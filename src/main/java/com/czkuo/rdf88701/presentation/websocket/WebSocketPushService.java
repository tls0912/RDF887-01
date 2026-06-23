package com.czkuo.rdf88701.presentation.websocket;

import com.czkuo.rdf88701.presentation.websocket.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * WebSocket 推播服務
 * - 負責將 Gripper 等 PLC 狀態推播給訂閱的前端 Client
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketPushService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 推播單筆 Crane 控制指令狀態給前端
     */
    public void pushCraneCommandStatus(CraneCommandUpdatedMessage message) {
        try {
            messagingTemplate.convertAndSend("/topic/crane/command", message);
            //log.debug("[WEBSOCKET] 推送 CraneCommandUpdatedMessage 成功, craneId={}", message.getCraneId());
        } catch (Exception ex) {
            log.error("[WEBSOCKET] 推送 CraneCommandUpdatedMessage 失敗: {}", ex.getMessage(), ex);
        }
    }

    /**
     * 批量推播多筆 Crane 控制指令狀態給前端
     */
    public void pushCraneCommandStatusBatch(CraneCommandBatchMessage batchMessage) {
        try {
            messagingTemplate.convertAndSend("/topic/crane/command/batch", batchMessage);
            //log.debug("[WEBSOCKET] 推送 CraneCommandBatchMessage, count={}", batchMessage.getCommands().size());
        } catch (Exception ex) {
            log.error("[WEBSOCKET] 推送 CraneCommandBatchMessage 失敗：{}", ex.getMessage(), ex);
        }
    }

    /**
     * 推播單筆 Crane 狀態給前端
     */
    public void pushCraneStatus(CraneStatusUpdatedMessage message) {
        try {
            messagingTemplate.convertAndSend("/topic/crane/status", message);
            //log.debug("[WEBSOCKET] 推送 CraneStatusUpdatedMessage 成功, craneId={}, state={}",
//                    message.getCraneId(), message.getStateMachineState());
        } catch (Exception ex) {
            log.error("[WEBSOCKET] 推送 CraneStatusUpdatedMessage 失敗: {}", ex.getMessage(), ex);
        }
    }

    /**
     * 批量推播多筆 Crane 狀態給前端
     */
    public void pushCraneStatusBatch(CraneStatusBatchMessage batchMessage) {
        try {
            messagingTemplate.convertAndSend("/topic/crane/status/batch", batchMessage);
            //log.debug("[WEBSOCKET] 推送 CraneStatusBatchMessage, count={}", batchMessage.getCranes().size());
        } catch (Exception ex) {
            log.error("[WEBSOCKET] 推送 CraneStatusBatchMessage 失敗：{}", ex.getMessage(), ex);
        }
    }

    /**
     * 推送 Gripper 狀態更新到前端
     */
    public void pushGripperStatus(GripperStatusUpdatedMessage message) {
        try {
            messagingTemplate.convertAndSend("/topic/gripper/status", message);
            //log.debug("[WEBSOCKET] 推送 GripperStatusUpdatedMessage 成功, gripperId={}, state={}",
//                    message.getGripperId(), message.getStateMachineState());
        } catch (Exception ex) {
            log.error("[WEBSOCKET] 推送 GripperStatusUpdatedMessage 失敗: {}", ex.getMessage(), ex);
        }
    }

    /**
     * 推送批量 Gripper 狀態更新到前端
     */
    public void pushGripperStatusBatch(GripperStatusBatchMessage message) {
        try {
            messagingTemplate.convertAndSend("/topic/gripper/status/batch", message);
            //log.debug("[WEBSOCKET] 推送 GripperStatusBatchMessage, count={}", message.getGrippers().size());
        } catch (Exception ex) {
            log.error("[WEBSOCKET] 推送 GripperStatusBatchMessage 失敗：{}", ex.getMessage(), ex);
        }
    }

    /**
     * 批量推播多筆 Gripper 指令狀態（PC → PLC）給前端
     */
    public void pushGripperCommandStatusBatch(GripperCommandBatchMessage message) {
        try {
            messagingTemplate.convertAndSend("/topic/gripper/command/batch", message);
            //log.debug("[WEBSOCKET] 推送 GripperCommandBatchMessage 成功, count={}", message.getCommands().size());
        } catch (Exception ex) {
            log.error("[WEBSOCKET] 推送 GripperCommandBatchMessage 失敗: {}", ex.getMessage(), ex);
        }
    }

    /**
     * 推播單筆 WorkingBeam 狀態給前端
     */
    public void pushWorkingBeamStatus(WorkingBeamStatusUpdatedMessage message) {
        try {
            messagingTemplate.convertAndSend("/topic/working-beam/status", message);
            //log.debug("[WEBSOCKET] 推送 WorkingBeamStatusUpdatedMessage 成功, workingBeamId={}, state={}",
//                    message.getWorkingBeamId(), message.getCurrentState());
        } catch (Exception ex) {
            log.error("[WEBSOCKET] 推送 WorkingBeamStatusUpdatedMessage 失敗: {}", ex.getMessage(), ex);
        }
    }

    /**
     * 批量推播多筆 WorkingBeam 狀態給前端
     */
    public void pushWorkingBeamStatusBatch(WorkingBeamStatusBatchMessage message) {
        try {
            messagingTemplate.convertAndSend("/topic/working-beam/status/batch", message);
            //log.debug("[WEBSOCKET] 推送 WorkingBeamStatusBatchMessage, count={}", message.getWorkingBeams().size());
        } catch (Exception ex) {
            log.error("[WEBSOCKET] 推送 WorkingBeamStatusBatchMessage 失敗：{}", ex.getMessage(), ex);
        }
    }

    /**
     * 推播單筆 WorkingBeam 指令狀態（PC → PLC）給前端
     */
    public void pushWorkingBeamCommandStatus(WorkingBeamCommandUpdatedMessage message) {
        try {
            messagingTemplate.convertAndSend("/topic/working-beam/command", message);
            //log.debug("[WEBSOCKET] 推送 WorkingBeamCommandUpdatedMessage 成功, workingBeamId={}, transferNo={}",
//                    message.getWorkingBeamId(), message.getTransferNo());
        } catch (Exception ex) {
            log.error("[WEBSOCKET] 推送 WorkingBeamCommandUpdatedMessage 失敗: {}", ex.getMessage(), ex);
        }
    }

    /**
     * 批量推播多筆 WorkingBeam 指令狀態（PC → PLC）給前端
     */
    public void pushWorkingBeamCommandStatusBatch(WorkingBeamCommandBatchMessage message) {
        try {
            messagingTemplate.convertAndSend("/topic/working-beam/command/batch", message);
            //log.debug("[WEBSOCKET] 推送 WorkingBeamCommandBatchMessage, count={}", message.getCommands().size());
        } catch (Exception ex) {
            log.error("[WEBSOCKET] 推送 WorkingBeamCommandBatchMessage 失敗: {}", ex.getMessage(), ex);
        }
    }

    /**
     * 推播單筆 Strapping 裝置狀態（PLC → PC）給前端
     */
    public void pushStrappingStatus(StrappingStatusUpdatedMessage message) {
        try {
            messagingTemplate.convertAndSend("/topic/strapping/status", message);
            //log.debug("[WEBSOCKET] 推送 StrappingStatusUpdatedMessage 成功, strappingId={}", message.getStrappingId());
        } catch (Exception ex) {
            log.error("[WEBSOCKET] 推送 StrappingStatusUpdatedMessage 失敗: {}", ex.getMessage(), ex);
        }
    }

    /**
     * 批量推播多筆 Strapping 裝置狀態（PLC → PC）給前端
     */
    public void pushStrappingStatusBatch(StrappingStatusBatchMessage message) {
        try {
            messagingTemplate.convertAndSend("/topic/strapping/status/batch", message);
            //log.debug("[WEBSOCKET] 推送 StrappingStatusBatchMessage 成功, count={}", message.getStrappings().size());
        } catch (Exception ex) {
            log.error("[WEBSOCKET] 推送 StrappingStatusBatchMessage 失敗: {}", ex.getMessage(), ex);
        }
    }

    /**
     * 推播單筆 Strapping 指令狀態（PC → PLC）給前端
     */
    public void pushStrappingCommandStatus(StrappingCommandUpdatedMessage message) {
        try {
            messagingTemplate.convertAndSend("/topic/strapping/command", message);
            //log.debug("[WEBSOCKET] 推送 StrappingCommandUpdatedMessage 成功, strappingId={}", message.getStrappingId());
        } catch (Exception ex) {
            log.error("[WEBSOCKET] 推送 StrappingCommandUpdatedMessage 失敗: {}", ex.getMessage(), ex);
        }
    }

    /**
     * 批量推播多筆 Strapping 指令狀態（PC → PLC）給前端
     */
    public void pushStrappingCommandStatusBatch(StrappingCommandBatchMessage message) {
        try {
            messagingTemplate.convertAndSend("/topic/strapping/command/batch", message);
            //log.debug("[WEBSOCKET] 推送 StrappingCommandBatchMessage 成功, count={}", message.getCommands().size());
        } catch (Exception ex) {
            log.error("[WEBSOCKET] 推送 StrappingCommandBatchMessage 失敗: {}", ex.getMessage(), ex);
        }
    }

    /**
     * 推送儲位狀態更新（isOccupied / isReserved / isLocked 等）
     */
    public void pushLocationPointStatus(LocationPointStatusMessage message) {
        try {
            messagingTemplate.convertAndSend("/topic/location/point/status", message);
            //log.debug("[WEBSOCKET] 推送 LocationPointStatusMessage 成功, locationPointId={}, occupied={}, reserved={}",
//                    message.getLocationPointId(), message.isOccupied(), message.isReserved());
        } catch (Exception ex) {
            log.error("[WEBSOCKET] 推送 LocationPointStatusMessage 失敗: {}", ex.getMessage(), ex);
        }
    }

    /**
     * 推送容器當前位置（location_tracking 資料）
     */
    public void pushLocationTrackingStatus(LocationTrackingStatusMessage message) {
        try {
            messagingTemplate.convertAndSend("/topic/location/tracking/status", message);
            //log.debug("[WEBSOCKET] 推送 LocationTrackingStatusMessage 成功, containerMainId={}, locationPointId={}",
//                    message.getContainerMainId(), message.getLocationPointId());
        } catch (Exception ex) {
            log.error("[WEBSOCKET] 推送 LocationTrackingStatusMessage 失敗: {}", ex.getMessage(), ex);
        }
    }

    /**
     * 推播單筆 Transfer 指令狀態（PC → PLC）給前端
     */
    public void pushTransferCommandStatus(TransferCommandUpdatedMessage message) {
        try {
            messagingTemplate.convertAndSend("/topic/transfer/command", message);
            //log.debug("[WEBSOCKET] 推送 TransferCommandUpdatedMessage 成功, transferId={}, transferNo={}",
//                    message.getTransferId(), message.getTransferNo());
        } catch (Exception ex) {
            log.error("[WEBSOCKET] 推送 TransferCommandUpdatedMessage 失敗: {}", ex.getMessage(), ex);
        }
    }

    /**
     * 批量推播多筆 Transfer 指令狀態（PC → PLC）給前端
     */
    public void pushTransferCommandStatusBatch(TransferCommandBatchMessage message) {
        try {
            messagingTemplate.convertAndSend("/topic/transfer/command/batch", message);
            //log.debug("[WEBSOCKET] 推送 TransferCommandBatchMessage 成功, count={}", message.getCommands().size());
        } catch (Exception ex) {
            log.error("[WEBSOCKET] 推送 TransferCommandBatchMessage 失敗: {}", ex.getMessage(), ex);
        }
    }

    /**
     * 推播單筆 Transfer 裝置狀態（PLC → PC）給前端
     */
    public void pushTransferStatus(TransferStatusUpdatedMessage message) {
        try {
            messagingTemplate.convertAndSend("/topic/transfer/status", message);
            //log.debug("[WEBSOCKET] 推送 TransferStatusUpdatedMessage 成功, transferId={}, state={}",
//                    message.getTransferId(), message.getCurrentState());
        } catch (Exception ex) {
            log.error("[WEBSOCKET] 推送 TransferStatusUpdatedMessage 失敗: {}", ex.getMessage(), ex);
        }
    }

    /**
     * 批量推播多筆 Transfer 裝置狀態（PLC → PC）給前端
     */
    public void pushTransferStatusBatch(TransferStatusBatchMessage message) {
        try {
            messagingTemplate.convertAndSend("/topic/transfer/status/batch", message);
            //log.debug("[WEBSOCKET] 推送 TransferStatusBatchMessage 成功, count={}", message.getTransfers().size());
        } catch (Exception ex) {
            log.error("[WEBSOCKET] 推送 TransferStatusBatchMessage 失敗: {}", ex.getMessage(), ex);
        }
    }

    /**
     * 推播單筆 Site 裝置狀態（PLC → PC）給前端
     */
    public void pushSiteStatus(SiteStatusUpdatedMessage message) {
        try {
            messagingTemplate.convertAndSend("/topic/site/status", message);
            //log.debug("[WEBSOCKET] 推送 SiteStatusUpdatedMessage 成功, siteId={}", message.getSiteId());
        } catch (Exception ex) {
            log.error("[WEBSOCKET] 推送 SiteStatusUpdatedMessage 失敗: {}", ex.getMessage(), ex);
        }
    }

    /**
     * 批量推播多筆 Site 裝置狀態（PLC → PC）給前端
     */
    public void pushSiteStatusBatch(SiteStatusBatchMessage message) {
        try {
            messagingTemplate.convertAndSend("/topic/site/status/batch", message);
            //log.debug("[WEBSOCKET] 推送 SiteStatusBatchMessage 成功, count={}", message.getSites().size());
        } catch (Exception ex) {
            log.error("[WEBSOCKET] 推送 SiteStatusBatchMessage 失敗: {}", ex.getMessage(), ex);
        }
    }

    /**
     * 推播單筆 Site 指令狀態（PC → PLC）給前端
     */
    public void pushSiteCommandStatus(SiteCommandUpdatedMessage message) {
        try {
            messagingTemplate.convertAndSend("/topic/site/command", message);
            //log.debug("[WEBSOCKET] 推送 SiteCommandUpdatedMessage 成功, siteId={}", message.getSiteId());
        } catch (Exception ex) {
            log.error("[WEBSOCKET] 推送 SiteCommandUpdatedMessage 失敗: {}", ex.getMessage(), ex);
        }
    }

    /**
     * 批量推播多筆 Site 指令狀態（PC → PLC）給前端
     */
    public void pushSiteCommandStatusBatch(SiteCommandBatchMessage message) {
        try {
            messagingTemplate.convertAndSend("/topic/site/command/batch", message);
            //log.debug("[WEBSOCKET] 推送 SiteCommandBatchMessage 成功, count={}", message.getCommands().size());
        } catch (Exception ex) {
            log.error("[WEBSOCKET] 推送 SiteCommandBatchMessage 失敗: {}", ex.getMessage(), ex);
        }
    }

    // =================== Infrared 狀態相關 ===================

    /**
     * 推播單筆 Infrared 裝置狀態（PLC → PC）給前端
     */
    public void pushInfraredStatus(InfraredStatusUpdatedMessage message) {
        try {
            messagingTemplate.convertAndSend("/topic/infrared/status", message);
            //log.debug("[WEBSOCKET] 推送 InfraredStatusUpdatedMessage 成功, infraredId={}", message.getInfraredId());
        } catch (Exception ex) {
            log.error("[WEBSOCKET] 推送 InfraredStatusUpdatedMessage 失敗: {}", ex.getMessage(), ex);
        }
    }

    /**
     * 批量推播多筆 Infrared 裝置狀態（PLC → PC）給前端
     */
    public void pushInfraredStatusBatch(InfraredStatusBatchMessage message) {
        try {
            messagingTemplate.convertAndSend("/topic/infrared/status/batch", message);
            //log.debug("[WEBSOCKET] 推送 InfraredStatusBatchMessage 成功, count={}", message.getInfrareds().size());
        } catch (Exception ex) {
            log.error("[WEBSOCKET] 推送 InfraredStatusBatchMessage 失敗: {}", ex.getMessage(), ex);
        }
    }

    /**
     * 推播單筆 Infrared 指令狀態（PC → PLC）給前端
     */
    public void pushInfraredCommandStatus(InfraredCommandUpdatedMessage message) {
        try {
            messagingTemplate.convertAndSend("/topic/infrared/command", message);
            //log.debug("[WEBSOCKET] 推送 InfraredCommandUpdatedMessage 成功, infraredId={}", message.getInfraredId());
        } catch (Exception ex) {
            log.error("[WEBSOCKET] 推送 InfraredCommandUpdatedMessage 失敗: {}", ex.getMessage(), ex);
        }
    }

    /**
     * 批量推播多筆 Infrared 指令狀態（PC → PLC）給前端
     */
    public void pushInfraredCommandStatusBatch(InfraredCommandBatchMessage message) {
        try {
            messagingTemplate.convertAndSend("/topic/infrared/command/batch", message);
            //log.debug("[WEBSOCKET] 推送 InfraredCommandBatchMessage 成功, count={}", message.getCommands().size());
        } catch (Exception ex) {
            log.error("[WEBSOCKET] 推送 InfraredCommandBatchMessage 失敗: {}", ex.getMessage(), ex);
        }
    }

}
