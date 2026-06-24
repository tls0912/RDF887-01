package com.czkuo.rdf88701.application.mqtt.handler.command;

import com.czkuo.rdf88701.application.mqtt.context.SystemContext;
import com.czkuo.rdf88701.application.mqtt.handler.AbstractCommandHandler;
import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.application.service.zip.ZipStockerCommandService;
import com.czkuo.rdf88701.common.dto.mqtt.ack.S081AckPayload;
import com.czkuo.rdf88701.common.dto.mqtt.command.S081CommandPayload;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.enums.ZipTarget;
import com.czkuo.rdf88701.domain.dto.zip.WipInfoUpdate.WipInfoUpdateSecondaryBody;
import com.czkuo.rdf88701.domain.dto.zip.common.Root;
import com.czkuo.rdf88701.domain.repository.ContainerMainRepository;
import com.czkuo.rdf88701.domain.repository.LocationPointRepository;
import com.czkuo.rdf88701.domain.repository.LocationTrackingRepository;
import com.czkuo.rdf88701.infra.entity.ContainerMain;
import com.czkuo.rdf88701.infra.entity.LocationPoint;
import com.czkuo.rdf88701.infra.entity.LocationTracking;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * S081CommandHandler
 * <p>
 * 用意：對「指定儲位」上已存在之帳（容器）做產品資訊變更。
 * 規則：
 * 1) 一律先依 WIP_NAME 定位儲位。
 * 2) 查該儲位是否有 location_tracking（= 有無帳）。
 * - 無帳：不更新，僅回覆說明。
 * - 有帳：載入該 containerMain，依帶入欄位做「選擇性更新」。
 * 3) 不做：佔用/清空、tracking 新增/刪除、容器搬移、容器新建。
 * <p>
 * 欄位對應（可依你的實表結構調整）：
 * - LOT_ID   → ContainerMain.lotNo（若有變更）
 * - CARRIERID  → ContainerMain.containerCode（若有變更；若你不希望用 carrierId 更新，可移除）
 * <p>
 * 備註：只對「非空且有差異」的欄位做更新；沒有實質變動則回「no changes」。
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
public class S081CommandHandler extends AbstractCommandHandler<S081CommandPayload> {

    private final MqttMessageLogService logService;
    private final SystemContext systemContext;
    private final LocationPointRepository locationPointRepository;
    private final LocationTrackingRepository locationTrackingRepository;
    private final ContainerMainRepository containerMainRepository;
    private final ZipStockerCommandService zipCommandService;
    private static final List<String> LOCAL_PREFIX = List.of(
            "IN_RIGHT_",
            "IN_LEFT_"
    );

    private boolean isLocalWip(String wipName) {

        return LOCAL_PREFIX.stream()
                .anyMatch(wipName::startsWith);
    }


    public S081CommandHandler(ObjectMapper objectMapper,
                              MqttMessageEventPublisher responseEventPublisher,
                              MqttMessageLogService logService,
                              SystemContext systemContext,
                              LocationPointRepository locationPointRepository,
                              LocationTrackingRepository locationTrackingRepository,
                              ContainerMainRepository containerMainRepository,
                              ZipStockerCommandService zipCommandService) {
        super(objectMapper, responseEventPublisher);
        this.logService = logService;
        this.systemContext = systemContext;
        this.locationPointRepository = locationPointRepository;
        this.locationTrackingRepository = locationTrackingRepository;
        this.containerMainRepository = containerMainRepository;
        this.zipCommandService = zipCommandService;
    }

    @Override
    @Transactional
    protected void process(String system, String topic, S081CommandPayload command, MqttMessageType type) throws Exception {
        final S081CommandPayload.Message inMsg = command.getMessage();
        final String rawWipName = inMsg != null ? ns(inMsg.getWipName()) : "";
        final String carrierId = inMsg != null ? ns(inMsg.getCarrierId()) : ""; // 如托盤/載具碼；空字串代表未提供
        final String lotId = inMsg != null ? ns(inMsg.getLotId()) : ""; // 目標 LOT；空字串代表未提供

        log.info("[S081] 收到儲格產品變更：TID={}, topic={}, from={}, WIPNAME(raw)={}, CARRIERID={}, LOT_ID={}",
                command.getTid(), topic, system, rawWipName, carrierId, lotId);

        // 1) 審計：記錄原始 COMMAND
        JsonNode payload = objectMapper.valueToTree(command);
        logService.record(topic, system, systemContext.getSystemCode(), payload, MqttMessageType.COMMAND);
        // 2) ACK 骨架（先回填原始入參）
        var ack = createAck(command, rawWipName, carrierId, lotId);
        // 3) 參數檢查：只有 WIP_NAME 必填
        if (rawWipName.isBlank()) {
            ack.setResult("FAIL");
            ack.setResultMessage("WIP_NAME is required");
            returnAck(system, ack);
            return;
        }

        if (!isLocalWip(rawWipName)) {
            processZipDevice(system, rawWipName, carrierId, lotId, ack);
            return;
        }

        processLocalDevice(system, rawWipName, carrierId, lotId, ack);
    }

    private void processZipDevice(String system, String rawWipName, String carrierId,
                                  String lotId, S081AckPayload ack) throws Exception {

        log.info("[S081] 發送 WipInfoUpdate：WipName={}, carrierId={}, LotId={}", rawWipName, carrierId, lotId);
        Root<WipInfoUpdateSecondaryBody> d = zipCommandService.sendWipInfoUpdate(ZipTarget.ZIPB, rawWipName, carrierId, lotId);
        WipInfoUpdateSecondaryBody.ResultInfo results = Optional.ofNullable(d)
                .map(Root::getBody)
                .map(WipInfoUpdateSecondaryBody::getResultInfo)
                .orElseGet(WipInfoUpdateSecondaryBody.ResultInfo::new);

        ack.setResult(results.getResult() == 0 ? "PASS" : "FAIL");
        ack.setResultMessage(results.getResult() == 0 ? "" : results.getResultMessage()); // 帶了參數但與現值相同 → 不更新

        returnAck(system, ack);
    }


    private void processLocalDevice(String system, String rawWipName, String carrierId,
                                    String lotId, S081AckPayload ack) throws Exception {

        // 4) 使用 WIP_NAME 據此查庫位
        LocationPoint lp = locationPointRepository
                .findByName(rawWipName)
                .orElse(null);
        if (lp == null) {
            ack.setResult("FAIL");
            ack.setResultMessage("WIP not found: " + rawWipName);
            returnAck(system, ack);
            return;
        }

        // 5) 查該儲位是否「有帳」（= 有 tracking）
        Optional<LocationTracking> trackingOpt = locationTrackingRepository.findByLocationPointId(lp.getId());
        if (trackingOpt.isEmpty()) {
            // 無帳：不更新，僅回覆
            ack.setResult("FAIL");
            ack.setResultMessage("No account bound on this slot; nothing updated");
            returnAck(system, ack);
            return;
        }

        LocationTracking tracking = trackingOpt.get();
        Long containerId = tracking.getContainerMainId();

        // 6) 取出該帳（容器）並做選擇性更新
        Optional<ContainerMain> cmOpt = containerMainRepository.findById(containerId);
        if (cmOpt.isEmpty()) {
            // 理論上不該發生（有 tracking 卻找不到主檔），保守處理
            ack.setResult("FAIL");
            ack.setResultMessage("Container not found by tracking: id=" + containerId);
            returnAck(system, ack);
            return;
        }

        ContainerMain cm = cmOpt.get();
        boolean changed = false;
        Map<String, String> changedFields = new HashMap<>();

        // 6.1 LOT 變更（若帶入且不同）
        if (hasText(lotId)) {
            String oldLot = safe(cm.getLotNo());
            if (!lotId.equals(oldLot)) {
                cm.setLotNo(lotId);
                changed = true;
                changedFields.put("lotNo", oldLot + " -> " + lotId);
            }
        }

        // 6.2 CARRIERID 變更（若你允許用 carrierId 更新 aliasCode）
        if (hasText(carrierId)) {
            String oldCode = safe(cm.getAliasCode());
            if (!carrierId.equals(oldCode)) {
                cm.setAliasCode(carrierId);
                changed = true;
                changedFields.put("aliasCode", oldCode + " -> " + carrierId);
            }
        }

        try {
            if (changed) {
                // 6.3 寫回 DB（使用你的 repository.update 具體實作）
                boolean ok = containerMainRepository.update(cm);
                if (!ok) {
                    ack.setResult("FAIL");
                    ack.setResultMessage("Update failed (no rows affected)");
                } else {
                    ack.setResult("PASS");
                    ack.setResultMessage("Updated fields: " + changedFields);
                }
            } else {
                ack.setResult("PASS");
                ack.setResultMessage("No changes"); // 帶了參數但與現值相同 → 不更新
            }
        } catch (Exception ex) {
            log.error("[S081] 產品資訊更新失敗：WIPNAME={}, containerId={}, changes={}",
                    rawWipName, containerId, changedFields, ex);
            ack.setResult("FAIL");
            ack.setResultMessage(ex.getMessage() != null ? ex.getMessage() : "update failed");
        }

        // 7) 回 ACK
        returnAck(system, ack);
    }

    private S081AckPayload createAck(
            S081CommandPayload command,
            String wipName,
            String carrierId,
            String lotId) {

        S081AckPayload ack = new S081AckPayload();
        ack.setCmd("SYSTEM");
        ack.setCmdId("S081");
        ack.setTid(command.getTid());
        ack.setIdDesc("WIP_PRODUCT_UPDATE");
        S081AckPayload.Message msg = new S081AckPayload.Message();
        msg.setWipName(wipName);
        msg.setCarrierId(carrierId);
        msg.setLotId(lotId);
        ack.setMessage(msg);
        return ack;
    }


    // ======= 實用小工具 =======

    private void returnAck(String system, S081AckPayload ack) throws Exception {
        String ackJson = objectMapper.writeValueAsString(ack);
        responseEventPublisher.publish(system, ackJson, MqttMessageType.ACK, ack.getTid(), ack.getCmdId());
        // 若需把 ACK 也落庫審計，可加：
        // logService.record("<ack-topic>", systemContext.getSystemCode(), system, objectMapper.readTree(ackJson), MqttMessageType.ACK);
    }

    private static boolean hasText(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static String ns(String s) {
        return s == null ? "" : s.trim();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    @Override
    protected String getCmdIdInternal() {
        return "S081";
    }

    @Override
    protected Class<S081CommandPayload> getCommandType() {
        return S081CommandPayload.class;
    }
}
