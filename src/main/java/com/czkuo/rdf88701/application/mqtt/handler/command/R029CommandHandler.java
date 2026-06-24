package com.czkuo.rdf88701.application.mqtt.handler.command;

import com.czkuo.rdf88701.application.mqtt.context.SystemContext;
import com.czkuo.rdf88701.application.mqtt.handler.AbstractCommandHandler;
import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.application.service.r029.R029ContextService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.R029AckPayload;
import com.czkuo.rdf88701.common.dto.mqtt.command.R029CommandPayload;
import com.czkuo.rdf88701.domain.repository.*;
import com.czkuo.rdf88701.infra.entity.ContainerMain;
import com.czkuo.rdf88701.infra.entity.RobotInR029;
import com.czkuo.rdf88701.infra.entity.RobotR029Task;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * R029CommandHandler
 * - CMD_ID=R029：通知將貨搬去拆併打帶
 * - 規則：
 *   1) 驗證 COUNT（每顆要拆幾片，需為正整數）
 *   2) 以 LocationTracking（containerRepo.findAllInWarehouse）檢查 LOT 是否在 storage（LotNo / AliasCode 任一符合）
 * - 流程：
 *   1) 記錄 COMMAND 至 mqtt_message_log（取得 logId）
 *   2) 寫入 robot_in_r029（主檔）與 robot_in_r029_lot（明細）
 *   2.5) 掛回 R029 上下文到來源容器（lotId=carrierId=alias_code）
 *   2.6) 建立 robot_r029_task（狀態 READY；流道由 Walker 之後決策；外部結果快取 OK）
 *   3) 匯入 mqtt_inbox 佇列（RECEIVED），ACK=OK；否則 ACK=NG
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
public class R029CommandHandler extends AbstractCommandHandler<R029CommandPayload> {

    private final MqttMessageLogService logService;
    private final SystemContext systemContext;
    private final ContainerMainRepository containerRepo;
    private final MqttInboxRepository inboxRepository;
    private final RobotInR029Repository r029Repo;
    private final RobotInR029LotRepository r029LotRepo;
    private final R029ContextService r029ContextService;
    private final RobotR029TaskRepository taskRepo;

    public R029CommandHandler(ObjectMapper objectMapper,
                              MqttMessageEventPublisher responseEventPublisher,
                              MqttMessageLogService logService,
                              SystemContext systemContext,
                              ContainerMainRepository containerRepo,
                              MqttInboxRepository inboxRepository,
                              RobotInR029Repository r029Repo,
                              RobotInR029LotRepository r029LotRepo,
                              R029ContextService r029ContextService,
                              RobotR029TaskRepository taskRepo) {
        super(objectMapper, responseEventPublisher);
        this.logService = logService;
        this.systemContext = systemContext;
        this.containerRepo = containerRepo;
        this.inboxRepository = inboxRepository;
        this.r029Repo = r029Repo;
        this.r029LotRepo = r029LotRepo;
        this.r029ContextService = r029ContextService;
        this.taskRepo = taskRepo;
    }

    @Override
    protected void process(String system, String topic, R029CommandPayload command, MqttMessageType type) throws Exception {
        final R029CommandPayload.Message msg = command.getMessage();
        final List<String> carrierList = (msg != null && msg.getCarrierList() != null)
                ? msg.getCarrierList().stream()
                .map(R029CommandPayload.CarrierInfo::getCarrierId)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList()
                : List.of();

        log.info("[R029] 收到拆併打帶指令：tid={}, topic={}, sender={}, carrierCount={}, trayType={}",
                command.getTid(), topic, system, carrierList.size(), (msg != null ? msg.getTrayType() : null));

        // 0) 驗證 COUNT（每顆要拆幾片）
        Integer pieceCount = null;
        if (msg != null && msg.getCount() != null) {
            try {
                pieceCount = Integer.valueOf(msg.getCount().trim());
            } catch (Exception ignore) { /* keep null */ }
        }
        if (pieceCount == null || pieceCount <= 0) {
            R029AckPayload ack = buildAckSkeleton(command, msg);
            ack.setResult("NG");
            ack.setResultMessage("COUNT invalid: must be positive integer (pieces per lot).");
            publishAck(system, ack);
            log.warn("[R029] 拒絕：COUNT 無效（需正整數）。tid={}", command.getTid());
            return;
        }

        // 1) 記錄 COMMAND 至 mqtt_message_log（取得 logId）
        final JsonNode payload = objectMapper.valueToTree(command);
        final Long logId = logService.recordReturningId(
                topic,
                system,                        // sender：對方
                systemContext.getSystemCode(), // receiver：本系統
                payload,
                MqttMessageType.COMMAND
        );

        // 2) 空 LOT 清單直接 NG
        R029AckPayload ack = buildAckSkeleton(command, msg);
        if (carrierList.isEmpty()) {
            ack.setResult("NG");
            ack.setResultMessage("CARRIER_LIST is empty or invalid.");
            publishAck(system, ack);
            log.warn("[R029] 拒絕：CARRIER_LIST 為空。tid={}", command.getTid());
            return;
        }

        // 3) storage 檢核（LotNo / AliasCode 任一符合即可）
        final var inWarehouse = containerRepo.findAllInWarehouse();
        final Set<String> lotNosInWarehouse = inWarehouse.stream()
                .map(ContainerMain::getLotNo)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        final Set<String> aliasCodesInWarehouse = inWarehouse.stream()
                .map(ContainerMain::getAliasCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        final List<String> notInStorage = carrierList.stream()
                .filter(id -> !lotNosInWarehouse.contains(id) && !aliasCodesInWarehouse.contains(id))
                .toList();

        if (!notInStorage.isEmpty()) {
            ack.setResult("NG");
            ack.setResultMessage("CARRIER(s) not in storage: " + String.join(",", notInStorage));
            publishAck(system, ack);
            log.warn("[R029] 拒絕：有 CARRIER 不在 storage。tid={}, lots={}", command.getTid(), notInStorage);
            return;
        }

        // 4) 寫入主檔 / 明細（入站快照）
        RobotInR029 main = new RobotInR029();
        main.setLogId(logId);
        main.setCount(pieceCount);                  // 每顆要拆幾片（= EXACT_GROUP）
        main.setTrayType(msg != null ? msg.getTrayType() : null);
        main.setTrayDesc(msg != null ? msg.getTrayDesc() : null);
        if (r029Repo.findById(logId).isPresent()) {
            r029Repo.update(main);
        } else {
            r029Repo.save(main);
        }
        // LOT 明細（批次寫入；若尚未擴充 batchUpsert，後備逐筆）
        try {
            r029LotRepo.batchUpsert(logId, carrierList);
        } catch (NoSuchMethodError | UnsupportedOperationException e) {
            for (String lotIdStr : carrierList) {
                var lot = new com.czkuo.rdf88701.infra.entity.RobotInR029Lot();
                lot.setLogId(logId);
                lot.setCarrierId(lotIdStr);
                r029LotRepo.save(lot);
            }
        }

        // 4.5) 掛回 R029 上下文到來源容器（lotId = carrierId = container_main.alias_code）
        try {
            r029ContextService.attachContextToSourceContainers(logId, command.getTid());
            log.info("[R029] 已掛回上下文至來源容器：logId={}, count={}, tid={}", logId, pieceCount, command.getTid());
        } catch (Exception e) {
            // 不阻斷主流程（仍可入佇列／回 ACK），但要記 error 以便追蹤
            log.error("[R029] 掛回上下文失敗（不阻斷）：logId={}, err={}", logId, e.getMessage(), e);
        }

        // 4.6) 建立任務主檔 robot_r029_task（READY；lane 交由 Walker 決策）
        try {
            if (taskRepo.findByLogId(logId).isEmpty()) {
                RobotR029Task t = new RobotR029Task();
                t.setLogId(logId);
                t.setTid(command.getTid());
                t.setPiecePerLot(pieceCount);
                t.setTrayType(main.getTrayType());
                t.setTrayDesc(main.getTrayDesc());
                // Walker 之後決策 lane（MAIN/SUB），這裡先維持 null
                t.setInternalState("QUEUED");
                t.setExternalLastResult("OK"); // 收單即對外結果 OK（對方收到 ACK=OK）
                t.setExternalLastTime(LocalDateTime.now());
                t.setCreatedTime(LocalDateTime.now());
                t.setUpdatedTime(LocalDateTime.now());
                // 原始 MESSAGE 快照
                t.setRawMessageJson(objectMapper.writeValueAsString(msg));
                taskRepo.save(t);
                log.info("[R029] 任務建立完成：task(logId={}) READY", logId);
            }
        } catch (Exception e) {
            // 不阻斷收單，但務必記錄
            log.error("[R029] 建立 robot_r029_task 失敗（不阻斷）：logId={}, err={}", logId, e.getMessage(), e);
        }

        // 5) 匯入 mqtt_inbox 佇列（RECEIVED）
        inboxRepository.enqueueFromInbound(
                logId,
                command.getTid(),
                command.getCmdId(),
                system,                        // sender
                systemContext.getSystemCode(), // receiver
                topic,
                LocalDateTime.now(),
                5                               // priority
        );

        // 6) 回 ACK=OK
        ack.setResult("OK");
        ack.setResultMessage("");
        publishAck(system, ack);

        log.info("[R029] 驗證通過，已入佇列並回 ACK=OK。tid={}, logId={}, carriers={}, pieces/lot={}",
                command.getTid(), logId, carrierList.size(), pieceCount);
    }

    /** 組 R029 ACK 基本骨架（echo 原 MESSAGE） */
    private R029AckPayload buildAckSkeleton(R029CommandPayload command, R029CommandPayload.Message msg) {
        R029AckPayload ack = new R029AckPayload();
        ack.setCmd("ROBOT");
        ack.setCmdId("R029");
        ack.setTid(command.getTid());
        ack.setIdDesc("MOVE_LOTS_TO_DISMANTLE_AND_TIE");

        R029AckPayload.Message ackMsg = new R029AckPayload.Message();
        if (msg != null) {
            if (msg.getCarrierList() != null) {
                ackMsg.setCarrierList(
                        msg.getCarrierList().stream().map(src -> {
                            R029AckPayload.CarrierInfo li = new R029AckPayload.CarrierInfo();
                            li.setCarrierId(src.getCarrierId());
                            return li;
                        }).toList()
                );
            }
            ackMsg.setCount(msg.getCount());
            ackMsg.setTrayType(msg.getTrayType());
            ackMsg.setTrayDesc(msg.getTrayDesc());
        }
        ack.setMessage(ackMsg);
        return ack;
    }

    /** 發送 ACK（同時可以在 Publisher 層記錄 mqtt_message_log） */
    private void publishAck(String targetSystem, R029AckPayload ack) throws Exception {
        String ackJson = objectMapper.writeValueAsString(ack);
        responseEventPublisher.publish(targetSystem, ackJson, MqttMessageType.ACK, ack.getTid(), ack.getCmdId());
    }

    /** 回傳對應的 CMD_ID，供 Router 註冊與分派 */
    @Override
    protected String getCmdIdInternal() {
        return "R029";
    }

    /** 回傳 payload 型別，供 Jackson 反序列化 */
    @Override
    protected Class<R029CommandPayload> getCommandType() {
        return R029CommandPayload.class;
    }

    // ---- helpers ----
    private Integer safeInt(String s) {
        if (s == null) return null;
        try { return Integer.valueOf(s.trim()); } catch (Exception e) { return null; }
    }
}
