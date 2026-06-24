package com.czkuo.rdf88701.application.monitor;

import com.czkuo.rdf88701.application.mqtt.context.SystemContext;
import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.mqtt.util.BaseMqttHandlerUtils;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.dto.mqtt.ack.S045AckPayload;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.config.plc.PlcSafetyRegistry;
import com.czkuo.rdf88701.domain.plc.state.safety.SafetyDeviceStatus;
import com.czkuo.rdf88701.domain.repository.SafetyEventLogRepository;
import com.czkuo.rdf88701.domain.repository.SafetyPointRepository;
import com.czkuo.rdf88701.domain.repository.SafetyStatusSnapshotRepository;
import com.czkuo.rdf88701.infra.cache.SafetyStatusCache;
import com.czkuo.rdf88701.infra.entity.SafetyEventLog;
import com.czkuo.rdf88701.infra.entity.SafetyPoint;
import com.czkuo.rdf88701.infra.entity.SafetyStatusSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SafetyStatusMonitor
 *
 * 功能：
 * 1) 每秒從 SafetyStatusCache 取出各安全設備最新快照
 * 2) 逐一比對各點位狀態，異動時：
 *    - upsert safety_status_snapshot (is_triggered / last_change_time / last_poll_time)
 *    - insert safety_event_log（記錄 from/to 與快照摘要）
 * 3) 無異動時只更新 last_poll_time
 *
 * 依賴：
 * - PlcSafetyRegistry：拿到所有安全設備名稱（對應 cache key）
 * - SafetyStatusCache：runtime 快取（deviceName -> SafetyDeviceStatus）
 * - SafetyPointRepository：查詢/快取 addr_expr -> pointId
 * - SafetyStatusSnapshotRepository：更新快照
 * - SafetyEventLogRepository：寫入事件
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已修改，// 註解已依現有實作校正。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SafetyStatusMonitor {

    private final PlcSafetyRegistry safetyRegistry;
    private final SafetyStatusCache statusCache;

    private final SafetyPointRepository pointRepo;
    private final SafetyStatusSnapshotRepository snapshotRepo;
    private final SafetyEventLogRepository eventLogRepo;

    // === 直接在 Monitor 內發 S045 ACK 所需相依 ===
    private final ObjectMapper objectMapper;
    private final MqttMessageEventPublisher responseEventPublisher;
    private final MqttMessageLogService logService;
    private final SystemContext systemContext;

    /** addr_expr -> pointId（僅緩存 enabled=Y 的點位） */
    private final Map<String, Long> addrToPointId = new ConcurrentHashMap<>();
    /** pointId -> SafetyPoint（避免熱路徑 DB 查詢） */
    private final Map<Long, SafetyPoint> pointCache = new ConcurrentHashMap<>();

    /** 啟動時預熱 addr 快取 */
    @PostConstruct
    public void warmUpAddressCache() {
        try {
            List<SafetyPoint> all = pointRepo.findAll();
            int loaded = 0, enabledCount = 0;
            for (SafetyPoint p : all) {
                if (isEnabled(p)) {
                    enabledCount++;
                    String addr = safeUpper(p.getAddrExpr());
                    if (addr != null) {
                        addrToPointId.put(addr, p.getId());
                        pointCache.put(p.getId(), p);
                        loaded++;
                    }
                }
            }
            log.info("[SafetyStatusMonitor] 預載入點位映射完成：loaded={} / enabled={} / total={}",
                    loaded, enabledCount, all.size());
        } catch (Exception e) {
            log.error("[SafetyStatusMonitor] 預載入點位映射失敗：{}", e.getMessage(), e);
        }
    }

    /**
     * 每 1 秒掃描一次：
     * - 同步快照/事件
     * - 若該設備有任一點位狀態變更，批次主動上拋一筆 S045 ACK（只帶變更點位；要全量可把 onlyPointIds 改為 null）
     */
    @Scheduled(fixedDelay = 1000)
    public void tick() {
        List<String> deviceNames = safetyRegistry.getAllDeviceNames();
        if (deviceNames == null || deviceNames.isEmpty()) return;

        LocalDateTime now = LocalDateTime.now();

        for (String deviceName : deviceNames) {
            SafetyDeviceStatus status = statusCache.getLatest(deviceName);
            if (status == null || status.getStates() == null || status.getStates().isEmpty()) {
                continue;
            }

            Map<String, Boolean> states = status.getStates(); // addr_expr -> bool
            // 用 Map 記住「變更點位」以及「最新狀態」；後面直接用它組 S045，不再回 DB
            Map<Long, Boolean> changedStates = new HashMap<>();

            for (Map.Entry<String, Boolean> e : states.entrySet()) {
                String addr = safeUpper(e.getKey());
                Boolean triggered = e.getValue();
                if (addr == null || triggered == null) continue;

                Long pointId = resolvePointId(addr);
                if (pointId == null) {
                    continue; // 未建/未啟用
                }

                try {
                    Optional<SafetyStatusSnapshot> opt = snapshotRepo.findByPointId(pointId);
                    if (opt.isEmpty()) {
                        SafetyStatusSnapshot snap = new SafetyStatusSnapshot();
                        snap.setPointId(pointId);
                        snap.setIsTriggered(toYN(triggered));
                        snap.setLastChangeTime(now);
                        snap.setLastPollTime(now);
                        snapshotRepo.save(snap);

                        writeEvent(pointId, triggered, triggered, now, addr, deviceName);
                        changedStates.put(pointId, triggered);   // 記住最新狀態
                        continue;
                    }

                    SafetyStatusSnapshot snap = opt.get();
                    boolean prev = fromYN(snap.getIsTriggered());
                    boolean cur  = triggered;

                    if (prev != cur) {
                        snap.setIsTriggered(toYN(cur));
                        snap.setLastChangeTime(now);
                        snap.setLastPollTime(now);
                        snapshotRepo.update(snap);

                        writeEvent(pointId, prev, cur, now, addr, deviceName);
                        changedStates.put(pointId, cur);         // 記住最新狀態
                    } else {
                        snap.setLastPollTime(now);
                        snapshotRepo.update(snap);
                    }
                } catch (Exception ex) {
                    log.error("[SafetyStatusMonitor] 同步點位 {} 失敗：{}", addr, ex.getMessage(), ex);
                }
            }

            // 只有有變更才上拋，且直接用 memory 中的 changedStates
            if (!changedStates.isEmpty()) {
                sendS045Delta(changedStates);
            }
        }
    }

    private void sendS045Delta(Map<Long, Boolean> changedStates) {
        try {
            if (changedStates == null || changedStates.isEmpty()) return;

            // 只用 pointCache；沒有就跳過，完全不回 DB
            List<S045AckPayload.SafetyDeviceStatus> deviceList = new ArrayList<>(changedStates.size());
            for (Map.Entry<Long, Boolean> entry : changedStates.entrySet()) {
                Long pointId = entry.getKey();
                Boolean triggered = entry.getValue();

                SafetyPoint p = pointCache.get(pointId);
                if (p == null || !isEnabled(p)) {
                    // 目前 cache 為 null 時直接略過，避免額外 DB IO。
                    //log.debug("[SafetyStatusMonitor] pointId={} 缺少快取，已略過主動上拋", pointId);
                    continue;
                }

                S045AckPayload.SafetyDeviceStatus d = new S045AckPayload.SafetyDeviceStatus();
                d.setDeviceName(nvl(p.getPointName()));
                d.setDeviceStatus(Boolean.TRUE.equals(triggered) ? "NG" : "OK");

                String base = Optional.ofNullable(p.getRemark()).map(String::trim).orElse("");
                if (base.isEmpty()) {
                    String type = nvl(p.getTypeCode());
                    String addr = nvl(p.getAddrExpr());
                    base = (type.isEmpty() && addr.isEmpty()) ? "" :
                            (type + (type.isEmpty() || addr.isEmpty() ? "" : " ") + addr);
                }
                d.setStatusDescription(base + (Boolean.TRUE.equals(triggered) ? "（被觸發）" : "（正常）"));

                deviceList.add(d);
            }

            if (deviceList.isEmpty()) return;

            String tid = BaseMqttHandlerUtils.generateTid();
            var msg = new S045AckPayload.Message();
            msg.setSafetyDeviceList(deviceList);

            var ack = new S045AckPayload();
            ack.setCmd("SYSTEM");
            ack.setCmdId("S045");
            ack.setIdDesc("SAFETY_DEVICE_STATUS_AUTO_PUSH");
            ack.setTid(tid);
            ack.setResult("OK");
            ack.setResultMessage("");
            ack.setMessage(msg);

            String ackJson = objectMapper.writeValueAsString(ack);
            String receiver = "ase"; // 可換成設定

            logService.record("AUTO_PUSH/S045", systemContext.getSystemCode(), receiver,
                    objectMapper.readTree(ackJson), MqttMessageType.ACK);
            responseEventPublisher.publish(receiver, ackJson, MqttMessageType.ACK, tid, "S045");
            log.info("[SafetyStatusMonitor] 已主動上拋 S045 ACK（delta, no-db）：target={}, changed={}, tid={}",
                    receiver, deviceList.size(), tid);

        } catch (Exception e) {
            log.error("[SafetyStatusMonitor] 主動上拋 S045(delta) 失敗：{}", e.getMessage(), e);
        }
    }

    // =================== 私有工具 ===================

    private void writeEvent(Long pointId, boolean from, boolean to,
                            LocalDateTime time, String addr, String deviceName) {
        SafetyEventLog logRow = new SafetyEventLog();
        logRow.setPointId(pointId);
        logRow.setFromTriggered(toYN(from));
        logRow.setToTriggered(toYN(to));
        logRow.setChangeTime(time);
        logRow.setSnapshotAfter(buildSnapshotJson(addr, to, time, deviceName));
        eventLogRepo.save(logRow);
    }

    /** 解析 addr 對應的 pointId（快取優先，miss 時從 DB 補快取） */
    private Long resolvePointId(String upperAddr) {
        Long cached = addrToPointId.get(upperAddr);
        if (cached != null) return cached;

        // 動態補快取：只收 enabled=Y 的
        Optional<SafetyPoint> opt = pointRepo.findByAddrExpr(upperAddr);
        if (opt.isPresent() && isEnabled(opt.get())) {
            Long id = opt.get().getId();
            addrToPointId.put(upperAddr, id);
            return id;
        }
        return null;
    }

    private static String buildSnapshotJson(String addr, boolean triggered, LocalDateTime time, String deviceName) {
        // 簡單手刻 JSON（避免額外相依）；若專案已有 Jackson 可改用 ObjectMapper
        return new StringBuilder(128)
                .append("{\"addr\":\"").append(escape(addr)).append("\",")
                .append("\"triggered\":").append(triggered).append(',')
                .append("\"time\":\"").append(time).append("\",")
                .append("\"device\":\"").append(escape(deviceName)).append("\"}")
                .toString();
    }

    private static String nvl(String s) { return s == null ? "" : s; }

    private static String toYN(boolean b) { return b ? "Y" : "N"; }

    private static boolean fromYN(String yn) { return "Y".equalsIgnoreCase(yn); }

    private static boolean isEnabled(SafetyPoint p) {
        String yn = p.getEnabled();
        return yn != null && yn.equalsIgnoreCase("Y");
    }

    private static String safeUpper(String s) {
        return (s == null) ? null : s.trim().toUpperCase(Locale.ROOT);
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
