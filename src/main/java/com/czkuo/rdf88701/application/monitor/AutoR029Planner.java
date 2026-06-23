package com.czkuo.rdf88701.application.monitor;

import com.czkuo.rdf88701.application.mqtt.context.SystemContext;
import com.czkuo.rdf88701.application.mqtt.util.BaseMqttHandlerUtils;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.application.service.r029.R029ContextService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.command.R029CommandPayload;
import com.czkuo.rdf88701.domain.repository.*;
import com.czkuo.rdf88701.infra.dto.ContainerWithLocation;
import com.czkuo.rdf88701.infra.entity.ContainerMain;
import com.czkuo.rdf88701.infra.entity.RobotInR029;
import com.czkuo.rdf88701.infra.entity.RobotInR029Lot;
import com.czkuo.rdf88701.infra.entity.RobotR029Task;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * AutoR029Planner
 * ─────────────────────────────────────────────────────────────
 * 目的：
 * - 自動挑「NORMAL_WITH_COVER」貨，數量滿足時（≥2, ≤3）→ 寫入 R029 主檔/明細
 * - 先完成帳籍變更 + 命名（以第一筆 lotNo 為 base：{base}_P_i）
 * - 將任務匯入 mqtt_inbox，交由既有流程處理（本類不產生任何 Crane 請求）
 * <p>
 * 過濾：
 * - 排除已被任務/請求鎖定
 * - 排除已被 R029 佔用
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoR029Planner {

    private static final String CONTENT_KIND_NWC = "NORMAL_WITH_COVER";
    private static final int MIN_BATCH = 2;
    private static final int MAX_BATCH = 3;

    private final ContainerMainRepository containerRepo;
    private final RobotInR029Repository r029Repo;
    private final RobotInR029LotRepository r029LotRepo;
    private final RobotR029TaskRepository taskRepo;
    private final MqttInboxRepository inboxRepository;
    private final R029ContextService r029ContextService;
    private final MqttMessageLogService logService;
    private final SystemContext systemContext;
    private final ObjectMapper objectMapper;
    public static final boolean TestMode = true;

    /**
     * 每 30 秒檢查一次是否可產生一批 R029（2~3 顆）
     */
    @Scheduled(fixedDelay = 10_000)
    public void planR029IfEligible() {
        if (!TestMode)
            return;
        // 1) 取得候選：倉儲儲位內 + 最新 container_data.content_kind = NORMAL_WITH_COVER
        List<ContainerWithLocation> candidates =
                containerRepo.findAllInWarehouseWithLocationByContentKind(CONTENT_KIND_NWC);

        if (candidates.isEmpty()) {
            //log.debug("[AutoR029] 無 NORMAL_WITH_COVER 候選");
            return;
        }

        // 2) 排除：已被任務/請求鎖定
        Set<Long> blockedIds = containerRepo.findContainerIdsWithUnfinishedTasksOrUnacceptedRequests();
        candidates.removeIf(c -> blockedIds.contains(c.getId()));

        // 3) 排除：R029 佔用
        Set<Long> r029Occupied = safeOccupiedIds();
        candidates.removeIf(c -> r029Occupied.contains(c.getId()));

        if (candidates.size() < MIN_BATCH) {
            //log.debug("[AutoR029] 可用 NORMAL_WITH_COVER < {}，不產生 R029", MIN_BATCH);
            return;
        }

        // 4) 取本批 2~3 顆
        int batchSize = Math.min(Math.max(candidates.size(), MIN_BATCH), MAX_BATCH);
        batchSize = Math.min(batchSize, MAX_BATCH); // 最多 3
        List<ContainerWithLocation> pick = candidates.subList(0, batchSize);

        // 5) 取得對應 ContainerMain（要 lotNo / aliasCode）
        Map<Long, ContainerMain> cmMap = pick.stream()
                .map(c -> containerRepo.findById(c.getId()).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(ContainerMain::getId, x -> x));

        // 6) 產生新命名：以第一筆的 lotNo 為 base；若 lotNo 為空則退回 aliasCode
        String base = firstNonBlank(
                cmMap.get(pick.get(0).getId()).getLotNo(),
                cmMap.get(pick.get(0).getId()).getAliasCode()
        );
        if (base == null) {
            log.warn("[AutoR029] 第一筆缺 lotNo/aliasCode，放棄本輪");
            return;
        }
        List<String> newNames = new ArrayList<>();
        for (int i = 1; i <= pick.size(); i++) {
            newNames.add(base + "_P_" + i);
        }

        // 7) 寫入前：帳籍變更 + 命名（最小實作：updateName；你若有更多欄位，請集中到服務層一次完成）
        for (int i = 0; i < pick.size(); i++) {
            Long cid = pick.get(i).getId();
            String newName = newNames.get(i);
            boolean ok = containerRepo.updateAliasCode(cid, newName);
            if (!ok) {
                log.warn("[AutoR029] 改名失敗：container#{}, newName={}", cid, newName);
                return; // 保守作法：整批放棄，避免部分成功
            }
        }

        // 8) 隨機 count（6 or 10）
        int count = ThreadLocalRandom.current().nextBoolean() ? 7 : 10;
        count = 5;
        // 9) 構造一筆「系統自產」的 R029 命令 payload（供日後追溯）
        R029CommandPayload payload = buildLocalR029Payload(pick, cmMap, count, base);

        // 10) 記錄到 mqtt_message_log（COMMAND）取得 logId
        Long logId = logService.recordReturningId(
                "auto://r029",                 // topic（自定）
                systemContext.getSystemCode(),       // sender：本系統
                systemContext.getSystemCode(),       // receiver：本系統
                objectMapper.valueToTree(payload),
                MqttMessageType.COMMAND
        );

        // 11) 寫入 robot_in_r029 主檔/明細
        RobotInR029 main = new RobotInR029();
        main.setLogId(logId);
        main.setCount(count);
        main.setTrayType(payload.getMessage() != null ? payload.getMessage().getTrayType() : null);
        main.setTrayDesc(payload.getMessage() != null ? payload.getMessage().getTrayDesc() : "AUTO");
        if (r029Repo.findById(logId).isPresent()) {
            r029Repo.update(main);
        } else {
            r029Repo.save(main);
        }

        // 明細用「新命名」寫入
        try {
            r029LotRepo.batchUpsert(logId, newNames);
        } catch (Throwable t) {
            for (String name : newNames) {
                var lot = new RobotInR029Lot();
                lot.setLogId(logId);
                lot.setCarrierId(name);
                r029LotRepo.save(lot);
            }
        }

        // 12) 掛回 R029 上下文（讓之後佔用檢查可以過濾掉）
        try {
            r029ContextService.attachContextToSourceContainers(logId, payload.getTid());
        } catch (Exception e) {
            log.error("[AutoR029] attachContext 失敗（不阻斷）：logId={}, err={}", logId, e.getMessage(), e);
        }

        // 13) 建立任務主檔 robot_r029_task（READY；lane 交由 Walker 決策）
        try {
            if (taskRepo.findByLogId(logId).isEmpty()) {
                RobotR029Task t = new RobotR029Task();
                t.setLogId(logId);
                t.setTid(payload.getTid());
                t.setPiecePerLot(count);
                t.setTrayType(main.getTrayType());
                t.setTrayDesc(main.getTrayDesc());
                // Walker 之後決策 lane（MAIN/SUB），這裡先維持 null
                t.setInternalState("QUEUED");
                t.setExternalLastResult("OK"); // 收單即對外結果 OK（對方收到 ACK=OK）
                t.setExternalLastTime(LocalDateTime.now());
                t.setCreatedTime(LocalDateTime.now());
                t.setUpdatedTime(LocalDateTime.now());
                // 原始 MESSAGE 快照
                t.setRawMessageJson(objectMapper.writeValueAsString(payload));
                taskRepo.save(t);
                log.info("[R029] 任務建立完成：task(logId={}) READY", logId);
            }
        } catch (Exception e) {
            // 不阻斷收單，但務必記錄
            log.error("[R029] 建立 robot_r029_task 失敗（不阻斷）：logId={}, err={}", logId, e.getMessage(), e);
        }

        // 14) 匯入 mqtt_inbox，交由你的既有處理器消化（這邊不建任何 Crane 請求）
        inboxRepository.enqueueFromInbound(
                logId,
                payload.getTid(),
                payload.getCmdId(),
                systemContext.getSystemCode(), // sender
                systemContext.getSystemCode(), // receiver
                "auto://r029",
                LocalDateTime.now(),
                5 // priority
        );

        log.info("[AutoR029] 新增 R029：logId={}, size={}, count={}, base={}, lots={}",
                logId, pick.size(), count, base, newNames);
    }

    // ─────────────────────────────────────────────────────────────

    private Set<Long> safeOccupiedIds() {
        try {
            Set<Long> s = r029ContextService.findOccupiedContainerIds();
            return (s != null) ? s : Collections.emptySet();
        } catch (Exception e) {
            log.error("[AutoR029] 取得 R029 佔用清單失敗：{}", e.getMessage(), e);
            return Collections.emptySet();
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a.trim();
        if (b != null && !b.isBlank()) return b.trim();
        return null;
    }

    private R029CommandPayload buildLocalR029Payload(List<ContainerWithLocation> pick,
                                                     Map<Long, ContainerMain> cmMap,
                                                     int count,
                                                     String base) {
        R029CommandPayload p = new R029CommandPayload();
        p.setCmd("ROBOT");
        p.setCmdId("R029");
        p.setTid(BaseMqttHandlerUtils.generateTid());

        R029CommandPayload.Message m = new R029CommandPayload.Message();
        m.setCount(String.valueOf(count));
        m.setTrayType("4607996101");
        m.setTrayDesc("AUTO"); // 標示來源

        // CARRIER_LIST：用「改名前」的標識（lotNo/aliasCode 之一）
        List<R029CommandPayload.CarrierInfo> list = new ArrayList<>();
        for (ContainerWithLocation c : pick) {
            ContainerMain cm = cmMap.get(c.getId());
            String id = firstNonBlank(cm.getLotNo(), cm.getAliasCode());
            R029CommandPayload.CarrierInfo li = new R029CommandPayload.CarrierInfo();
            li.setCarrierId(id);
            list.add(li);
        }
        m.setCarrierList(list);
        m.setTrayDesc(base + " (AUTO)");
        p.setMessage(m);
        return p;
    }
}
