package com.czkuo.rdf88701.application.mqtt.handler.command;

import com.czkuo.rdf88701.application.mqtt.context.SystemContext;
import com.czkuo.rdf88701.application.mqtt.handler.AbstractCommandHandler;
import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.application.service.wip.WipSlotQueryService;
import com.czkuo.rdf88701.application.service.zip.ZipStockerCommandService;
import com.czkuo.rdf88701.common.dto.mqtt.ack.S004AckPayload;
import com.czkuo.rdf88701.common.dto.mqtt.command.S004CommandPayload;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.enums.ZipTarget;
import com.czkuo.rdf88701.domain.dto.wip.WipSlotDetailDTO;
import com.czkuo.rdf88701.domain.dto.zip.StatusQuery.StatusQuerySecondaryBody;
import com.czkuo.rdf88701.domain.dto.zip.common.Root;
import com.czkuo.rdf88701.domain.repository.ContainerMainRepository;
import com.czkuo.rdf88701.domain.repository.L005SessionRepository;
import com.czkuo.rdf88701.infra.entity.ContainerMain;
import com.czkuo.rdf88701.infra.entity.L005Session;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Slf4j
@Component
public class S004CommandHandler extends AbstractCommandHandler<S004CommandPayload> {

    private final MqttMessageLogService logService;
    private final SystemContext systemContext;
    private final WipSlotQueryService wipSlotQueryService;
    private final ZipStockerCommandService zipCommandService;
    private final ContainerMainRepository containerMainRepository;
    private final L005SessionRepository l005SessionRepository;

    public S004CommandHandler(ObjectMapper objectMapper,
                              MqttMessageEventPublisher responseEventPublisher,
                              MqttMessageLogService logService,
                              SystemContext systemContext,
                              WipSlotQueryService wipSlotQueryService,
                              ZipStockerCommandService zipCommandService,
                              ContainerMainRepository containerMainRepository,
                              L005SessionRepository l005SessionRepository) {
        super(objectMapper, responseEventPublisher);
        this.logService = logService;
        this.systemContext = systemContext;
        this.wipSlotQueryService = wipSlotQueryService;
        this.zipCommandService = zipCommandService;
        this.containerMainRepository = containerMainRepository;
        this.l005SessionRepository = l005SessionRepository;
    }

    @Override
    protected void process(String system, String topic, S004CommandPayload command, MqttMessageType type) throws Exception {
        log.info("[S004] 收到查詢 WIP 請求，TID={}, topic={}, system={}", command.getTid(), topic, system);

        // 1) 紀錄 COMMAND
        JsonNode payload = objectMapper.valueToTree(command);
        logService.record(
                topic,
                system,                        // sender
                systemContext.getSystemCode(), // receiver
                payload,
                MqttMessageType.COMMAND
        );

        // 2) 查 DB 儲格（WIP）
        List<WipSlotDetailDTO> slotList = wipSlotQueryService.queryAllWipSlots();

        // 3) 組 ACK：先鋪 WIP（STATUS = isLocked）
        S004AckPayload ack = new S004AckPayload();
        ack.setCmd("SYSTEM");
        ack.setCmdId("S004");
        ack.setIdDesc("SYSTEM_COMPARE_DB");
        ack.setTid(command.getTid());
        ack.setResult("OK");
        ack.setResultMessage("");

        S004AckPayload.Message message = new S004AckPayload.Message();

        for (WipSlotDetailDTO slot : slotList) {
            String wipName = safeName(slot.getLocationName());
            if (wipName == null) {
                log.warn("[S004] 略過一筆 DB 儲格：locationName 為空，locationCode={}, zone={}",
                        slot.getLocationCode(), slot.getZoneCode());
                continue;
            }

            String onOff = !Boolean.TRUE.equals(slot.getIsLocked()) ? "ON" : "OFF";
            String carrier = emptyToNull(slot.getContainerAliasCode());
            String lot     = emptyToNull(slot.getLotNo());
            String trayNum = (slot.getEstimatedQuantity() != null)
                    ? slot.getEstimatedQuantity().toString()
                    : "0";

            message.addWip(wipName, onOff, carrier, lot, trayNum);
        }

        // 4) 追加 ZIPA / ZIPB（回補 LOT 需查 container_main）
        appendZipSlots(message, ZipTarget.ZIPA);
        appendZipSlots(message, ZipTarget.ZIPB);

        ack.setMessage(message);

        // 5) 發 ACK
        String ackJson = objectMapper.writeValueAsString(ack);
        responseEventPublisher.publish(system, ackJson, MqttMessageType.ACK, ack.getTid(), ack.getCmdId());
    }

    @Override
    protected String getCmdIdInternal() {
        return "S004";
    }

    @Override
    protected Class<S004CommandPayload> getCommandType() {
        return S004CommandPayload.class;
    }

    /**
     * 追加 ZIP 資料：
     * - 不與 WIP 名稱關聯；直接 append
     * - 若 ZIP 缺 LOT，且有 CARRIERID，就用 container_main 查回補 LOT
     * - TRAY 僅信任 ZIP 的數字；缺就維持 "0"
     */
    private void appendZipSlots(S004AckPayload.Message message, ZipTarget target) {
        try {
            Root<StatusQuerySecondaryBody> resp = zipCommandService.queryAllSlots(target);
            if (resp == null || resp.getBody() == null || resp.getBody().getStatusInfos() == null) {
                log.warn("[S004] StatusQuery(target={}) 回覆為空", target);
                return;
            }

            int appended = 0;
            Set<String> seenZipNames = new HashSet<>(); // 僅避免同一 ZIP 回重複名稱

            for (StatusQuerySecondaryBody.StatusInfo s : resp.getBody().getStatusInfos()) {
                if (s == null || s.getType() != 3) continue; // 只處理 Type=3（儲格）

                String wipName = safeName(s.getName()); // ZIP 顯示名稱
                if (wipName == null || !seenZipNames.add(wipName)) continue;

                String onOff = (s.getStatus() == 41) ? "ON" : "OFF";

                // ZIP: message[0]=CARRIERID, message[1]=TRAY_NUM
                String zipCarrier = null;
                String zipTray    = "0";
                List<?> msgList = s.getMessage();
                if (msgList != null) {
                    if (msgList.size() >= 2 && msgList.get(1) != null) {
                        zipCarrier = safeText(msgList.get(1));
                    }
                    if (msgList.size() >= 4 && msgList.get(3) != null) {
                        String raw = safeText(msgList.get(3));
                        zipTray = parseIntStringOrZero(raw);
                    }
                }

                String carrierId = emptyToDefault(zipCarrier, "");
                String lotId     = ""; // 先預設空

                // 只有在 ZIP 有給 carrierId 時，才從 container_main 回補 LOT
                if (target == ZipTarget.ZIPA) {
                    if (!carrierId.isEmpty()) {
                        lotId = lookupLotByCarrierToZipA(carrierId).orElse("");
                    }
                } else if (target == ZipTarget.ZIPB) {
                    if (!carrierId.isEmpty()) {
                        lotId = lookupLotByCarrierToZipB(carrierId).orElse("");
                    }
                }

                // 參數順序：WIPNAME, STATUS, CARRIERID, LOT_ID, TRAY_NUM
                message.addWip(wipName, onOff, carrierId, lotId, emptyToDefault(zipTray, "0"));
                appended++;
            }

            log.info("[S004] 追加 {} 筆來自 {} 的儲格資訊（LOT 以 l005_session/container_main 回補）", appended, target);

        } catch (Exception e) {
            log.error("[S004] 查詢 {} 的 StatusQuery 失敗：{}", target, e.getMessage(), e);
        }
    }

    // ---------- container_main 回補 ----------

    /** 以 carrierId 尋找對應 container_main.lot_no（先 aliasCode，再 containerCode） */
    private Optional<String> lookupLotByCarrierToZipA(String carrierId) {
        try {
            // 使用 L005 Session 查
            Optional<L005Session> sOpt = l005SessionRepository.findLatestByPeerCarrierId(carrierId);
            if (sOpt.isPresent()) {
                String lot = sOpt.get().getPeerLotId();
                return Optional.ofNullable(lot).map(String::trim).filter(s -> !s.isEmpty());
            }
        } catch (Exception ex) {
            log.error("[S004] 依 CARRIERID 查 L005Session 失敗，carrierId={}：{}", carrierId, ex.getMessage(), ex);
        }
        return Optional.empty();
    }

    /** 以 carrierId 尋找對應 container_main.lot_no（先 aliasCode，再 containerCode） */
    private Optional<String> lookupLotByCarrierToZipB(String carrierId) {
        try {
            // 先以 alias_code 查
            Optional<ContainerMain> byAlias = containerMainRepository.findByAliasCode(carrierId);
            if (byAlias.isPresent()) {
                String lot = byAlias.get().getLotNo();
                return Optional.ofNullable(lot).map(String::trim).filter(s -> !s.isEmpty());
            }

            // 再以 container_code 查
            Optional<ContainerMain> byBarcode = containerMainRepository.findByContainerCode(carrierId);
            if (byBarcode.isPresent()) {
                String lot = byBarcode.get().getLotNo();
                return Optional.ofNullable(lot).map(String::trim).filter(s -> !s.isEmpty());
            }
        } catch (Exception ex) {
            log.error("[S004] 依 CARRIERID 查 container_main 失敗，carrierId={}：{}", carrierId, ex.getMessage(), ex);
        }
        return Optional.empty();
    }

    // ---------- 小工具 ----------

    private static String safeName(Object o) {
        String s = (o == null) ? null : o.toString().trim();
        return (s == null || s.isEmpty()) ? null : s;
    }

    private static String safeText(Object o) {
        return o == null ? null : o.toString().trim();
    }

    private static String parseIntStringOrZero(String raw) {
        if (raw == null || raw.isEmpty()) return "0";
        if (raw.matches("\\d+")) return raw;
        try {
            return String.valueOf(new BigDecimal(raw).intValue());
        } catch (Exception ignored) {
            return "0";
        }
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }

    private static String emptyToDefault(String s, String def) {
        return (s == null || s.isEmpty()) ? def : s;
    }
}
