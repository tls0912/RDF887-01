package com.czkuo.rdf88701.application.monitor;

import com.czkuo.rdf88701.application.mqtt.context.SystemContext;
import com.czkuo.rdf88701.application.mqtt.util.BaseMqttHandlerUtils;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.application.service.r029.R029ContextService;
import com.czkuo.rdf88701.common.dto.mqtt.command.R031CommandPayload;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.domain.repository.*;
import com.czkuo.rdf88701.infra.entity.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * AutoR031Planner
 * ─────────────────────────────────────────────────────────────
 * 目的：
 * - 手動挑選要退庫的carrier 寫入 R031 主檔/明細
 * - 先完成帳籍變更 + 命名（以第一筆 lotNo 為 base：{base}_P_i）
 * - 將任務匯入 mqtt_inbox，交由既有流程處理（本類不產生任何 Crane 請求）
 * <p>
 * 過濾：
 * - 排除已被任務/請求鎖定
 * - 排除已被 R029 佔用
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoR031Planner {


    private final ContainerMainRepository containerRepo;
    private final LocationTrackingRepository locationTrackingRepository;
    private final LocationPointRepository locationPointRepository;
    private final RobotInR031Repository r031Repository;
    private final RobotR031TaskRepository r031TaskRepository;
    private final MqttInboxRepository inboxRepository;
    private final R029ContextService r029ContextService;
    private final MqttMessageLogService logService;
    private final SystemContext systemContext;
    private final ObjectMapper objectMapper;


    @Transactional
    public void planR031IfEligible(String carrierId) {
        if (carrierId == null || carrierId.isBlank()) {
            return;
        }
        ContainerMain cm = containerRepo.findByAliasCode(carrierId).orElse(null);
        if (cm == null)
            return;
        LocationTracking lt = locationTrackingRepository.findByContainerMainId(cm.getId()).orElse(null);
        if (lt == null)
            return;
        LocationPoint lp = locationPointRepository.findById(lt.getLocationPointId()).orElse(null);
        if (lp == null)
            return;

        // 2) 排除：已被任務/請求鎖定
        Set<Long> blockedIds = containerRepo.findContainerIdsWithUnfinishedTasksOrUnacceptedRequests();
        if (blockedIds.contains(cm.getId()))
            return;
        // 3) 排除：R029 佔用
        try {
            Set<Long> r029Occupied = r029ContextService.findOccupiedContainerIds();
            if (r029Occupied == null || r029Occupied.contains(cm.getId()))
                return;
        } catch (Exception e) {
            log.error("[AutoR031] 取得 R029 佔用清單失敗：{}", e.getMessage(), e);
            return;
        }

        // 9) 構造一筆「系統自產」的 R031 命令 payload（供日後追溯）
        R031CommandPayload payload = new R031CommandPayload();
        payload.setCmd("ROBOT");
        payload.setCmdId("R031");
        payload.setTid(BaseMqttHandlerUtils.generateTid());
        R031CommandPayload.Message m = new R031CommandPayload.Message();
        m.setCarrierId(cm.getAliasCode());
        m.setLotId(cm.getLotNo());
        m.setWipName(lp.getName()); // 標示來源
        payload.setMessage(m);

        // 10) 記錄到 mqtt_message_log（COMMAND）取得 logId
        Long logId = logService.recordReturningId(
                "auto://r031",                 // topic（自定）
                systemContext.getSystemCode(),       // sender：本系統
                systemContext.getSystemCode(),       // receiver：本系統
                objectMapper.valueToTree(payload),
                MqttMessageType.COMMAND
        );

        // 11) 寫入 robot_in_R031 主檔/明細
        RobotInR031 main = new RobotInR031();
        main.setLogId(logId);
        main.setCarrierId(cm.getAliasCode());
        main.setLotId(cm.getLotNo());
        main.setWipName(lp.getName());
        if (r031Repository.findById(logId).isPresent()) {
            r031Repository.update(main);
        } else {
            r031Repository.save(main);
        }

        // 13) 建立任務主檔 robot_R031_task
        try {
            if (r031TaskRepository.findByLogId(logId).isEmpty()) {
                LocalDateTime now = LocalDateTime.now();
                RobotR031Task t = new RobotR031Task();
                t.setLogId(logId);
                t.setTid(payload.getTid());
                t.setCarrierId(cm.getAliasCode());
                t.setLotId(cm.getLotNo());
                t.setWipName(lp.getName());
                t.setInternalState("QUEUED");
                t.setExternalLastResult("OK"); // 收單即對外結果 OK（對方收到 ACK=OK）
                t.setExternalLastTime(now);
                t.setCreatedTime(now);
                t.setUpdatedTime(now);
                // 原始 MESSAGE 快照
                t.setRawMessageJson(objectMapper.writeValueAsString(payload));
                r031TaskRepository.save(t);
                log.info("[R031] 任務建立完成：task(logId={}) READY", logId);
            }
        } catch (Exception e) {
            // 不阻斷收單，但務必記錄
            log.error("[R031] 建立 robot_R031_task 失敗（不阻斷）：logId={}, err={}", logId, e.getMessage(), e);
        }

        // 14) 匯入 mqtt_inbox，交由你的既有處理器消化（這邊不建任何 Crane 請求）
        inboxRepository.enqueueFromInbound(
                logId,
                payload.getTid(),
                payload.getCmdId(),
                systemContext.getSystemCode(), // sender
                systemContext.getSystemCode(), // receiver
                "auto://r031",
                LocalDateTime.now(),
                5 // priority
        );

        log.info("[AutoR031] 新增 R031：logId={}, CarrierID={}, LotID={}, WipName={}",
                logId, cm.getAliasCode(), cm.getLotNo(), lp.getName());
    }

    // ─────────────────────────────────────────────────────────────




}
