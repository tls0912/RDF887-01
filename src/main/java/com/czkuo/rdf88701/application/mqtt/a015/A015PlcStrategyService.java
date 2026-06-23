package com.czkuo.rdf88701.application.mqtt.a015;

import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.AmrInterlockService;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.dto.mqtt.ack.A015AckPayload;
import com.czkuo.rdf88701.common.dto.mqtt.command.A015CommandPayload;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.domain.repository.LocationTrackingRepository;
import com.czkuo.rdf88701.domain.repository.WorkingBeamRequestRepository;
import com.czkuo.rdf88701.domain.repository.WorkingBeamTaskRepository;
import com.czkuo.rdf88701.infra.entity.LocationTracking;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 策略#2：STK03 / STK04 / STK05
 * - 機制：W0015 寫 pass-enable=1 → 等 W1015 可取(=1) → 回 A015 ACK(DONE)
 * - TID：沿用原始 A015 的 TID
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class A015PlcStrategyService {

    private final WorkingBeamRequestRepository workingBeamRequestRepository;
    private final WorkingBeamTaskRepository workingBeamTaskRepository;
    private final AmrInterlockService interlock;
    private final LocationTrackingRepository locationTrackingRepository;
    private final MqttMessageEventPublisher publisher;
    private final MqttMessageLogService logService;
    private final ObjectMapper mapper;

    // ====== 常數與對應 ======
    private static final Set<String> PORTS = Set.of("STK03", "STK04", "STK05");

    /**
     * 站口 → 位置別名
     */
    private static final Map<String, String> PORT_TO_LOCATION_ALIAS = Map.of(
            "STK03", "Site#1",
            "STK04", "Transfer#2",
            "STK05", "Site#17"
    );

    /**
     * 等待 PLC 「可取」的逾時（毫秒）
     */
    @Value("${app.a015.plc.wait-ready-timeout-ms:8000}")
    private long waitReadyTimeoutMs;

    /**
     * 執行 PLC 互鎖：enable → waitReady → DONE
     *
     * @param fromSystem 原始來源系統（預期為 SEEC）
     * @param cmd        A015 原始 payload
     */
    public void handle(String fromSystem, A015CommandPayload cmd) {
        String tid = cmd.getMessage().getTid();
        String dest = cmd.getMessage() != null ? cmd.getMessage().getDestLoc() : "";

        int idx = dest.indexOf("_");
        if (idx > 0) {
            dest = dest.substring(0, idx);
        }

        try {

            // 0) 確認該位置有無帳
            boolean hasContainer = true;
            long containerMainId = 0L;
            switch (dest) {
                case "STK03" -> {
                    Optional<LocationTracking> trackingOpt = locationTrackingRepository.findByLocationPointId(205L);
                    hasContainer = trackingOpt.isPresent();
                    containerMainId = hasContainer ? trackingOpt.orElse(null).getContainerMainId() : 0L;
                }
                case "STK04" -> {
                    Optional<Long> containerOnTransfer = locationTrackingRepository.findContainerOnTransfer(2L);
                    hasContainer = containerOnTransfer.isPresent();
                    containerMainId = hasContainer ? containerOnTransfer.orElse(0L) : 0L;
                }
                case "STK05" -> {
                    Optional<LocationTracking> trackingOpt = locationTrackingRepository.findByLocationPointId(215L);
                    hasContainer = trackingOpt.isPresent();
                    containerMainId = hasContainer ? trackingOpt.orElse(null).getContainerMainId() : 0L;
                }
            }
            if (hasContainer) {
                log.warn("[A015][PLC] enableDrop 失敗，位置有帳，container id={}, tid={}, dest={}",
                        containerMainId, tid, dest);
                sendAck(fromSystem, tid, cmd, "RETRY", "");
                return;
            }

            // 1) 置 pass-enable=1（讓 PLC 允許 AMR 取）
            boolean enOk = interlock.enableDrop(dest);
            if (!enOk) {
                log.warn("[A015][PLC] enableDrop 失敗，tid={}, dest={}", tid, dest);
                // sendAck(fromSystem, tid, cmd, "OK", "PLC enablePick failed");
                sendAck(fromSystem, tid, cmd, "RETRY", "");
                return;
            }

            // 2) 等待 W1015 ready=1（可取）
            boolean ready = interlock.waitReady(dest, waitReadyTimeoutMs);
            if (!ready) {
                log.warn("[A015][PLC] waitReady 逾時，tid={}, dest={}", tid, dest);
                // sendAck(fromSystem, tid, cmd, "OK", "PLC not ready in time");
                sendAck(fromSystem, tid, cmd, "RETRY", "");
                return;
            }

            // 3) DONE（光閘閉合 or 互鎖就緒）
            sendAck(fromSystem, tid, cmd, "DONE", "");
            log.info("[A015][PLC] DONE，tid={}, dest={}", tid, dest);

        } catch (Exception e) {
            log.error("[A015][PLC] 例外，tid={}, dest={}, err={}", tid, dest, e.getMessage(), e);
            // sendAck(fromSystem, tid, cmd, "OK", "PLC exception: " + e.getMessage());
        }
    }

    private void sendAck(String targetSystem, String tid, A015CommandPayload cmd, String result, String resultMessage) {
        try {
            A015AckPayload ack = new A015AckPayload();
            ack.setCmd("AGV");
            ack.setCmdId("A015");
            ack.setTid(tid);
            ack.setResult(result);
            ack.setResultMessage(resultMessage);

            A015AckPayload.Message msg = new A015AckPayload.Message();
            msg.setTid(cmd.getMessage().getTid());
            msg.setDeviceName(cmd.getMessage().getDeviceName());
            msg.setDestLoc(cmd.getMessage().getDestLoc());

            ack.setMessage(msg);

            logService.recordReturningId(
                    "ack/a015",
                    "a015-plc-strategy",
                    targetSystem,
                    mapper.valueToTree(ack),
                    MqttMessageType.ACK
            );
            publisher.publish(targetSystem,
                    mapper.writeValueAsString(ack),
                    MqttMessageType.ACK,
                    ack.getTid(),
                    ack.getCmdId());
        } catch (Exception e) {
            log.error("[A015][PLC] 發送 ACK 失敗，tid={}, err={}", tid, e.getMessage(), e);
        }
    }

    /**
     * 指定工作樑裝置是否忙碌（有未完成請求或任務）
     */
    private boolean workingBeamBusy(long workingBeamId) {
        return workingBeamRequestRepository.existsUnfinishedRequestForBeam(workingBeamId)
                || workingBeamTaskRepository.existsUnfinishedTaskForBeam(workingBeamId);
    }
}
