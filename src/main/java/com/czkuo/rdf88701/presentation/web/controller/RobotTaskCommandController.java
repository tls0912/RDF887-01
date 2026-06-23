package com.czkuo.rdf88701.presentation.web.controller;

import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttCommandService;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.dto.MqttSendResult;
import com.czkuo.rdf88701.common.dto.mqtt.ack.R007AckPayload;
import com.czkuo.rdf88701.common.dto.mqtt.ack.R008AckPayload;
import com.czkuo.rdf88701.common.dto.mqtt.ack.R029AckPayload;
import com.czkuo.rdf88701.common.dto.mqtt.ack.R031AckPayload;
import com.czkuo.rdf88701.common.dto.mqtt.command.R018CommandPayload;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.domain.repository.*;
import com.czkuo.rdf88701.infra.entity.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/missions")
@RequiredArgsConstructor
public class RobotTaskCommandController {

    private final RobotR007TaskRepository robotR007TaskRepository;
    private final RobotR008TaskRepository robotR008TaskRepository;
    private final RobotR029TaskRepository robotR029TaskRepository;
    private final RobotR031TaskRepository robotR031TaskRepository;
    private final RobotInR029LotRepository r029LotRepository;

    private final MqttMessageEventPublisher publisher;
    private final MqttMessageLogService logService;
    private final ObjectMapper objectMapper;

    private final MqttCommandService mqttCommandService;

    @Value("${app.external.ase-system:ase}")
    private String aseSystem;

    @PostMapping("/{cmd}/{id}/cancel")
    @Transactional
    public void cancelTask(
            @PathVariable String cmd,
            @PathVariable Long id,
            @RequestBody(required = false) CancelRequest body) {

        String reason = (body == null || body.reason == null || body.reason.isBlank())
                ? "Manual cancel from WPF"
                : body.reason;

        switch (cmd.toUpperCase()) {
            case "R007" -> cancelR007(id, reason);
            case "R008" -> cancelR008(id, reason);
            case "R029" -> cancelR029(id, reason);
            case "R031" -> cancelR031(id, reason);
            default -> throw new IllegalArgumentException("Unknown CMD: " + cmd);
        }
    }

    private void cancelR007(Long id, String reason) {
        RobotR007Task t = robotR007TaskRepository.findById(id).orElse(null);
        if (t == null) return;

        // if (isTerminated(t.getExternalLastResult())) {
        //     log.info("[R007-CANCEL] id={} 已終態({})，略過", id, t.getExternalLastResult());
        //     return;
        // }

        // 1) 先更新 DB
        t.setExternalLastResult("CANCEL");
        t.setExternalLastTime(LocalDateTime.now());
        t.setCancelReason(reason);
        t.setInternalState("CANCELLED");
        robotR007TaskRepository.update(t);

        // 2) 再把 DB 的內容組成 MQTT ACK 丟給 ASE
        try {
            sendR007CancelToAse(t, reason);
        } catch (Exception e) {
            log.error("[R007-CANCEL→ASE] 發送 CANCEL ACK 失敗：id={}, tid={}, err={}",
                    id, t.getTid(), e.getMessage(), e);
        }
    }


    private void cancelR008(Long id, String reason) {
        RobotR008Task t = robotR008TaskRepository.findById(id).orElse(null);
        if (t == null) return;

        try {
            sendR008CancelToSEEC(t, reason);
        } catch (Exception e) {
            log.error("[R008-CANCEL→SEEC] 發送 CANCEL ACK 失敗：id={}, tid={}, err={}",
                    id, t.getTid(), e.getMessage(), e);
        }
    }

    private void cancelR029(Long id, String reason) {
        RobotR029Task t = robotR029TaskRepository.findById(id).orElse(null);
        if (t == null) return;
        if (isTerminated(t.getExternalLastResult())) {
            log.info("[R029-CANCEL] id={} 已終態({})，略過", id, t.getExternalLastResult());
            return;
        }
        t.setExternalLastResult("CANCEL");
        t.setExternalLastTime(LocalDateTime.now());
        t.setFailReason(reason);           // 你目前是用 failReason 當取消原因，我就沿用
        t.setInternalState("CANCELLED");
        t.setActiveLane(null);
        robotR029TaskRepository.update(t);

        try {
            sendR029CancelToAse(t, reason);
        } catch (Exception e) {
            log.error("[R029-CANCEL→ASE] 發送 CANCEL ACK 失敗：id={}, tid={}, err={}",
                    id, t.getTid(), e.getMessage(), e);
        }
    }

    private void cancelR031(Long id, String reason) {
        RobotR031Task t = robotR031TaskRepository.findById(id).orElse(null);
        if (t == null) return;
        if (isTerminated(t.getExternalLastResult())) {
            log.info("[R031-CANCEL] id={} 已終態({})，略過", id, t.getExternalLastResult());
            return;
        }
        t.setExternalLastResult("CANCEL");
        t.setExternalLastTime(LocalDateTime.now());
        t.setInternalState("CANCELLED");
        // 如需保留 reason，可考慮加欄位或放 rawMessageJson 裡
        robotR031TaskRepository.update(t);

        try {
            sendR031CancelToAse(t, reason);
        } catch (Exception e) {
            log.error("[R031-CANCEL→ASE] 發送 CANCEL ACK 失敗：id={}, tid={}, err={}",
                    id, t.getTid(), e.getMessage(), e);
        }
    }

    private boolean isTerminated(String externalLastResult) {
        if (externalLastResult == null || externalLastResult.isBlank()) return false;
        String r = externalLastResult.toUpperCase();
        return switch (r) {
            case "END", "FAIL", "NG", "CANCEL", "DONE", "SUCCESS" -> true;
            default -> false;
        };
    }

    private void sendR007CancelToAse(RobotR007Task t, String reason) throws Exception {
        if (t.getTid() == null) {
            log.warn("[R007-CANCEL→ASE] 任務缺少 TID，略過發送：id={}", t.getId());
            return;
        }

        R007AckPayload ack = new R007AckPayload();
        ack.setCmd("ROBOT");
        ack.setCmdId("R007");
        ack.setTid(t.getTid());
        ack.setIdDesc("ROBOT_MOVE_SCH_TO_EQP");

        R007AckPayload.Message m = new R007AckPayload.Message();
        m.setLotId(t.getLotId());
        m.setCarrierId(t.getCarrierId());
        m.setWipName(t.getWipName());
        m.setDestLoc(t.getDestLoc());
        m.setEqpPort(t.getEqpPort());
        m.setTrayHigh(t.getTrayHigh());
        m.setTrayType(t.getTrayType());
        m.setTrayNum(t.getTrayNum());
        m.setDeviceName(t.getDeviceName());
        m.setMovePriority(t.getMovePriority());
        m.setMissionTrip(t.getMissionTrip());
        m.setOdo(t.getOdo());
        m.setAmrSpeed(t.getAmrSpeed());
        m.setAmrRobotSpeed(t.getAmrRobotSpeed());
        m.setPpkgBodySize(t.getPpkgBodySize());

        ack.setMessage(m);

        ack.setResult("CANCEL");
        ack.setResultMessage(reason);

        var jsonNode = objectMapper.valueToTree(ack);
        logService.recordReturningId(
                "ack/r007/manual-cancel",
                logService.getLocalSystem(),   // sender
                aseSystem,                     // receiver
                jsonNode,
                MqttMessageType.ACK
        );

        publisher.publish(
                aseSystem,
                objectMapper.writeValueAsString(ack),
                MqttMessageType.ACK,
                ack.getTid(),
                ack.getCmdId()
        );

        log.info("[R007-CANCEL→ASE] 已送 R007 CANCEL：id={}, tid={}, reason={}",
                t.getId(), ack.getTid(), reason);
    }

    private void sendR007CancelToSEEC(RobotR007Task t, String reason) throws Exception {
        if (t.getTid() == null) {
            log.warn("[R007-CANCEL→SEEC] 任務缺少 TID，略過發送：id={}", t.getId());
            return;
        }

        String cmdTid = "R007_" + t.getTid();
        MqttSendResult result = mqttCommandService.sendR018("SEEC", cmdTid);

        log.info("[R018-CANCEL→SEEC] 已送 R018 CANCEL：cmd=R007, id={}, tid={}, reason={}",
                t.getId(), result.getTid(), reason);
    }

    private void sendR008CancelToAse(RobotR008Task t, String reason) throws Exception {
        if (t.getTid() == null) {
            log.warn("[R008-CANCEL→ASE] 任務缺少 TID，略過發送：id={}", t.getId());
            return;
        }

        R008AckPayload ack = new R008AckPayload();
        ack.setCmd("ROBOT");
        ack.setCmdId("R008");
        ack.setTid(t.getTid());
        ack.setIdDesc("ROBOT_MOVE_SCH_TO_WIP");

        R008AckPayload.Message m = new R008AckPayload.Message();
        m.setLotId(t.getLotId());
        m.setCarrierId(t.getCarrierId());
        m.setWipName(t.getWipName());
        m.setDestLoc(t.getDestLoc());
        m.setEqpPort(t.getEqpPort());
        m.setTrayHigh(t.getTrayHigh());
        m.setTrayType(t.getTrayType());
        m.setBinType(t.getBinType());
        m.setTrayNum(t.getTrayNum());
        m.setDeviceName(t.getDeviceName());
        m.setMovePriority(t.getMovePriority());
        m.setMissionTrip(t.getMissionTrip());
        m.setOdo(t.getOdo());
        m.setAmrSpeed(t.getAmrSpeed());
        m.setAmrRobotSpeed(t.getAmrRobotSpeed());
        m.setPpkgBodySize(t.getPpkgBodySize());

        ack.setMessage(m);
        ack.setResult("CANCEL");
        ack.setResultMessage(reason);

        var jsonNode = objectMapper.valueToTree(ack);
        logService.recordReturningId(
                "ack/r008/manual-cancel",
                logService.getLocalSystem(),
                aseSystem,
                jsonNode,
                MqttMessageType.ACK
        );

        publisher.publish(
                aseSystem,
                objectMapper.writeValueAsString(ack),
                MqttMessageType.ACK,
                ack.getTid(),
                ack.getCmdId()
        );

        log.info("[R008-CANCEL→ASE] 已送 R008 CANCEL：id={}, tid={}, reason={}",
                t.getId(), ack.getTid(), reason);
    }

    private void sendR008CancelToSEEC(RobotR008Task t, String reason) throws Exception {
        if (t.getTid() == null) {
            log.warn("[R008-CANCEL→SEEC] 任務缺少 TID，略過發送：id={}", t.getId());
            return;
        }

        String cmdTid = "R008_" + t.getTid();
        MqttSendResult result = mqttCommandService.sendR018("SEEC", cmdTid);

        log.info("[R018-CANCEL→SEEC] 已送 R018 CANCEL：cmd=R008, id={}, tid={}, reason={}",
                t.getId(), result.getTid(), reason);
    }

    private void sendR029CancelToAse(RobotR029Task task, String reason) throws Exception {
        if (task.getTid() == null || task.getLogId() == null) {
            log.warn("[R029-CANCEL→ASE] 任務缺少 TID 或 logId，略過：id={}", task.getId());
            return;
        }

        // 1) 取 base carrier list（跟 END 用法一樣）
        var baseIds = r029LotRepository.findCarrierIdsByLogId(task.getLogId());
        var carrierIds = (baseIds == null ? List.<String>of() :
                baseIds.stream()
                        .filter(s -> s != null && !s.isBlank())
                        .distinct()
                        .toList());

        R029AckPayload ack = new R029AckPayload();
        ack.setCmd("ROBOT");
        ack.setCmdId("R029");
        ack.setTid(task.getTid());
        ack.setIdDesc("MOVE_LOTS_TO_DISMANTLE_AND_TIE");

        R029AckPayload.Message msg = new R029AckPayload.Message();
        msg.setCarrierList(toCarrierInfoList(carrierIds));
        msg.setCount(task.getPiecePerLot() == null ? null : String.valueOf(task.getPiecePerLot()));
        msg.setTrayType(task.getTrayType());
        msg.setTrayDesc(task.getTrayDesc());
        ack.setMessage(msg);

        ack.setResult("CANCEL");
        ack.setResultMessage(reason);

        var json = objectMapper.valueToTree(ack);
        logService.recordReturningId(
                "ack/r029/manual-cancel",
                logService.getLocalSystem(),
                aseSystem,
                json,
                MqttMessageType.ACK
        );

        publisher.publish(
                aseSystem,
                objectMapper.writeValueAsString(ack),
                MqttMessageType.ACK,
                ack.getTid(),
                ack.getCmdId()
        );

        log.info("[R029-CANCEL→ASE] 已送 R029 CANCEL：taskId={}, tid={}, carriers={}, reason={}",
                task.getId(), ack.getTid(), carrierIds, reason);
    }

    private static List<R029AckPayload.CarrierInfo> toCarrierInfoList(List<String> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<R029AckPayload.CarrierInfo> list = new java.util.ArrayList<>();
        for (String id : ids) {
            if (id == null || id.isBlank()) continue;
            R029AckPayload.CarrierInfo ci = new R029AckPayload.CarrierInfo();
            ci.setCarrierId(id.trim());
            list.add(ci);
        }
        return list;
    }

    private void sendR031CancelToAse(RobotR031Task t, String reason) throws Exception {
        if (t.getTid() == null) {
            log.warn("[R031-CANCEL→ASE] 任務缺少 TID，略過：id={}", t.getId());
            return;
        }

        R031AckPayload ack = new R031AckPayload();
        ack.setCmd("ROBOT");
        ack.setCmdId("R031");
        ack.setTid(t.getTid());
        ack.setIdDesc("STK_MOVE_SCH_TO_MANUAL_PORT");

        R031AckPayload.Message m = new R031AckPayload.Message();
        m.setLotId(t.getLotId());
        m.setCarrierId(t.getCarrierId());
        // 如果有記 manualPort，就先回那個；沒有就用原本的 wipName
        m.setWipName(t.getManualPort() != null ? t.getManualPort() : t.getWipName());
        ack.setMessage(m);

        ack.setResult("CANCEL");
        ack.setResultMessage(reason);

        var jsonNode = objectMapper.valueToTree(ack);
        logService.recordReturningId(
                "ack/r031/manual-cancel",
                logService.getLocalSystem(),
                aseSystem,
                jsonNode,
                MqttMessageType.ACK
        );

        publisher.publish(
                aseSystem,
                objectMapper.writeValueAsString(ack),
                MqttMessageType.ACK,
                ack.getTid(),
                ack.getCmdId()
        );

        log.info("[R031-CANCEL→ASE] 已送 R031 CANCEL：id={}, tid={}, reason={}",
                t.getId(), ack.getTid(), reason);
    }

    public record CancelRequest(String reason) {}
}
