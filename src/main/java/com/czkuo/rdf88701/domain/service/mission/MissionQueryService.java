package com.czkuo.rdf88701.domain.service.mission;

import com.czkuo.rdf88701.application.service.zip.ZipStockerCommandService;
import com.czkuo.rdf88701.common.dto.mqtt.ack.S074AckPayload;
import com.czkuo.rdf88701.common.enums.ZipTarget;
import com.czkuo.rdf88701.domain.dto.zip.StatusQuery.StatusQuerySecondaryBody;
import com.czkuo.rdf88701.domain.dto.zip.common.Root;
import com.czkuo.rdf88701.domain.repository.*;
import com.czkuo.rdf88701.infra.entity.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MissionQueryService {

    private final RobotInR007Repository robotInR007Repository;
    private final RobotR007TaskRepository robotR007TaskRepository;

    private final RobotInR008Repository robotInR008Repository;
    private final RobotR008TaskRepository robotR008TaskRepository;

    private final RobotInR029Repository robotInR029Repository;
    private final RobotInR029LotRepository robotInR029LotRepository;
    private final RobotR029TaskRepository robotR029TaskRepository;

    private final RobotInR031Repository robotInR031Repository;
    private final RobotR031TaskRepository robotR031TaskRepository;

    // 用來判斷容器是否仍在來源儲位
    private final ContainerMainRepository containerMainRepository;
    private final LocationTrackingRepository locationTrackingRepository;
    private final LocationPointRepository locationPointRepository;

    private final ZipStockerCommandService zipCommandService;

    @Value("${mqtt.s074.max-missions:500}")
    private int maxMissions;

    @Value("${mqtt.s074.r029.max-lots-per-log:100}")
    private int maxLotsPerR029;

    // ================== 單筆狀態查詢：改以 Task 查 ==================

    @Transactional(readOnly = true)
    public SingleMissionStatus querySingleMissionStatus(String cmdMaybeNull, String tidMaybeNull) {
        String cmd = upper(cmdMaybeNull);
        String qTid = nz(tidMaybeNull);
        if (cmd.isEmpty() || qTid.isEmpty()) return SingleMissionStatus.nullStatus();

        switch (cmd) {
            case "R007" -> {
                var tOpt = robotR007TaskRepository.findLatestByTid(qTid);
                if (tOpt.isEmpty()) return SingleMissionStatus.nullStatus();
                RobotR007Task t = tOpt.get();

                ZipSlotsCache     zipSlots = loadZipType3Slots(ZipTarget.ZIPA);
                ZipInventoryCache zipInv   = loadZipType6Inventory(ZipTarget.ZIPA);

                String carrierId = nz(t.getCarrierId());
                String slotName  = zipSlots.slotByCarrier(carrierId);

                if (!slotName.isBlank())
                    return new SingleMissionStatus(slotName, nz(t.getDestLoc()), nz(t.getEqpPort()));
                if (zipInv.contains(carrierId))
                    return new SingleMissionStatus("STK", nz(t.getDestLoc()), nz(t.getEqpPort()));
                return new SingleMissionStatus("AMR", nz(t.getDestLoc()), nz(t.getEqpPort()));
            }

            case "R008" -> {
                var tOpt = robotR008TaskRepository.findLatestByTid(qTid);
                if (tOpt.isEmpty()) return SingleMissionStatus.nullStatus();
                RobotR008Task t = tOpt.get();

                String mover = "AMR";
                return new SingleMissionStatus(mover, nz(t.getDestLoc()), nz(t.getEqpPort()));
            }

            case "R029" -> {
                var tOpt = robotR029TaskRepository.findLatestByTid(qTid);
                if (tOpt.isEmpty()) return SingleMissionStatus.nullStatus();
                // R029 沒來源儲位 → 統一 STK
                return new SingleMissionStatus("STK", "", "");
            }

            case "R031" -> {
                var tOpt = robotR031TaskRepository.findLatestByTid(qTid);
                if (tOpt.isEmpty()) return SingleMissionStatus.nullStatus();
                RobotR031Task t = tOpt.get();

                ZipSlotsCache     zipSlots = loadZipType3Slots(ZipTarget.ZIPA);
                ZipInventoryCache zipInv   = loadZipType6Inventory(ZipTarget.ZIPA);

                String carrierId = nz(t.getCarrierId());
                String slotName  = zipSlots.slotByCarrier(carrierId);

                if (!slotName.isBlank()) return new SingleMissionStatus(slotName, "", "");
                if (zipInv.contains(carrierId)) return new SingleMissionStatus("STK", "", "");
                return SingleMissionStatus.nullStatus();
            }

            default -> {
                return SingleMissionStatus.nullStatus();
            }
        }
    }

    // ================== 列表查詢：只用 findOpen() ==================

    @Transactional(readOnly = true)
    public List<S074AckPayload.MissionItem> queryPendingMissions() {
        // 載入 ZIPA 的 Type=3 與 Type=6（分開）
        ZipSlotsCache     zipSlots = loadZipType3Slots(ZipTarget.ZIPA);
        ZipInventoryCache zipInv   = loadZipType6Inventory(ZipTarget.ZIPA);

        List<S074AckPayload.MissionItem> out = new ArrayList<>();

        // R007
        for (RobotR007Task t : robotR007TaskRepository.findOpen()) {
            if (out.size() >= maxMissions) break;
            buildFromR007Task(t, zipSlots, zipInv).ifPresent(m -> addBounded(out, m));
        }

        // R008
        for (RobotR008Task t : robotR008TaskRepository.findOpen()) {
            if (out.size() >= maxMissions) break;
            buildFromR008Task(t).ifPresent(m -> addBounded(out, m));
        }

        // R029（可能多 lot）
        for (RobotR029Task t : robotR029TaskRepository.findOpen()) {
            if (out.size() >= maxMissions) break;
            List<S074AckPayload.MissionItem> items = buildFromR029Task(t);
            for (S074AckPayload.MissionItem m : items) {
                if (!addBounded(out, m)) break;
            }
        }

        // R031
        for (RobotR031Task t : robotR031TaskRepository.findOpen()) {
            if (out.size() >= maxMissions) break;
            buildFromR031Task(t, zipSlots, zipInv).ifPresent(m -> addBounded(out, m));
        }

        return out;
    }

    // ================== 各指令：Task -> MissionItem ==================

    private Optional<S074AckPayload.MissionItem> buildFromR007Task(
            RobotR007Task t, ZipSlotsCache zipSlots, ZipInventoryCache zipInv) {

        Long logId = t.getLogId();
        if (logId == null) return Optional.empty();

        String carrierId = nz(t.getCarrierId());
        String slotName  = zipSlots.slotByCarrier(carrierId);

        S074AckPayload.MissionItem m = new S074AckPayload.MissionItem();
        m.setCommandTid("R007_" + t.getTid());
        m.setLotId(carrierId);


        if (!slotName.isBlank())             m.setStatus(slotName);
        else if (zipInv.contains(carrierId)) m.setStatus("STK");
        else                                 m.setStatus("AMR");

        m.setEqpName(nz(t.getDestLoc()));
        m.setEqpPort(nz(t.getEqpPort()));
        return Optional.of(m);
    }

    private Optional<S074AckPayload.MissionItem> buildFromR008Task(RobotR008Task t) {
        Long logId = t.getLogId();
        if (logId == null) return Optional.empty();

        var opt = robotInR008Repository.findById(logId);
        if (opt.isEmpty()) return Optional.empty();
        RobotInR008 r = opt.get();

        S074AckPayload.MissionItem m = new S074AckPayload.MissionItem();
        m.setCommandTid("R008_" + t.getTid());
        m.setLotId(nz(r.getLotId()));

        String status = "AMR";
        m.setStatus(status);

        m.setEqpName(nz(r.getDestLoc()));
        m.setEqpPort(nz(r.getEqpPort()));
        return Optional.of(m);
    }

    private List<S074AckPayload.MissionItem> buildFromR029Task(RobotR029Task t) {
        List<S074AckPayload.MissionItem> list = new ArrayList<>();
        Long logId = t.getLogId();

        if (logId == null) {
            S074AckPayload.MissionItem m = new S074AckPayload.MissionItem();
            m.setCommandTid("R029_" + t.getTid());
            m.setLotId("");
            m.setStatus("STK");
            m.setEqpName("");
            m.setEqpPort("");
            list.add(m);
            return list;
        }

        List<String> carriers = robotInR029LotRepository.findCarrierIdsByLogId(logId);
        if (carriers.isEmpty()) {
            S074AckPayload.MissionItem m = new S074AckPayload.MissionItem();
            m.setCommandTid("R029_" + t.getTid());
            m.setLotId("");
            m.setStatus("STK");
            m.setEqpName("");
            m.setEqpPort("");
            list.add(m);
            return list;
        }

        int count = 0;
        for (String c : carriers) {
            if (count++ >= maxLotsPerR029) break;
            S074AckPayload.MissionItem m = new S074AckPayload.MissionItem();
            m.setCommandTid("R029_" + t.getTid());
            m.setLotId(nz(c));
            m.setStatus("STK");
            m.setEqpName("");
            m.setEqpPort("");
            list.add(m);
        }
        return list;
    }

    private Optional<S074AckPayload.MissionItem> buildFromR031Task(
            RobotR031Task t, ZipSlotsCache zipSlots, ZipInventoryCache zipInv) {

        Long logId = t.getLogId();
        if (logId == null) return Optional.empty();

        var opt = robotInR031Repository.findById(logId);
        if (opt.isEmpty()) return Optional.empty();
        RobotInR031 r = opt.get();

        S074AckPayload.MissionItem m = new S074AckPayload.MissionItem();
        m.setCommandTid("R031_" + t.getTid());
        m.setLotId(nz(r.getCarrierId()));

        String carrierId = nz(r.getCarrierId());
        String slotName  = zipSlots.slotByCarrier(carrierId);

        if (!slotName.isBlank())             m.setStatus(slotName);
        // else if (zipInv.contains(carrierId)) m.setStatus("STK");
        else                                 m.setStatus("STK");

        m.setEqpName("");
        m.setEqpPort("");
        return Optional.of(m);
    }

    // ================== 共同工具 ==================

    /** 只載入 ZIP 指定目標的 的 Type=3（所有 slot） */
    private ZipSlotsCache loadZipType3Slots(ZipTarget t) {
        ZipSlotsCache cache = new ZipSlotsCache();
        mergeZipType3Into(cache, t);
        return cache;
    }

    private void mergeZipType3Into(ZipSlotsCache cache, ZipTarget t) {
        try {
            Root<StatusQuerySecondaryBody> resp = zipCommandService.queryAllSlots(t);
            if (resp == null || resp.getBody() == null || resp.getBody().getStatusInfos() == null) return;

            for (StatusQuerySecondaryBody.StatusInfo s : resp.getBody().getStatusInfos()) {
                if (s == null || s.getName() == null) continue;
                if (s.getType() != 3) continue; // 只看儲位
                String slotName = s.getName().toString().trim();
                if (slotName.isEmpty()) continue;

                String carrier = null;
                List<?> msg = s.getMessage();
                if (msg != null && !msg.isEmpty() && msg.get(0) != null) {
                    String m0 = msg.get(0).toString().trim();
                    carrier = m0.isEmpty() ? null : m0;
                }
                cache.put(slotName, carrier);
            }
        } catch (Exception e) {
            log.warn("[S074] 查 ZIP {} Type=3 失敗：{}", t, e.getMessage());
        }
    }

    /** 只載入 ZIP 指定目標的 Type=6（庫存清單）：建立存在於 Type=6 的 carrier 索引（msg[1]=carrierId） */
    private ZipInventoryCache loadZipType6Inventory(ZipTarget t) {
        ZipInventoryCache idx = new ZipInventoryCache();
        try {
            Root<StatusQuerySecondaryBody> resp = zipCommandService.queryInventory(t); // Type=6
            if (resp == null || resp.getBody() == null || resp.getBody().getStatusInfos() == null) return idx;

            for (StatusQuerySecondaryBody.StatusInfo s : resp.getBody().getStatusInfos()) {
                if (s == null) continue;
                if (s.getType() != 6) continue;

                List<?> msg = s.getMessage();
                if (msg == null || msg.isEmpty()) continue;

                String carrierId = null; // msg[0]=barcode, msg[1]=carrierId
                if (msg.size() >= 2 && msg.get(1) != null) {
                    carrierId = msg.get(1).toString().trim();
                }
                if (carrierId == null || carrierId.isEmpty()) continue;

                idx.add(carrierId);
            }
        } catch (Exception e) {
            log.warn("[S074] 查 ZIP {} Type=6 失敗：{}", t, e.getMessage());
        }
        return idx;
    }

    /** slot->carrier 快取（新增 slotByCarrier 方便反查） */
    private static final class ZipSlotsCache {
        private final Map<String,String> map = new HashMap<>();
        void put(String slot, String carrier) { map.put(slot, carrier); }
        String carrierAt(String slot) { return map.get(slot); } // 可能為 null
        String slotByCarrier(String carrierId) {
            if (carrierId == null || carrierId.isBlank()) return "";
            for (var e : map.entrySet()) {
                if (carrierId.equalsIgnoreCase(nz(e.getValue()))) return e.getKey();
            }
            return "";
        }
    }

    /** Type=6 索引（與 Type=3 分開管理，不做 merge） */
    private static final class ZipInventoryCache {
        private final Set<String> carriers = new HashSet<>();
        void add(String carrier) { if (carrier != null && !carrier.isBlank()) carriers.add(carrier); }
        boolean contains(String carrier) { return carrier != null && !carrier.isBlank() && carriers.contains(carrier); }
    }

    public record SingleMissionStatus(String status, String eqpName, String eqpPort) {
        public static SingleMissionStatus nullStatus() { return new SingleMissionStatus("NULL", "", ""); }
    }

    // helpers...
    private boolean addBounded(List<S074AckPayload.MissionItem> list, S074AckPayload.MissionItem item) {
        if (list.size() >= maxMissions) return false;
        list.add(item);
        return true;
    }
    private static boolean looksLikeAmr(String mappedTaskType) {
        return mappedTaskType != null && mappedTaskType.toUpperCase(Locale.ROOT).contains("AMR");
    }
    private static String nz(String s) { return s == null ? "" : s; }
    private static String upper(String s) { return s == null ? "" : s.toUpperCase(Locale.ROOT); }
}

