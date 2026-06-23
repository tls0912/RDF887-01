package com.czkuo.rdf88701.infra.event;

import com.czkuo.rdf88701.domain.plc.state.Strapping.StrappingDeviceStatus;
import com.czkuo.rdf88701.domain.plc.state.infrared.InfraredDeviceStatus;
import com.czkuo.rdf88701.domain.plc.state.site.SiteCommandStatus;
import com.czkuo.rdf88701.domain.plc.state.site.SiteDeviceStatus;
import com.czkuo.rdf88701.domain.plc.valueobject.InfraredStatus;
import com.czkuo.rdf88701.domain.plc.valueobject.StrappingStatus;
import com.czkuo.rdf88701.infra.event.model.plc.connection.PlcConnectedEvent;
import com.czkuo.rdf88701.infra.event.model.plc.connection.PlcDisconnectedEvent;
import com.czkuo.rdf88701.infra.event.model.plc.connection.PlcReconnectAttemptEvent;
import com.czkuo.rdf88701.infra.event.model.plc.connection.PlcReconnectResultEvent;
import com.czkuo.rdf88701.infra.event.model.plc.crane.CraneCommandOverdueEvent;
import com.czkuo.rdf88701.infra.event.model.plc.crane.CraneCommandUpdatedEvent;
import com.czkuo.rdf88701.infra.event.model.plc.crane.CraneStatusOverdueEvent;
import com.czkuo.rdf88701.infra.event.model.plc.crane.CraneStatusUpdatedEvent;
import com.czkuo.rdf88701.infra.event.model.plc.gripper.GripperCommandOverdueEvent;
import com.czkuo.rdf88701.infra.event.model.plc.gripper.GripperCommandUpdatedEvent;
import com.czkuo.rdf88701.infra.event.model.plc.gripper.GripperStatusUpdatedEvent;
import com.czkuo.rdf88701.infra.event.model.plc.gripper.GripperStatusOverdueEvent;
import com.czkuo.rdf88701.infra.event.model.plc.health.PlcHeartbeatCheckedEvent;
import com.czkuo.rdf88701.infra.event.model.plc.health.PlcHeartbeatTimeoutEvent;
import com.czkuo.rdf88701.infra.event.model.plc.infrared.InfraredCommandOverdueEvent;
import com.czkuo.rdf88701.infra.event.model.plc.infrared.InfraredCommandUpdatedEvent;
import com.czkuo.rdf88701.infra.event.model.plc.infrared.InfraredStatusOverdueEvent;
import com.czkuo.rdf88701.infra.event.model.plc.infrared.InfraredStatusUpdatedEvent;
import com.czkuo.rdf88701.infra.event.model.plc.safety.SafetyStatusOverdueEvent;
import com.czkuo.rdf88701.infra.event.model.plc.safety.SafetyStatusUpdatedEvent;
import com.czkuo.rdf88701.infra.event.model.plc.site.SiteCommandOverdueEvent;
import com.czkuo.rdf88701.infra.event.model.plc.site.SiteCommandUpdatedEvent;
import com.czkuo.rdf88701.infra.event.model.plc.site.SiteStatusOverdueEvent;
import com.czkuo.rdf88701.infra.event.model.plc.site.SiteStatusUpdatedEvent;
import com.czkuo.rdf88701.infra.event.model.plc.strapping.StrappingCommandOverdueEvent;
import com.czkuo.rdf88701.infra.event.model.plc.strapping.StrappingCommandUpdatedEvent;
import com.czkuo.rdf88701.infra.event.model.plc.strapping.StrappingStatusOverdueEvent;
import com.czkuo.rdf88701.infra.event.model.plc.strapping.StrappingStatusUpdatedEvent;
import com.czkuo.rdf88701.infra.event.model.plc.transfer.TransferCommandOverdueEvent;
import com.czkuo.rdf88701.infra.event.model.plc.transfer.TransferCommandUpdatedEvent;
import com.czkuo.rdf88701.infra.event.model.plc.transfer.TransferStatusOverdueEvent;
import com.czkuo.rdf88701.infra.event.model.plc.transfer.TransferStatusUpdatedEvent;
import com.czkuo.rdf88701.infra.event.model.plc.workingbeam.WorkingBeamCommandOverdueEvent;
import com.czkuo.rdf88701.infra.event.model.plc.workingbeam.WorkingBeamCommandUpdatedEvent;
import com.czkuo.rdf88701.infra.event.model.plc.workingbeam.WorkingBeamStatusOverdueEvent;
import com.czkuo.rdf88701.infra.event.model.plc.workingbeam.WorkingBeamStatusUpdatedEvent;
import com.czkuo.rdf88701.presentation.websocket.WebSocketPushService;
import com.czkuo.rdf88701.presentation.websocket.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * PlcEventPublisher
 * <p>
 * 統一封裝的事件發送器。
 * 負責將各種 PLC 相關事件（連線、心跳、Gripper 狀態）發送出去，
 * 呼叫端無需關心實際是 Spring 事件、Kafka、MQTT 等，未來可自由擴充替換。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlcEventPublisher {

    private final ApplicationEventPublisher springPublisher;
    private final WebSocketPushService webSocketPushService;

    /**
     * 發送通用事件（基礎發送方法）
     */
    public void publish(Object event) {
        if (event == null) {
            log.warn("[EVENT] 嘗試發送 null 事件，忽略");
            return;
        }
        springPublisher.publishEvent(event);
        //log.debug("[EVENT] Publish event: {}", event.getClass().getSimpleName());
    }

    // =================== 連線相關 ===================

    /**
     * 發送 PLC 成功連線事件
     */
    public void publishConnected(PlcConnectedEvent event) {
        publish(event);
    }

    /**
     * 發送 PLC 斷線事件
     */
    public void publishDisconnected(PlcDisconnectedEvent event) {
        publish(event);
    }

    /**
     * 發送 PLC 重連嘗試事件（開始嘗試連線）
     */
    public void publishReconnectAttempt(PlcReconnectAttemptEvent event) {
        publish(event);
    }

    /**
     * 發送 PLC 重連結果事件（成功或失敗）
     */
    public void publishReconnectResult(PlcReconnectResultEvent event) {
        publish(event);
    }

    // =================== 心跳相關 ===================

    /**
     * 發送 PLC 心跳檢查成功事件
     */
    public void publishHeartbeatChecked(PlcHeartbeatCheckedEvent event) {
        publish(event);
    }

    /**
     * 發送 PLC 心跳逾時事件
     */
    public void publishHeartbeatTimeout(PlcHeartbeatTimeoutEvent event) {
        publish(event);
    }

    // =================== Crane 狀態相關 ===================

    public void publishCraneStatusOverdue(CraneStatusOverdueEvent event) {
        publish(event);
        log.warn("[EVENT] 發送 CraneStatusOverdueEvent, craneId={}, elapsed={}ms",
                event.getCraneId(), event.getElapsedMillis());
    }

    public void publishCraneCommandOverdue(CraneCommandOverdueEvent event) {
        publish(event);
        log.warn("[EVENT] 發送 CraneCommandOverdueEvent craneId={}, elapsed={}ms",
                event.getCraneId(), event.getElapsedMillis());
    }

    public void publishCraneStatusUpdated(CraneStatusUpdatedEvent event) {
        publish(event);
        CraneStatusUpdatedMessage message = toWebSocketMessage(event);
        webSocketPushService.pushCraneStatus(message);
        //log.debug("[WEBSOCKET] 推播 CraneStatusUpdatedMessage 成功 craneId={}, state={}",
//                event.getCraneId(), event.getCurrentState());
    }

    public void publishCraneCommandUpdated(CraneCommandUpdatedEvent event) {
        publish(event);
        log.info("[EVENT] 發送 CraneCommandUpdatedEvent craneId={} FromCST={} ToCST={}",
                event.getCraneId(), event.getFromCstId(), event.getToCstId());
    }

    public void publishCraneStatusUpdatedBatch(List<CraneStatusUpdatedEvent> events) {
        if (events == null || events.isEmpty()) return;
        for (CraneStatusUpdatedEvent event : events) publishCraneStatusUpdated(event);
        List<CraneStatusUpdatedMessage> messageList = events.stream().map(this::toWebSocketMessage).toList();
        CraneStatusBatchMessage batchMessage = CraneStatusBatchMessage.builder().cranes(messageList).build();
        webSocketPushService.pushCraneStatusBatch(batchMessage);
        //log.debug("[WEBSOCKET] 推播 CraneStatusBatchMessage, count={}", messageList.size());
    }

    /**
     * 批量推送 Crane 指令狀態更新事件。
     * - 推送 Spring Event
     * - 同時轉換成 WebSocket 訊息批次推播
     */
    public void publishCraneCommandUpdatedBatch(List<CraneCommandUpdatedEvent> events) {
        if (events == null || events.isEmpty()) return;

        // 發送 Spring Event
        for (CraneCommandUpdatedEvent event : events) {
            publishCraneCommandUpdated(event);
        }

        // 組成 WebSocket 批次訊息並推送
        List<CraneCommandUpdatedMessage> messageList = events.stream()
                .map(this::toWebSocketMessage)
                .toList();

        CraneCommandBatchMessage batchMessage = CraneCommandBatchMessage.builder()
                .commands(messageList)
                .build();

        webSocketPushService.pushCraneCommandStatusBatch(batchMessage);

        //log.debug("[WEBSOCKET] 推播 CraneCommandBatchMessage, count={}", messageList.size());
    }

    /**
     * 將 CraneCommandUpdatedEvent 轉換為 WebSocket 推播訊息格式
     */
    private CraneCommandUpdatedMessage toWebSocketMessage(CraneCommandUpdatedEvent event) {
        var status = event.getCommandStatus();
        var cmd = status.getCommand();

        return CraneCommandUpdatedMessage.builder()
                .timestamp(status.getSnapshotTime() != null ? status.getSnapshotTime() : Instant.now())
                .craneId(event.getCraneId())

                // Bit 區
                .transferReady(status.isTransferReady())
                .fromTransferCmdReq(status.isFromTransferCmdReq())
                .fromTransferCompAck(status.isFromTransferCompAck())
                .toTransferCmdReq(status.isToTransferCmdReq())
                .toTransferCompAck(status.isToTransferCompAck())
                .homeReturnRequest(status.isHomeReturnRequest())
                .removeAccountAck(status.isRemoveAccountAck())

                // Word 區
                .fromTransferNo(cmd != null ? cmd.getFromTransferNo() : -1)
                .fromCstId(cmd != null ? cmd.getFromCstId() : null)
                .fromLocationType(cmd != null ? cmd.getFromLocationType() : -1)
                .fromLocationBank(cmd != null ? cmd.getFromLocationBank() : -1)
                .fromLocationBay(cmd != null ? cmd.getFromLocationBay() : -1)
                .fromLocationLv(cmd != null ? cmd.getFromLocationLv() : -1)
                .fromCraneCommandType(cmd != null ? cmd.getFromCraneCommandType() : null)

                .toTransferNo(cmd != null ? cmd.getToTransferNo() : -1)
                .toCstId(cmd != null ? cmd.getToCstId() : null)
                .toLocationType(cmd != null ? cmd.getToLocationType() : -1)
                .toLocationBank(cmd != null ? cmd.getToLocationBank() : -1)
                .toLocationBay(cmd != null ? cmd.getToLocationBay() : -1)
                .toLocationLv(cmd != null ? cmd.getToLocationLv() : -1)
                .toTransferType(cmd != null ? cmd.getToCraneCommandType() : null)

                .stale(status.isStale())
                .build();
    }

    private CraneStatusUpdatedMessage toWebSocketMessage(CraneStatusUpdatedEvent event) {
        var status = event.getDeviceStatus();
        return CraneStatusUpdatedMessage.builder()
                .timestamp(status.getSnapshotTime() != null ? status.getSnapshotTime() : Instant.now())
                .craneId(event.getCraneId())
                .stateMachineState(event.getCurrentState().name())
                .transferStandby(status.isTransferStandby())
                .cstPresent(status.isCstPresent())
                .readyHandleFromCmd(status.isReadyHandleFromCmd())
                .readyHandleToCmd(status.isReadyHandleToCmd())
                .fromJobHandling(status.isFromJobHandling())
                .fromTransferCmdAck(status.isFromTransferCmdAck())
                .fromTransferCompReq(status.isFromTransferCompReq())
                .toJobHandling(status.isToJobHandling())
                .toTransferCmdAck(status.isToTransferCmdAck())
                .toTransferCompReq(status.isToTransferCompReq())
                .homeReturnAck(status.isHomeReturnAck())
                .removeAccountReq(status.isRemoveAccountReq())
                .bayPosition(status.getBayPosition())
                .levelPosition(status.getLevelPosition())
                .bankPosition(status.getBankPosition())
                .deviceStatusCode(String.format("0x%02X", status.getDeviceStatus()))
                .fromReturnCode(String.format("0x%04X", status.getFromReturnCode()))
                .toReturnCode(String.format("0x%04X", status.getToReturnCode()))
                .productId(status.getProductId())
                .build();
    }

    // =================== Gripper 狀態相關 ===================

    /**
     * 發送 Gripper 指令過期事件（GripperCommandOverdueEvent）
     * - 用於監控指令是否卡住或長時間未回應
     */
    public void publishGripperCommandOverdue(GripperCommandOverdueEvent event) {
        publish(event);
        log.warn("[EVENT] 發送 GripperCommandOverdueEvent, GripperId={}, elapsed={}ms",
                event.getGripperId(), event.getElapsedMillis());
    }

    /**
     * 發送 Gripper 資料過期警告事件（資料超時未更新）
     */
    public void publishGripperStatusOverdue(GripperStatusOverdueEvent event) {
        publish(event);
    }

    /**
     * 發送 Gripper 最新狀態更新事件（新鮮資料）
     * 同時推播 WebSocket 給前端
     */
    public void publishGripperStatusUpdated(GripperStatusUpdatedEvent event) {
        publish(event);

        // 額外推 WebSocket
        GripperStatusUpdatedMessage message = toWebSocketMessage(event);
        webSocketPushService.pushGripperStatus(message);

        //log.debug("[WEBSOCKET] 推播 GripperStatusUpdatedMessage 成功 gripperId={}, state={}",
//                event.getGripperId(), event.getStateMachineState());
    }


    public void publishGripperCommandUpdated(GripperCommandUpdatedEvent event) {
        publish(event);
        log.info("[EVENT] 發送 GripperCommandUpdatedEvent gripperId={} taskType={}",
                event.getGripperId(), event.getCommandType());
    }

    public void publishGripperStatusUpdatedBatch(List<GripperStatusUpdatedEvent> events) {
        if (events == null || events.isEmpty()) return;
        for (GripperStatusUpdatedEvent event : events) publishGripperStatusUpdated(event);
        List<GripperStatusUpdatedMessage> messageList = events.stream().map(this::toWebSocketMessage).toList();
        GripperStatusBatchMessage batchMessage = GripperStatusBatchMessage.builder().grippers(messageList).build();
        webSocketPushService.pushGripperStatusBatch(batchMessage);
        //log.debug("[WEBSOCKET] 推播 GripperStatusBatchMessage, count={}", messageList.size());
    }

    /**
     * 批量推送 Gripper 指令狀態更新事件。
     * - 推送 Spring Event
     * - 同時轉換成 WebSocket 訊息批次推播
     */
    public void publishGripperCommandUpdatedBatch(List<GripperCommandUpdatedEvent> events) {
        if (events == null || events.isEmpty()) return;

        // 發送 Spring Event
        for (GripperCommandUpdatedEvent event : events) {
            publishGripperCommandUpdated(event);
        }

        // 組成 WebSocket 批次訊息並推送
        List<GripperCommandUpdatedMessage> messageList = events.stream()
                .map(this::toWebSocketMessage)
                .toList();

        GripperCommandBatchMessage batchMessage = GripperCommandBatchMessage.builder()
                .commands(messageList)
                .build();

        webSocketPushService.pushGripperCommandStatusBatch(batchMessage);

        //log.debug("[WEBSOCKET] 推播 GripperCommandBatchMessage, count={}", messageList.size());
    }

    /**
     * 將 GripperStatusUpdatedEvent 轉成 WebSocket 推播資料
     */
    private GripperStatusUpdatedMessage toWebSocketMessage(GripperStatusUpdatedEvent event) {
        var status = event.getDeviceStatus();

        return GripperStatusUpdatedMessage.builder()
                .timestamp(status.getSnapshotTime() != null ? status.getSnapshotTime() : Instant.now())
                .gripperId(event.getGripperId())
                .stateMachineState(event.getStateMachineState().name())
                .ready(status.isTransferStandby())
                .productPresent(status.isProductPresent())
                .transferCmdAck(status.isTransferCmdAck())
                .transferCompReq(status.isTransferCompReq())
                .alarm(status.isAlarm())
                .bay(status.getBay())
                .level(status.getLevel())
                .bank(status.getBank())
                .deviceStatusCode(String.format("0x%02X", status.getGripperStatus().getRaw()))
                .returnCode(String.format("0x%02X", status.getReturnCode()))
                .productId(status.getProductId())
                .stale(status.isStale())
                .build();
    }

    private GripperCommandUpdatedMessage toWebSocketMessage(GripperCommandUpdatedEvent event) {
        var status = event.getCommandStatus();
        var cmd = (status != null) ? status.getCommand() : null;

        return GripperCommandUpdatedMessage.builder()
                .timestamp((status != null && status.getSnapshotTime() != null)
                        ? status.getSnapshotTime() : Instant.now())
                .gripperId(event.getGripperId())                                 // Gripper 裝置 ID

                // Bit 區控制流程相關位元
                .gripperReady(status != null && status.isTransferReady())        // 是否 Ready
                .gripperCmdReq(status != null && status.isTransferCmdReq())      // 是否發出命令請求
                .gripperCompAck(status != null && status.isTransferCompAck())    // 是否完成回應確認

                // Word 區指令內容（從 PC → PLC 寫入的命令內容）
                .gripperNo(cmd != null ? cmd.getTransferNo() : -1)              // 指令編號
                .taskType(cmd != null ? cmd.getTaskType() : null)               // 指令類型（PICK / DROP / MOVE）
                .bank(cmd != null ? cmd.getLocationBank() : -1)         // Bank
                .bay(cmd != null ? cmd.getLocationBay() : -1)           // Bay
                .level(cmd != null ? cmd.getLocationLevel() : -1)       // Level
                .productId(cmd != null ? cmd.getProductId() : "")               // 產品條碼

                .stale(status != null && status.isStale())                      // 是否過期
                .build();
    }


    // =================== WorkingBeam 狀態相關 ===================

    /**
     * 推播 WorkingBeam 指令過期事件
     */
    public void publishWorkingBeamCommandOverdue(WorkingBeamCommandOverdueEvent event) {
        publish(event);
        log.warn("[EVENT] 發送 WorkingBeamCommandOverdueEvent, WorkingBeamId={}, elapsed={}ms",
                event.getWorkingBeamId(), event.getElapsedMillis());
    }

    /**
     * 推播 WorkingBeam 狀態過期事件
     */
    public void publishWorkingBeamStatusOverdue(WorkingBeamStatusOverdueEvent event) {
        publish(event);
        log.warn("[EVENT] 發送 WorkingBeamStatusOverdueEvent, WorkingBeamId={}, elapsed={}ms",
                event.getWorkingBeamId(), event.getElapsedMillis());
    }

    /**
     * 推播單筆 WorkingBeam 狀態更新（事件 + WebSocket）
     */
    public void publishWorkingBeamStatusUpdated(WorkingBeamStatusUpdatedEvent event) {
        publish(event);
        WorkingBeamStatusUpdatedMessage message = toWebSocketMessage(event);
        webSocketPushService.pushWorkingBeamStatus(message);
        //log.debug("[WEBSOCKET] 推播 WorkingBeamStatusUpdatedMessage 成功, WorkingBeamId={}, state={}",
//                event.getWorkingBeamId(), event.getCurrentState());
    }

    /**
     * 推播單筆 WorkingBeam 指令狀態更新（事件）
     */
    public void publishWorkingBeamCommandUpdated(WorkingBeamCommandUpdatedEvent event) {
        publish(event);
        log.info("[EVENT] 發送 WorkingBeamCommandUpdatedEvent, WorkingBeamId={}", event.getWorkingBeamId());
    }

    /**
     * 推播多筆 WorkingBeam 狀態更新（事件 + 批次 WebSocket）
     */
    public void publishWorkingBeamStatusUpdatedBatch(List<WorkingBeamStatusUpdatedEvent> events) {
        if (events == null || events.isEmpty()) return;

        for (WorkingBeamStatusUpdatedEvent event : events) {
            publishWorkingBeamStatusUpdated(event);
        }

        List<WorkingBeamStatusUpdatedMessage> messageList = events.stream()
                .map(this::toWebSocketMessage)
                .toList();

        WorkingBeamStatusBatchMessage batchMessage = WorkingBeamStatusBatchMessage.builder()
                .workingBeams(messageList) // 🔧 小寫修正
                .build();

        webSocketPushService.pushWorkingBeamStatusBatch(batchMessage);
        //log.debug("[WEBSOCKET] 推播 WorkingBeamStatusBatchMessage, count={}", messageList.size());
    }

    /**
     * 推播多筆 WorkingBeam 指令狀態更新（事件 + 批次 WebSocket）
     */
    public void publishWorkingBeamCommandUpdatedBatch(List<WorkingBeamCommandUpdatedEvent> events) {
        if (events == null || events.isEmpty()) return;

        for (WorkingBeamCommandUpdatedEvent event : events) {
            publishWorkingBeamCommandUpdated(event);
        }

        List<WorkingBeamCommandUpdatedMessage> messageList = events.stream()
                .map(this::toWebSocketMessage)
                .toList();

        WorkingBeamCommandBatchMessage batchMessage = WorkingBeamCommandBatchMessage.builder()
                .commands(messageList)
                .build();

        webSocketPushService.pushWorkingBeamCommandStatusBatch(batchMessage);
        //log.debug("[WEBSOCKET] 推播 WorkingBeamCommandBatchMessage, count={}", messageList.size());
    }

    /**
     * 將 CommandUpdatedEvent 轉為 WebSocket DTO（防 null）
     */
    private WorkingBeamCommandUpdatedMessage toWebSocketMessage(WorkingBeamCommandUpdatedEvent event) {
        var status = event.getCommandStatus();
        var cmd = (status != null) ? status.getCommand() : null;

        return WorkingBeamCommandUpdatedMessage.builder()
                .timestamp((status != null && status.getSnapshotTime() != null)
                        ? status.getSnapshotTime() : Instant.now())
                .workingBeamId(event.getWorkingBeamId())

                // Bit 區位元
                .transferReady(status != null && status.isTransferReady())
                .transferCmdReq(status != null && status.isTransferCmdReq())
                .transferCompAck(status != null && status.isTransferCompAck())

                // Word 區欄位（若無資料則預設 -1/null）
                .transferNo(cmd != null ? cmd.getTransferNo() : -1)
                .commandType(cmd != null ? cmd.getCommandType(): null)
                .commandMeta(cmd != null ? cmd.getCommandMeta() : null)

                .stale(status != null && status.isStale())
                .build();
    }

    /**
     * 將 StatusUpdatedEvent 轉為 WebSocket DTO
     */
    private WorkingBeamStatusUpdatedMessage toWebSocketMessage(WorkingBeamStatusUpdatedEvent event) {
        var status = event.getDeviceStatus();

        return WorkingBeamStatusUpdatedMessage.builder()
                .timestamp(status != null && status.getSnapshotTime() != null
                        ? status.getSnapshotTime()
                        : Instant.now())
                .workingBeamId(event.getWorkingBeamId())
                .currentState(event.getCurrentState().name())

                .transferStandby(status != null && status.isTransferStandby())
                .transferCmdAck(status != null && status.isTransferCmdAck())
                .transferCompReq(status != null && status.isTransferCompReq())
                .alarm(status != null && status.isAlarm())
                .deviceStatusCode(status != null && status.getWorkingBeamStatus() != null
                        ? String.format("0x%02X", status.getWorkingBeamStatus().getWorkingStatus())
                        : "0x00")
                .returnCode(status != null
                        ? String.format("0x%04X", status.getReturnCode())
                        : "0x0000")

                .stale(status != null && status.isStale())
                .build();
    }

    // =================== Transfer 狀態相關 ===================

    public void publishTransferCommandOverdue(TransferCommandOverdueEvent event) {
        publish(event);
        log.warn("[EVENT] 發送 TransferCommandOverdueEvent, TransferId={}, elapsed={}ms",
                event.getTransferId(), event.getElapsedMillis());
    }

    public void publishTransferStatusOverdue(TransferStatusOverdueEvent event) {
        publish(event);
        log.warn("[EVENT] 發送 TransferStatusOverdueEvent, TransferId={}, elapsed={}ms",
                event.getTransferId(), event.getElapsedMillis());
    }

    public void publishTransferStatusUpdated(TransferStatusUpdatedEvent event) {
        publish(event);
        TransferStatusUpdatedMessage message = toWebSocketMessage(event);
        webSocketPushService.pushTransferStatus(message);
        //log.debug("[WEBSOCKET] 推播 TransferStatusUpdatedMessage 成功, TransferId={}, state={}",
//                event.getTransferId(), event.getCurrentState());
    }

    public void publishTransferCommandUpdated(TransferCommandUpdatedEvent event) {
        publish(event);
        log.info("[EVENT] 發送 TransferCommandUpdatedEvent, TransferId={}", event.getTransferId());
    }

    public void publishTransferStatusUpdatedBatch(List<TransferStatusUpdatedEvent> events) {
        if (events == null || events.isEmpty()) return;

        for (TransferStatusUpdatedEvent event : events) {
            publishTransferStatusUpdated(event);
        }

        List<TransferStatusUpdatedMessage> messageList = events.stream()
                .map(this::toWebSocketMessage)
                .toList();

        TransferStatusBatchMessage batchMessage = TransferStatusBatchMessage.builder()
                .transfers(messageList)
                .build();

        webSocketPushService.pushTransferStatusBatch(batchMessage);
        //log.debug("[WEBSOCKET] 推播 TransferStatusBatchMessage, count={}", messageList.size());
    }

    public void publishTransferCommandUpdatedBatch(List<TransferCommandUpdatedEvent> events) {
        if (events == null || events.isEmpty()) return;

        for (TransferCommandUpdatedEvent event : events) {
            publishTransferCommandUpdated(event);
        }

        List<TransferCommandUpdatedMessage> messageList = events.stream()
                .map(this::toWebSocketMessage)
                .toList();

        TransferCommandBatchMessage batchMessage = TransferCommandBatchMessage.builder()
                .commands(messageList)
                .build();

        webSocketPushService.pushTransferCommandStatusBatch(batchMessage);
        //log.debug("[WEBSOCKET] 推播 TransferCommandBatchMessage, count={}", messageList.size());
    }

    private TransferStatusUpdatedMessage toWebSocketMessage(TransferStatusUpdatedEvent event) {
        var status = event.getDeviceStatus();

        return TransferStatusUpdatedMessage.builder()
                .timestamp(status != null && status.getSnapshotTime() != null
                        ? status.getSnapshotTime()
                        : Instant.now())
                .transferId(event.getTransferId())
                .currentState(event.getCurrentState().name())

                .transferStandby(status != null && status.isTransferStandby())
                .transferCmdAck(status != null && status.isTransferCmdAck())
                .transferCompReq(status != null && status.isTransferCompReq())
                .alarm(status != null && status.isAlarm())
                .deviceStatusCode(status != null
                        ? String.format("0x%02X", status.getTransferStatus().getTransferStatus())
                        : "0x00")
                .returnCode(status != null
                        ? String.format("0x%04X", status.getReturnCode())
                        : "0x0000")

                .stale(status != null && status.isStale())
                .build();
    }

    private TransferCommandUpdatedMessage toWebSocketMessage(TransferCommandUpdatedEvent event) {
        var status = event.getCommandStatus();
        var cmd = (status != null) ? status.getCommand() : null;

        return TransferCommandUpdatedMessage.builder()
                .timestamp((status != null && status.getSnapshotTime() != null)
                        ? status.getSnapshotTime() : Instant.now())
                .transferId(event.getTransferId())                           // Transfer 裝置 ID

                // Bit 資訊（控制流程相關位元）
                .transferReady(status != null && status.isTransferReady())  // 是否 Ready
                .transferCmdReq(status != null && status.isTransferCmdReq())// 是否發出命令請求
                .transferCompAck(status != null && status.isTransferCompAck()) // 是否完成回應確認

                // Word 區指令資訊（PLC 傳送控制命令內容）
                .transferNo(cmd != null ? cmd.getTransferNo() : -1)         // 指令編號
                .commandType(cmd != null ? cmd.getTaskType() : null)        // 指令類型（PICK / DROP / MOVE）
                .locationBank(cmd != null ? cmd.getLocationBank() : -1)     // Bank
                .locationBay(cmd != null ? cmd.getLocationBay() : -1)       // Bay
                .locationLevel(cmd != null ? cmd.getLocationLevel() : -1)   // Level
                .productId(cmd != null ? cmd.getProductId() : "")           // 產品條碼

                .stale(status != null && status.isStale())                  // 是否過期
                .build();
    }

    // =================== Strapping 狀態相關 ===================

    /**
     * 推播 Strapping 指令過期事件
     */
    public void publishStrappingCommandOverdue(StrappingCommandOverdueEvent event) {
        publish(event);
        log.warn("[EVENT] 發送 StrappingCommandOverdueEvent, StrappingId={}, elapsed={}ms",
                event.getStrappingId(), event.getElapsedMillis());
    }

    /**
     * 推播 Strapping 狀態過期事件
     */
    public void publishStrappingStatusOverdue(StrappingStatusOverdueEvent event) {
        publish(event);
        log.warn("[EVENT] 發送 StrappingStatusOverdueEvent, StrappingId={}, elapsed={}ms",
                event.getStrappingId(), event.getElapsedMillis());
    }

    /**
     * 推播單筆 Strapping 狀態更新（事件 + WebSocket）
     */
    public void publishStrappingStatusUpdated(StrappingStatusUpdatedEvent event) {
        publish(event);
        StrappingStatusUpdatedMessage message = toWebSocketMessage(event);
        webSocketPushService.pushStrappingStatus(message);
        //log.debug("[WEBSOCKET] 推播 StrappingStatusUpdatedMessage 成功, StrappingId={}, state={}",
//                event.getStrappingId(), event.getCurrentState());
    }

    /**
     * 推播單筆 Strapping 指令狀態更新（事件）
     */
    public void publishStrappingCommandUpdated(StrappingCommandUpdatedEvent event) {
        publish(event);
        log.info("[EVENT] 發送 StrappingCommandUpdatedEvent, StrappingId={}", event.getStrappingId());
    }

    /**
     * 推播多筆 Strapping 狀態更新（事件 + 批次 WebSocket）
     */
    public void publishStrappingStatusUpdatedBatch(List<StrappingStatusUpdatedEvent> events) {
        if (events == null || events.isEmpty()) return;

        for (StrappingStatusUpdatedEvent event : events) {
            publishStrappingStatusUpdated(event);
        }

        List<StrappingStatusUpdatedMessage> messageList = events.stream()
                .map(this::toWebSocketMessage)
                .toList();

        StrappingStatusBatchMessage batchMessage = StrappingStatusBatchMessage.builder()
                .strappings(messageList)
                .build();

        webSocketPushService.pushStrappingStatusBatch(batchMessage);
        //log.debug("[WEBSOCKET] 推播 StrappingStatusBatchMessage, count={}", messageList.size());
    }

    /**
     * 推播多筆 Strapping 指令狀態更新（事件 + 批次 WebSocket）
     */
    public void publishStrappingCommandUpdatedBatch(List<StrappingCommandUpdatedEvent> events) {
        if (events == null || events.isEmpty()) return;

        for (StrappingCommandUpdatedEvent event : events) {
            publishStrappingCommandUpdated(event);
        }

        List<StrappingCommandUpdatedMessage> messageList = events.stream()
                .map(this::toWebSocketMessage)
                .toList();

        StrappingCommandBatchMessage batchMessage = StrappingCommandBatchMessage.builder()
                .commands(messageList)
                .build();

        webSocketPushService.pushStrappingCommandStatusBatch(batchMessage);
        //log.debug("[WEBSOCKET] 推播 StrappingCommandBatchMessage, count={}", messageList.size());
    }

    /**
     * 將 CommandUpdatedEvent 轉為 WebSocket DTO（防 null）
     */
    private StrappingCommandUpdatedMessage toWebSocketMessage(StrappingCommandUpdatedEvent event) {
        var status = event.getCommandStatus();
        var cmd = (status != null) ? status.getCommand() : null;

        return StrappingCommandUpdatedMessage.builder()
                .timestamp((status != null && status.getSnapshotTime() != null)
                        ? status.getSnapshotTime() : Instant.now())
                .strappingId(event.getStrappingId())

                // Bit 區位元
                .strappingReady(status != null && status.isStrappingReady())
                .strappingCmdReq(status != null && status.isStrappingCmdReq())
                .strappingCompAck(status != null && status.isStrappingCompAck())

                // Word 區欄位
                .strappingNo(cmd != null ? cmd.getStrappingNo() : -1)
                .strappingCount(cmd != null ? cmd.getStrappingCount() : -1)
                .strappingMode(cmd != null && cmd.getStrappingMode() != null
                        ? cmd.getStrappingMode().getModeName()
                        : "UNKNOWN")

                .stale(status != null && status.isStale())
                .build();
    }

    /**
     * 將 StatusUpdatedEvent 轉為 WebSocket DTO
     */
    private StrappingStatusUpdatedMessage toWebSocketMessage(StrappingStatusUpdatedEvent event) {
        StrappingDeviceStatus status = event.getDeviceStatus();
        StrappingStatus strappingStatus = status.getStrappingStatus();

        return StrappingStatusUpdatedMessage.builder()
                .timestamp(status.getSnapshotTime())
                .strappingId(status.getStrappingId())

                // Bit 資訊
                .strappingStandby(status.isStrappingStandby())
                .strappingCmdAck(status.isStrappingCmdAck())
                .strappingCompReq(status.isStrappingCompReq())
                .alarm(status.isAlarm())

                // Word 區主狀態
                .deviceStatusCode(strappingStatus != null
                        ? String.format("0x%04X", strappingStatus.toRaw())
                        : "N/A")
                .deviceStatusDesc(strappingStatus != null
                        ? strappingStatus.getWorkingStatusText() + " / " + strappingStatus.getRunningSubStatusText()
                        : "Unknown")

                // Return Code
                .returnCode(String.format("0x%04X", status.getReturnCode()))

                // 是否過期
                .stale(status.isStale())
                .build();
    }


    // =================== Site 狀態相關 ===================

    // 發送單一 Site Command 指令過期事件
    public void publishSiteCommandOverdue(SiteCommandOverdueEvent event) {
        publish(event);
        log.warn("[EVENT] 發送 SiteCommandOverdueEvent, SiteId={}, elapsed={}ms",
                event.getSiteId(), event.getElapsedMillis());
    }

    // 發送單一 Site Status 狀態過期事件
    public void publishSiteStatusOverdue(SiteStatusOverdueEvent event) {
        publish(event);
        log.warn("[EVENT] 發送 SiteStatusOverdueEvent, SiteId={}, elapsed={}ms",
                event.getSiteId(), event.getElapsedMillis());
    }

    // 發送單一 Site 狀態更新事件（含 WebSocket 推播）
    public void publishSiteStatusUpdated(SiteStatusUpdatedEvent event) {
        publish(event);
        SiteStatusUpdatedMessage message = toWebSocketMessage(event);
        webSocketPushService.pushSiteStatus(message);
        //log.debug("[WEBSOCKET] 推播 SiteStatusUpdatedMessage 成功, SiteId={}", event.getSiteId());
    }

    // 發送單一 Site 指令更新事件（但不推播 WebSocket）
    public void publishSiteCommandUpdated(SiteCommandUpdatedEvent event) {
        publish(event);
        log.info("[EVENT] 發送 SiteCommandUpdatedEvent, SiteId={}", event.getSiteId());
    }

    // 批次發送 Site 狀態更新事件（含 WebSocket 推播）
    public void publishSiteStatusUpdatedBatch(List<SiteStatusUpdatedEvent> events) {
        if (events == null || events.isEmpty()) return;

        for (SiteStatusUpdatedEvent event : events) {
            publishSiteStatusUpdated(event);
        }

        List<SiteStatusUpdatedMessage> messageList = events.stream()
                .map(this::toWebSocketMessage)
                .toList();

        SiteStatusBatchMessage batchMessage = SiteStatusBatchMessage.builder()
                .sites(messageList)
                .build();

        webSocketPushService.pushSiteStatusBatch(batchMessage);
        //log.debug("[WEBSOCKET] 推播 SiteStatusBatchMessage, count={}", messageList.size());
    }

    // 批次發送 Site 指令更新事件（含 WebSocket 推播）
    public void publishSiteCommandUpdatedBatch(List<SiteCommandUpdatedEvent> events) {
        if (events == null || events.isEmpty()) return;

        for (SiteCommandUpdatedEvent event : events) {
            publishSiteCommandUpdated(event);
        }

        List<SiteCommandUpdatedMessage> messageList = events.stream()
                .map(this::toWebSocketMessage)
                .toList();

        SiteCommandBatchMessage batchMessage = SiteCommandBatchMessage.builder()
                .commands(messageList)
                .build();

        webSocketPushService.pushSiteCommandStatusBatch(batchMessage);
        //log.debug("[WEBSOCKET] 推播 SiteCommandBatchMessage, count={}", messageList.size());
    }

    private SiteStatusUpdatedMessage toWebSocketMessage(SiteStatusUpdatedEvent event) {
        SiteDeviceStatus status = event.getDeviceStatus();

        return SiteStatusUpdatedMessage.builder()
                .timestamp(status != null && status.getSnapshotTime() != null
                        ? status.getSnapshotTime()
                        : Instant.now())
                .siteId(event.getSiteId())

                .siteStandby(status != null && status.isSiteStandby())
                .productPresent(status != null && status.isProductPresent())
                .removeAccountReq(status != null && status.isRemoveAccountReq())
                .portReportPlc(status != null && status.isPortReportPlc())
                .productId(status != null ? status.getProductId() : "")

                .stale(status != null && status.isStale())
                .build();
    }

    private SiteCommandUpdatedMessage toWebSocketMessage(SiteCommandUpdatedEvent event) {
        SiteCommandStatus status = event.getCommandStatus();

        return SiteCommandUpdatedMessage.builder()
                .timestamp((status != null && status.getSnapshotTime() != null)
                        ? status.getSnapshotTime()
                        : Instant.now())
                .siteId(event.getSiteId())

                .siteReady(status != null && status.isSiteReady())
                .removeAccountAck(status != null && status.isRemoveAccountAck())
                .portReportPc(status != null && status.isPortReportPc())

                .productId(status != null ? status.getProductId() : "")

                .stale(status != null && status.isStale())
                .build();
    }

    // =================== Infrared 狀態相關 ===================

    /**
     * 推播 Infrared 指令過期事件
     */
    public void publishInfraredCommandOverdue(InfraredCommandOverdueEvent event) {
        publish(event);
        log.warn("[EVENT] 發送 InfraredCommandOverdueEvent, InfraredId={}, elapsed={}ms",
                event.getInfraredId(), event.getElapsedMillis());
    }

    /**
     * 推播 Infrared 狀態過期事件
     */
    public void publishInfraredStatusOverdue(InfraredStatusOverdueEvent event) {
        publish(event);
        log.warn("[EVENT] 發送 InfraredStatusOverdueEvent, InfraredId={}, elapsed={}ms",
                event.getInfraredId(), event.getElapsedMillis());
    }

    /**
     * 推播單筆 Infrared 狀態更新（事件 + WebSocket）
     */
    public void publishInfraredStatusUpdated(InfraredStatusUpdatedEvent event) {
        publish(event);
        InfraredStatusUpdatedMessage message = toWebSocketMessage(event);
        webSocketPushService.pushInfraredStatus(message);
        //log.debug("[WEBSOCKET] 推播 InfraredStatusUpdatedMessage 成功, InfraredId={}, state={}",
//                event.getInfraredId(), event.getCurrentState());
    }

    /**
     * 推播單筆 Infrared 指令狀態更新（事件）
     */
    public void publishInfraredCommandUpdated(InfraredCommandUpdatedEvent event) {
        publish(event);
        log.info("[EVENT] 發送 InfraredCommandUpdatedEvent, InfraredId={}", event.getInfraredId());
    }

    /**
     * 推播多筆 Infrared 狀態更新（事件 + 批次 WebSocket）
     */
    public void publishInfraredStatusUpdatedBatch(List<InfraredStatusUpdatedEvent> events) {
        if (events == null || events.isEmpty()) return;

        for (InfraredStatusUpdatedEvent event : events) {
            publishInfraredStatusUpdated(event);
        }

        List<InfraredStatusUpdatedMessage> messageList = events.stream()
                .map(this::toWebSocketMessage)
                .toList();

        InfraredStatusBatchMessage batchMessage = InfraredStatusBatchMessage.builder()
                .infrareds(messageList)
                .build();

        webSocketPushService.pushInfraredStatusBatch(batchMessage);
        //log.debug("[WEBSOCKET] 推播 InfraredStatusBatchMessage, count={}", messageList.size());
    }

    /**
     * 推播多筆 Infrared 指令狀態更新（事件 + 批次 WebSocket）
     */
    public void publishInfraredCommandUpdatedBatch(List<InfraredCommandUpdatedEvent> events) {
        if (events == null || events.isEmpty()) return;

        for (InfraredCommandUpdatedEvent event : events) {
            publishInfraredCommandUpdated(event);
        }

        List<InfraredCommandUpdatedMessage> messageList = events.stream()
                .map(this::toWebSocketMessage)
                .toList();

        InfraredCommandBatchMessage batchMessage = InfraredCommandBatchMessage.builder()
                .commands(messageList)
                .build();

        webSocketPushService.pushInfraredCommandStatusBatch(batchMessage);
        //log.debug("[WEBSOCKET] 推播 InfraredCommandBatchMessage, count={}", messageList.size());
    }

    /**
     * 將 CommandUpdatedEvent 轉為 WebSocket DTO（防 null）
     */
    private InfraredCommandUpdatedMessage toWebSocketMessage(InfraredCommandUpdatedEvent event) {
        var status = event.getCommandStatus();
        var cmd = (status != null) ? status.getCommand() : null;

        return InfraredCommandUpdatedMessage.builder()
                .timestamp((status != null && status.getSnapshotTime() != null)
                        ? status.getSnapshotTime() : Instant.now())
                .infraredId(event.getInfraredId())

                // Bit 區位元
                .infraredReady(status != null && status.isInfraredReady())
                .measureCmdReq(status != null && status.isMeasureCmdReq())
                .measureCompAck(status != null && status.isMeasureCompAck())

                // Word 區指令資訊（紅外線實際只需指令編號與測高參數）
                .infraredNo(cmd != null ? cmd.getInfraredNo() : -1)
                .trayThickness(cmd != null ? cmd.getTrayThickness() : -1) // 若無此欄位可改為 null

                .stale(status != null && status.isStale())
                .build();
    }

    /**
     * 將 StatusUpdatedEvent 轉為 WebSocket DTO
     */
    private InfraredStatusUpdatedMessage toWebSocketMessage(InfraredStatusUpdatedEvent event) {
        InfraredDeviceStatus status = event.getDeviceStatus();
        InfraredStatus state = status.getInfraredStatus();

        return InfraredStatusUpdatedMessage.builder()
                .timestamp(status.getSnapshotTime())
                .infraredId(status.getInfraredId())

                // Bit 資訊
                .infraredStandby(status.isInfraredStandby())
                .measureCmdAck(status.isMeasureCmdAck())
                .measureCompReq(status.isMeasureCompReq())
                .alarm(status.isAlarm())

                // Word 區主狀態
                .deviceStatusCode(state != null
                        ? String.format("0x%04X", state.toRaw())
                        : "N/A")
                .deviceStatusDesc(state != null
                        ? state.getWorkingStatusText() + " / " + state.getRunningStatusText()
                        : "Unknown")

                // Return Code
                .returnCode(String.format("0x%04X", status.getReturnCode()))

                // 是否過期
                .stale(status.isStale())
                .build();
    }

    // =================== Safety 狀態相關 ===================

    /**
     * 推播 Safety 狀態過期事件（資料超時未更新）
     */
    public void publishSafetyStatusOverdue(SafetyStatusOverdueEvent event) {
        publish(event);
        log.warn("[EVENT] 發送 SafetyStatusOverdueEvent, deviceId={}, lastSnapshot={}",
                event.getDeviceId(), event.getLastSnapshotTime());
    }

    /**
     * 批次推送 Safety 單點位變更事件（目前僅發 Spring Event）
     * 若後續加上 WebSocket DTO（例如 SafetyStatusUpdatedMessage、SafetyStatusBatchMessage）
     * 可在此組裝後呼叫 webSocketPushService 對前端推播。
     */
    public void publishSafetyStatusUpdatedBatch(List<SafetyStatusUpdatedEvent> events) {
        if (events == null || events.isEmpty()) return;

        // 逐筆發 Spring Event
        for (SafetyStatusUpdatedEvent event : events) {
            publish(event);
        }

        // TODO: 若日後有 WebSocket DTO：
        // List<SafetyStatusUpdatedMessage> messageList = events.stream()
        //         .map(this::toWebSocketMessage)
        //         .toList();
        // SafetyStatusBatchMessage batchMessage = SafetyStatusBatchMessage.builder()
        //         .points(messageList)
        //         .build();
        // webSocketPushService.pushSafetyStatusBatch(batchMessage);

        //log.debug("[EVENT] 批次發送 SafetyStatusUpdatedEvent, count={}", events.size());
    }

}