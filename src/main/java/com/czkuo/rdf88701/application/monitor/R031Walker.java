package com.czkuo.rdf88701.application.monitor;

import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.command.CraneRequestCommandService;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.application.service.reservation.ReservationOrchestrator;
import com.czkuo.rdf88701.application.service.zip.ZipStockerCommandService;
import com.czkuo.rdf88701.common.dto.mqtt.ack.R031AckPayload;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.enums.ZipTarget;
import com.czkuo.rdf88701.domain.dto.zip.DispatchOrder.DispatchOrderSecondaryBody;
import com.czkuo.rdf88701.domain.dto.zip.StatusQuery.StatusQuerySecondaryBody;
import com.czkuo.rdf88701.domain.dto.zip.common.Root;
import com.czkuo.rdf88701.domain.repository.*;
import com.czkuo.rdf88701.infra.entity.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * R031Walker
 * - 先看 ZIPA：StatusQuery Type=3, Name="*" 全抓 → 找到 Name==wipName 的儲位，確認 message[0]==carrierId
 * - 若 ZIPA 檢核成功：直接送 ZIPA DispatchOrder(magazine=carrierId, stkPort="REJECT")，回 ACK=START 並 DONE（不走起重機）
 * - 若 ZIPA 不成立：才回到本倉 WIP 流程（站點/雙向/起重機檢查 → createOutboundRequest → ACK=START → DONE）
 * - 從 mqtt_inbox 撿 R031（IN_PROGRESS 上鎖）
 * - R031 單筆：LOT 來自 robot_in_r031（logId= mqtt_message_log.id）
 * - LOT 對容器：lotNo 匹配；找不到再用 carrierId 對 aliasCode
 * - 出到 Site#15；但需通過雙向設定：site_bidir_route.active_target 必須等於 expectedTarget(預設 Site#16)
 * - 出單前檢查：Site#15 / Site#16 / Transfer#6 不可被佔用；起重機不可忙碌
 * - 成功建立出庫 → 回 ACK=START 並 DONE；否則 requeue
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class R031Walker {

    private final MqttInboxRepository inboxRepo;
    private final RobotInR031Repository r031Repo;
    private final ContainerMainRepository containerRepo;
    private final LocationPointRepository locationPointRepo;
    private final LocationTrackingRepository locationTrackingRepo;
    private final CraneRequestRepository craneRequestRepository;
    private final CraneTaskRepository craneTaskRepository;
    private final CraneRequestCommandService craneRequestCommandService;
    private final SiteBidirRouteRepository siteBidirRouteRepository;
    private final ZipStockerCommandService zipCommandService;

    private final MqttMessageEventPublisher publisher;
    private final MqttMessageLogService logService;
    private final ObjectMapper objectMapper;
    private final ReservationOrchestrator reservationOrchestrator;

    // 控制
    @Value("${app.worker.r031.enabled:true}")
    private boolean enabled;

    @Value("${app.worker.r031.lock-ttl-seconds:120}")
    private int lockTtlSeconds;

    @Value("${app.worker.r031.backoff-seconds:15}")
    private int backoffSeconds;

    @Value("${spring.application.name:r031-walker}")
    private String workerId;

    // 雙向設定（WIP 流程才會用到）
    @Value("${app.worker.site15.pair-code:SITE15_16}")
    private String pairCode;

    /**
     * 只有當 activeTarget == expectedTarget 才派（需求指定 Site#16 才能派）
     */
    @Value("${app.worker.site15.expected-target:Site#16}")
    private String expectedTarget;

    @Value("${app.worker.site15.device-id:1}")
    private Long craneDeviceId;

    @Value("${app.worker.site15.crane-id:1}")
    private String craneId;

    @Value("${app.worker.r031.reason:R031_WALKER}")
    private String reason;

    @Value("${app.worker.r031.origin-ttl-seconds:600}")
    private long originTtlSeconds;

    // 站點常數（WIP 流程）
    private static final String SITE_15 = "Site#15";
    private static final String SITE_16 = "Site#16";
    private static final String TRANSFER_6 = "Transfer#6";
    private static final String TARGET_SITE = SITE_15;

    @Scheduled(fixedDelayString = "${app.worker.r031.interval-ms:1000}")
    public void tick() {
        if (!enabled) return;
        processOnce();
    }

    /**
     * 單次只處理 1 筆 R031 inbox
     */
    public void processOnce() {
        // 3.2 站點佔用檢查
        if (locationTrackingRepo.hasContainerAtLocationName(SITE_15)
                || locationTrackingRepo.hasContainerAtLocationName(SITE_16)
                || locationTrackingRepo.hasContainerAtLocationName(TRANSFER_6)) {
            return;
        }
        // 3.3 起重機忙碌
        boolean craneBusy = craneRequestRepository.existsUnfinishedRequestForDevice(craneDeviceId)
                || craneTaskRepository.existsUnfinishedTaskForCrane(craneId);
        if (craneBusy) {
            return;
        }
        Optional<SiteBidirRoute> routeOpt = siteBidirRouteRepository.findAll().stream()
                .filter(r -> pairCode.equalsIgnoreCase(r.getPairCode()))
                .findFirst();
        if (routeOpt.isEmpty())
            return;
        SiteBidirRoute route = routeOpt.get();
        String activeTarget = route.getActiveTarget();

        // 僅挑 R031
        Optional<MqttInbox> opt = inboxRepo.pickOneForProcessingByCmdNoNextAttemptTime("R031", workerId, Duration.ofSeconds(lockTtlSeconds));
        if (opt.isEmpty()) {
            resetActiveTarget(route, expectedTarget, SITE_15);
            return;
        }
        MqttInbox inbox = opt.get();
        Long inboxId = inbox.getId();
        Long logId = inbox.getLogId();

        try {
            // 限定 R031
            if (!"R031".equalsIgnoreCase(inbox.getCmdId())) {
                resetActiveTarget(route, expectedTarget, SITE_15);
                inboxRepo.requeue(inboxId, Duration.ofSeconds(1));
                return;
            }

            // 1) 讀 R031 主檔
            RobotInR031 main = r031Repo.findById(logId).orElse(null);
            if (main == null) {
                resetActiveTarget(route, expectedTarget, SITE_15);
                log.warn("[R031Walker] robot_in_r031 缺資料，requeue inboxId={}, logId={}", inboxId, logId);
                inboxRepo.requeue(inboxId, Duration.ofSeconds(backoffSeconds));
                return;
            }

            String carrierId = trimOrNull(main.getCarrierId());
            String wipName = trimOrNull(main.getWipName()); // 當作 ZIP slot 名稱
            String lotId = trimOrNull(main.getLotId());   // 只用於 ACK echo，不作為比對依據

            if (carrierId == null || wipName == null) {
                resetActiveTarget(route, expectedTarget, SITE_15);
                log.warn("[R031Walker] 基本欄位不足 carrierId/wipName，requeue inboxId={}, logId={}", inboxId, logId);
                inboxRepo.requeue(inboxId, Duration.ofSeconds(backoffSeconds));
                return;
            }

            // 2) 先看 ZIPA：Type=3, Name="*" 全取；找 slot(Name==wipName) 的 message[0] == carrierId
            Optional<String> zipErr = verifyZipaSlotHasCarrier(wipName, carrierId);

            if (zipErr.isEmpty()) {
                // ZIPA 成功：直接下 DispatchOrder(magazine=carrierId, stkPort="REJECT")
                try {
                    Root<DispatchOrderSecondaryBody> resp =
                            zipCommandService.sendDispatchOrderSingle(ZipTarget.ZIPA, carrierId, "REJECT");
                    String resultMsg = getResultMsg(resp);
                    log.info("[R031Walker] ZIPA DispatchOrder REJECT 已送出，carrierId={}, slot={}, msg={}",
                            carrierId, wipName, resultMsg);
                    sendAckStart(inbox.getSender(), inbox.getTid(), lotId, carrierId, wipName);
                    inboxRepo.markDone(inboxId, "R031", null);
                    return;
                } catch (Exception e) {
                    log.warn("[R031Walker] ZIPA DispatchOrder 失敗，requeue inboxId={}, err={}", inboxId, e.getMessage(), e);
                    inboxRepo.requeue(inboxId, Duration.ofSeconds(backoffSeconds));
                    return;
                }
            } else {
                // 需要的話可以把原因打在 debug
                //log.debug("[R031Walker] ZIPA 檢核未通過：{}", zipErr.get());
            }


            // 3.4 目標站點 ID（Site#15）
            Long toId = locationPointRepo.findByName(TARGET_SITE)
                    .map(LocationPoint::getId)
                    .orElse(null);
            if (toId == null) {
                resetActiveTarget(route, expectedTarget, SITE_15);
                inboxRepo.requeue(inboxId, Duration.ofSeconds(backoffSeconds));
                return;
            }

            // 3.5 找容器：優先用 carrierId 對 aliasCode；找不到再用 lotId 對 lotNo（僅為相容）
            Optional<ContainerMain> cOpt = containerRepo.findByAliasCode(carrierId);
            if (cOpt.isEmpty() && lotId != null) {
                cOpt = containerRepo.findByLotNo(lotId);
            }
            if (cOpt.isEmpty()) {
                resetActiveTarget(route, expectedTarget, SITE_15);
                log.warn("[R031Walker] 找不到容器：carrierId={}, lotId={}，requeue inboxId={}", carrierId, lotId, inboxId);
                inboxRepo.markCancelled(inboxId, "[R031Walker] 找不到容器：carrierId=" + carrierId);
                return;
            }
            ContainerMain c = cOpt.get();

            // 3.6 容器當前位置
            Long fromLocationId = locationTrackingRepo.findByContainerMainId(c.getId())
                    .map(LocationTracking::getLocationPointId)
                    .orElse(null);
            if (fromLocationId == null) {
                resetActiveTarget(route, expectedTarget, SITE_15);
                log.warn("[R031Walker] 容器無位置追蹤：containerId={}，requeue inboxId={}", c.getId(), inboxId);
                inboxRepo.requeue(inboxId, Duration.ofSeconds(backoffSeconds));
                return;
            }

            // 3.7 預約來源位
            var rsvOpt = reservationOrchestrator.reserveOriginForOutbound(
                    c.getId(),
                    fromLocationId,
                    0,
                    "R031_WALKER",
                    "HOLD_ORIGIN_BEFORE_OUTBOUND"
            );
            if (rsvOpt.isEmpty()) {
                resetActiveTarget(route, expectedTarget, SITE_15);
                inboxRepo.requeue(inboxId, Duration.ofSeconds(backoffSeconds));
                return;
            }
            LocationReservationRecord rsv = rsvOpt.get();
            if (rsv.getContainerMainId() != null && !rsv.getContainerMainId().equals(c.getId())) {
                resetActiveTarget(route, expectedTarget, SITE_15);
                inboxRepo.requeue(inboxId, Duration.ofSeconds(backoffSeconds));
                return;
            }

            // 3) ZIPA 不成立 → 走本倉 WIP 流程（原邏輯）
            // 3.1 雙向表檢查
            routeOpt = siteBidirRouteRepository.findAll().stream()
                    .filter(r -> pairCode.equalsIgnoreCase(r.getPairCode()))
                    .findFirst();
            if (routeOpt.isEmpty()) {
                resetActiveTarget(route, expectedTarget, SITE_15);
                inboxRepo.requeue(inboxId, Duration.ofSeconds(backoffSeconds));
                return;
            }
            if (!resetActiveTarget(route, SITE_15, expectedTarget)) {
                inboxRepo.requeue(inboxId, Duration.ofSeconds(backoffSeconds));
                //return;
            }
            Root<StatusQuerySecondaryBody> respTp5 = zipCommandService.queryDispatchStatus(ZipTarget.ZIPA);
            if (respTp5 == null || respTp5.getBody() == null || respTp5.getBody().getStatusInfos() == null)
                return;
            for (StatusQuerySecondaryBody.StatusInfo s : respTp5.getBody().getStatusInfos()) {
                if (s == null || s.getType() != 5)
                    continue;
                if (s.getStatus() == 61 || s.getStatus() == 62) {
                    //  String name = toText(s.getName());
                    //  log.("[R007] ZIPA 發現執行中任務（type=5, name={}, status=62）", name);
                    return;
                }
            }
            // 3.8 建立出庫請求：from -> Site#15
            try {
                craneRequestCommandService.createOutboundRequest(
                        c.getId(),
                        fromLocationId,
                        toId,
                        reason + ";pair=" + pairCode + ";active=" + activeTarget
                );

                // 回 ACK=START & DONE
                sendAckStart(inbox.getSender(), inbox.getTid(), lotId, carrierId, wipName);
                inboxRepo.markDone(inboxId, "R031", /*mappedTaskId*/ null);

            } catch (Exception e) {
                log.warn("[R031Walker] 建立出庫失敗：{}", e.getMessage(), e);
                inboxRepo.requeue(inboxId, Duration.ofSeconds(backoffSeconds));
            }

        } catch (Exception e) {
            log.error("[R031Walker] 例外，requeue inboxId={}, err={}", inbox.getId(), e.getMessage(), e);
            inboxRepo.requeue(inbox.getId(), Duration.ofSeconds(backoffSeconds));
        }
    }

    private static @NotNull String getResultMsg(Root<DispatchOrderSecondaryBody> resp) {
        String resultMsg = "";
        if (resp != null && resp.getBody() != null
                && resp.getBody().getResultInfos() != null
                && !resp.getBody().getResultInfos().isEmpty()
                && resp.getBody().getResultInfos().get(0) != null) {
            var ri = resp.getBody().getResultInfos().get(0);
            resultMsg = ri.getResultMessage() == null ? "" : ri.getResultMessage();
        }
        return resultMsg;
    }

    private boolean resetActiveTarget(SiteBidirRoute route, String source, String target) {
        if (route.getActiveTarget() == null || route.getActiveTarget().equals(source)) {
            route.setActiveTarget(target);
            siteBidirRouteRepository.update(route);
            return false;
        }
        return true;
    }

    /**
     * ZIPA：檢查 slot(Name==wipName) 的 message[1] 是否等於 carrierId（忽略大小寫）
     * 成功：回 Optional.empty()
     * 失敗：回 Optional.of(原因字串)
     */
    private Optional<String> verifyZipaSlotHasCarrier(String wipName, String carrierId) {
        try {
            Root<StatusQuerySecondaryBody> resp = zipCommandService.queryAllSlots(ZipTarget.ZIPA);
            if (resp == null || resp.getBody() == null || resp.getBody().getStatusInfos() == null) {
                return Optional.of("ZIPA 回覆為空");
            }
            for (StatusQuerySecondaryBody.StatusInfo s : resp.getBody().getStatusInfos()) {
                if (s == null || s.getName() == null) continue;
                if (s.getType() != 3) continue; // 只看儲格
                String name = s.getName().toString().trim();
                if (!name.equalsIgnoreCase(wipName)) continue;

                // message[1] = carrierId（若沒有視為空）
                String zipCarrier = null;
                List<?> msgList = s.getMessage();
                if (msgList != null && !msgList.isEmpty() && msgList.get(1) != null) {
                    String m1 = msgList.get(1).toString().trim();
                    zipCarrier = m1.isEmpty() ? null : m1;
                }
                if (zipCarrier != null && zipCarrier.equalsIgnoreCase(carrierId)) {
                    return Optional.empty(); // OK
                } else {
                    return Optional.of("slot=" + wipName + " carrier 不符（ZIP=" + (zipCarrier == null ? "null" : zipCarrier) + "）");
                }
            }
            return Optional.of("找不到 ZIPA slot：" + wipName);
        } catch (Exception e) {
            return Optional.of("ZIPA 查詢失敗：" + e.getMessage());
        }
    }

    /**
     * 發 ACK=START
     */
    private void sendAckStart(String targetSystem, String tid, String lotId, String carrierId, String wipName) throws Exception {
        R031AckPayload ack = new R031AckPayload();
        ack.setCmd("ROBOT");
        ack.setCmdId("R031");
        ack.setTid(tid);
        ack.setIdDesc("STK_MOVE_SCH_TO_MANUAL_PORT");

        R031AckPayload.Message msg = new R031AckPayload.Message();
        msg.setLotId(lotId);
        msg.setCarrierId(carrierId);
        msg.setWipName(wipName);
        ack.setMessage(msg);

        ack.setResult("START");
        ack.setResultMessage("dispatch created");

        // 先記錄，再發送
        logService.recordReturningId(
                "ack/r031",
                workerId,
                targetSystem,
                objectMapper.valueToTree(ack),
                MqttMessageType.ACK
        );

        publisher.publish(
                targetSystem,
                objectMapper.writeValueAsString(ack),
                MqttMessageType.ACK,
                ack.getTid(),
                ack.getCmdId()
        );

        //log.debug("[R031Walker][ACK] sent START, tid={}", tid);
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
