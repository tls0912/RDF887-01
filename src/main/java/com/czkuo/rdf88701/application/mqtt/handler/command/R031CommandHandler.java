package com.czkuo.rdf88701.application.mqtt.handler.command;


import com.czkuo.rdf88701.application.mqtt.context.SystemContext;
import com.czkuo.rdf88701.application.mqtt.handler.AbstractCommandHandler;
import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.application.service.wip.WipSlotQueryService;
import com.czkuo.rdf88701.application.service.zip.ZipStockerCommandService;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.dto.mqtt.ack.R031AckPayload;
import com.czkuo.rdf88701.common.dto.mqtt.command.R031CommandPayload;
import com.czkuo.rdf88701.common.enums.ZipTarget;
import com.czkuo.rdf88701.domain.dto.wip.WipSlotDetailDTO;
import com.czkuo.rdf88701.domain.dto.zip.StatusQuery.StatusQuerySecondaryBody;
import com.czkuo.rdf88701.domain.dto.zip.common.Root;
import com.czkuo.rdf88701.domain.repository.*;
import com.czkuo.rdf88701.infra.entity.RobotInR031;
import com.czkuo.rdf88701.infra.entity.RobotR031Task;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * R031CommandHandler
 * ------------------------------------------------------------
 * CMD_ID=R031：從 WIP(STK) 或 ZIPA 的「指定儲位」搬貨至 Manual Port
 * 驗證規則：
 *  (1) 只看 CARRIERID 是否在該 slot（LOT 僅為 echo/追蹤，但為必填欄位）
 *  (2) 先查 ZIP：StatusQuery Type=3, Name="*"; 尋找 Name==WIPNAME 的 slot，檢查 message[0] == CARRIERID
 *  (3) 若 ZIP 無此 slot 或不吻合，再查 WIP：locationName==WIPNAME 且 containerAliasCode == CARRIERID
 *  (4) 兩邊都找不到該 slot → FAIL: WIPNAME not found；有 slot 但 carrier 不符 → FAIL: CARRIERID mismatched
 *
 * 流程：
 *  1) 記錄 COMMAND 至 mqtt_message_log（取得 logId）
 *  2) 規則驗證（不通過→ACK=FAIL）
 *  3) 通過→寫入 robot_in_r031（以 logId 關聯），upsert robot_r031_task（internal_state=QUEUED），
 *     匯入 mqtt_inbox 佇列並回填 inboxId，ACK=OK
 *
 * 對外 RESULT 規則：
 *  - 接單：OK（本 Handler 回覆）
 *  - 開始：START（Walker 觸發）
 *  - 完成：END，RESULT_MESSAGE=實際 Manual Port 名稱（Walker 觸發）
 *  - 失敗/拒絕：FAIL + RESULT_MESSAGE=原因
 *  - 取消：CANCLE（Walker 觸發）
 */
@Slf4j
@Component
public class R031CommandHandler extends AbstractCommandHandler<R031CommandPayload> {

    private final MqttMessageLogService logService;
    private final SystemContext systemContext;
    private final MqttInboxRepository inboxRepository;
    private final RobotInR031Repository r031Repo;
    private final RobotR031TaskRepository r031TaskRepo;
    private final ZipStockerCommandService zipCommandService;
    private final WipSlotQueryService wipSlotQueryService;

    public R031CommandHandler(ObjectMapper objectMapper,
                              MqttMessageEventPublisher responseEventPublisher,
                              MqttMessageLogService logService,
                              SystemContext systemContext,
                              MqttInboxRepository inboxRepository,
                              RobotInR031Repository r031Repo,
                              RobotR031TaskRepository r031TaskRepo,
                              ZipStockerCommandService zipCommandService,
                              WipSlotQueryService wipSlotQueryService) {
        super(objectMapper, responseEventPublisher);
        this.logService = logService;
        this.systemContext = systemContext;
        this.inboxRepository = inboxRepository;
        this.r031Repo = r031Repo;
        this.r031TaskRepo = r031TaskRepo;
        this.zipCommandService = zipCommandService;
        this.wipSlotQueryService = wipSlotQueryService;
    }

    @Override
    protected void process(String system, String topic, R031CommandPayload command, MqttMessageType type) throws Exception {
        final R031CommandPayload.Message msg = command.getMessage();
        final String lotId     = (msg != null && msg.getLotId()     != null) ? msg.getLotId().trim()     : "";
        final String carrierId = (msg != null && msg.getCarrierId() != null) ? msg.getCarrierId().trim() : "";
        final String wipName   = (msg != null && msg.getWipName()   != null) ? msg.getWipName().trim()   : "";

        log.info("[R031] 收到通知從WIP(STK)搬貨至Manual Port：tid={}, topic={}, sender={}, LOT_ID={}, CARRIERID={}, WIPNAME={}",
                command.getTid(), topic, system, lotId, carrierId, wipName);

        // 1) 記錄 COMMAND（取得 logId）
        final JsonNode payload = objectMapper.valueToTree(command);
        final Long logId = logService.recordReturningId(
                topic,
                system,
                systemContext.getSystemCode(),
                payload,
                MqttMessageType.COMMAND
        );

        // 預建 ACK
        R031AckPayload ack = buildAckSkeleton(command, msg);

        // 2) 必填欄位檢查
        if (lotId.isEmpty() || carrierId.isEmpty() || wipName.isEmpty()) {
            ack.setResult("FAIL");
            ack.setResultMessage("Missing required fields: LOT_ID, CARRIERID and WIPNAME are required.");
            publishAck(system, ack);
            log.warn("[R031] FAIL：欄位不足。tid={}, lotId={}, carrierId={}, wipName={}", command.getTid(), lotId, carrierId, wipName);
            return;
        }

        // 3) 驗證：ZIP → WIP
        boolean slotFoundZip = false, slotFoundWip = false;
        boolean carrierMatchZip = false, carrierMatchWip = false;
        String zipDetail = null, wipDetail = null;

        try {
            Root<StatusQuerySecondaryBody> resp = zipCommandService.queryAllSlots(ZipTarget.ZIPA);
            StatusQuerySecondaryBody.StatusInfo slot = pickZipType3SlotByName(resp, wipName);
            if (slot != null) {
                slotFoundZip = true;
                String zipCarrier = readZipCarrierFromType3(slot);
                if (zipCarrier != null && zipCarrier.equalsIgnoreCase(carrierId)) {
                    carrierMatchZip = true;
                    zipDetail = "ZIPA 命中（slot=" + wipName + ", carrier=" + zipCarrier + "）";
                } else {
                    zipDetail = "ZIPA slot=" + wipName + " carrier 不符（ZIP=" + (zipCarrier == null ? "null" : zipCarrier) + "）";
                }
            } else {
                zipDetail = "ZIPA 無此儲位：" + wipName;
            }
        } catch (Exception e) {
            zipDetail = "ZIPA 查詢失敗：" + e.getMessage();
            log.error("[R031] ZIPA 查詢失敗：tid={}, wipName={}, err={}", command.getTid(), wipName, e.getMessage(), e);
        }

        if (!slotFoundZip) {
            try {
                List<WipSlotDetailDTO> all = wipSlotQueryService.queryAllWipSlots();
                WipSlotDetailDTO slot = all.stream()
                        .filter(s -> s.getLocationName() != null && s.getLocationName().trim().equalsIgnoreCase(wipName))
                        .findFirst().orElse(null);
                if (slot != null) {
                    slotFoundWip = true;
                    String dbCarrier = slot.getContainerAliasCode();
                    if (dbCarrier != null && dbCarrier.trim().equalsIgnoreCase(carrierId)) {
                        carrierMatchWip = true;
                        wipDetail = "WIP 命中（slot=" + wipName + ", carrier=" + dbCarrier + "）";
                    } else {
                        wipDetail = "WIP slot=" + wipName + " carrier 不符（WIP=" + (dbCarrier == null ? "null" : dbCarrier) + "）";
                    }
                } else {
                    wipDetail = "WIP 無此儲位：" + wipName;
                }
            } catch (Exception e) {
                wipDetail = "WIP 查詢失敗：" + e.getMessage();
                log.error("[R031] WIP 查詢失敗：tid={}, wipName={}, err={}", command.getTid(), wipName, e.getMessage(), e);
            }
        }

        // 4) 決策
        if (carrierMatchZip || carrierMatchWip) {
            // accept
        } else if (!slotFoundZip && !slotFoundWip) {
            ack.setResult("FAIL");
            String detail = (zipDetail != null ? zipDetail : "") + ((zipDetail != null && wipDetail != null) ? "；" : "") + (wipDetail != null ? wipDetail : "");
            ack.setResultMessage("WIPNAME not found: " + wipName + ". " + detail);
            publishAck(system, ack);
            log.warn("[R031] FAIL：WIPNAME 無效。tid={}, wipName={}, detail={}", command.getTid(), wipName, detail);
            return;
        } else {
            ack.setResult("FAIL");
            String detail = (zipDetail != null ? zipDetail : "") + ((zipDetail != null && wipDetail != null) ? "；" : "") + (wipDetail != null ? wipDetail : "");
            ack.setResultMessage("CARRIERID mismatched at the given slot. " + detail);
            publishAck(system, ack);
            log.warn("[R031] FAIL：carrier 不符。tid={}, wipName={}, carrierId={}, detail={}", command.getTid(), wipName, carrierId, detail);
            return;
        }

        // 5) 寫入 robot_in_r031（logId 關聯）
        RobotInR031 rec = new RobotInR031();
        rec.setLogId(logId);
        rec.setLotId(lotId);
        rec.setCarrierId(carrierId);
        rec.setWipName(wipName);
        if (r031Repo.findById(logId).isPresent()) r031Repo.update(rec);
        else r031Repo.save(rec);

        // 6) upsert robot_r031_task（以 log_id 冪等）
        try {
            RobotR031Task task = new RobotR031Task();
            task.setLogId(logId);
            task.setInboxId(null);
            task.setTid(command.getTid());
            task.setLotId(lotId);
            task.setCarrierId(carrierId);
            task.setWipName(wipName);
            try {
                task.setRawMessageJson(objectMapper.writeValueAsString(msg));
            } catch (Exception e) {
                log.warn("[R031] rawMessageJson 序列化失敗：{}", e.getMessage());
                task.setRawMessageJson(null);
            }
            task.setInternalState("QUEUED");
            task.setExternalLastResult("OK");
            task.setExternalLastTime(LocalDateTime.now());
            task.setCreatedTime(LocalDateTime.now());
            task.setUpdatedTime(LocalDateTime.now());

            if (r031TaskRepo.findByLogId(logId).isPresent()) {
                r031TaskRepo.updateByLogId(task);
            } else {
                r031TaskRepo.save(task);
            }
        } catch (DataAccessException dae) {
            ack.setResult("FAIL");
            ack.setResultMessage("DB 錯誤：寫入 robot_r031_task 失敗 - " + safeMsg(dae));
            publishAck(system, ack);
            log.error("[R031] DB 錯誤：寫入 robot_r031_task 失敗", dae);
            return;
        }

        // 7) 入佇列並回填 inboxId
        Long inboxId;
        try {
            inboxId = inboxRepository.enqueueFromInbound(
                    logId,
                    command.getTid(),
                    command.getCmdId(),
                    system,
                    systemContext.getSystemCode(),
                    topic,
                    LocalDateTime.now(),
                    5
            );
        } catch (DataAccessException dae) {
            ack.setResult("FAIL");
            ack.setResultMessage("DB 錯誤：寫入 mqtt_inbox 失敗 - " + safeMsg(dae));
            publishAck(system, ack);
            log.error("[R031] DB 錯誤：寫入 mqtt_inbox 失敗", dae);
            return;
        }
        r031TaskRepo.updateInboxIdByLogId(logId, inboxId);

        // 8) ACK=OK
        ack.setResult("OK");
        ack.setResultMessage("");
        publishAck(system, ack);

        log.info("[R031] 成功接單：logId={}，已入佇列並建立/更新任務（internal_state=QUEUED）", logId);
    }

    // ---------------- ZIP Type=3（儲格）工具 ----------------

    /** 從 ZIP StatusQuery(Type=3, Name="*") 回覆中，挑出 Name==wipName 的那一筆 slot 資訊 */
    private StatusQuerySecondaryBody.StatusInfo pickZipType3SlotByName(Root<StatusQuerySecondaryBody> resp, String wipName) {
        if (resp == null || resp.getBody() == null || resp.getBody().getStatusInfos() == null) return null;
        for (StatusQuerySecondaryBody.StatusInfo s : resp.getBody().getStatusInfos()) {
            if (s == null || s.getName() == null) continue;
            if (s.getType() != 3) continue;
            String name = s.getName().toString().trim();
            if (!name.equalsIgnoreCase(wipName)) continue;
            return s;
        }
        return null;
    }

    /** 讀取 ZIP Type=3 slot 的 CARRIERID（message[1]），若無或空白則回 null */
    private String readZipCarrierFromType3(StatusQuerySecondaryBody.StatusInfo slot) {
        if (slot == null || slot.getMessage() == null || slot.getMessage().isEmpty()) return null;
        Object m1 = slot.getMessage().get(1);
        if (m1 == null) return null;
        String s = m1.toString().trim();
        return s.isEmpty() ? null : s;
    }

    // ---------------- ACK/骨架/工具 ----------------

    private R031AckPayload buildAckSkeleton(R031CommandPayload command, R031CommandPayload.Message msg) {
        R031AckPayload ack = new R031AckPayload();
        ack.setCmd("ROBOT");
        ack.setCmdId("R031");
        ack.setTid(command.getTid());
        ack.setIdDesc("STK_MOVE_SCH_TO_MANUAL_PORT");

        R031AckPayload.Message ackMsg = new R031AckPayload.Message();
        if (msg != null) {
            ackMsg.setLotId(msg.getLotId());
            ackMsg.setCarrierId(msg.getCarrierId());
            ackMsg.setWipName(msg.getWipName());
        }
        ack.setMessage(ackMsg);
        return ack;
    }

    private void publishAck(String targetSystem, R031AckPayload ack) throws Exception {
        String ackJson = objectMapper.writeValueAsString(ack);
        responseEventPublisher.publish(targetSystem, ackJson, MqttMessageType.ACK, ack.getTid(), ack.getCmdId());
    }

    /**
     * 回傳對應的 CMD_ID，供 Router 註冊與分派
     */
    @Override
    protected String getCmdIdInternal() {
        return "R031";
    }

    /**
     * 回傳 payload 型別，供 Jackson 反序列化
     */
    @Override
    protected Class<R031CommandPayload> getCommandType() {
        return R031CommandPayload.class;
    }

    // ---- helpers ----
    private static String toText(Object o) {
        if (o == null) return null;
        String s = o.toString();
        if (s == null) return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    private static Integer toInt(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(o.toString().trim());
        } catch (Exception ignore) {
            return null;
        }
    }

    private static String safeMsg(Throwable t) {
        String m = t.getMessage();
        return (m == null || m.isBlank()) ? t.getClass().getSimpleName() : m;
    }
}
