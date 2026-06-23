package com.czkuo.rdf88701.application.mqtt.util;

import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.domain.repository.MqttMessageLogRepository;
import com.czkuo.rdf88701.infra.mqtt.PendingSendRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Set;

/**
 * MqttMessageTypeResolver
 * - 根據 payload 結構與歷史紀錄判斷訊息型別（COMMAND / ACK）
 * - 基於 TID + CMD_ID 與發送方向進行準確推論
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqttMessageTypeResolver {

    private final ObjectMapper objectMapper;
    private final MqttMessageLogRepository mqttMessageLogRepository;
    private final PendingSendRegistry pendingSendRegistry;

    // ─────────────────────────────────────────────────────────
    // 共用工具
    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
    private static String text(JsonNode n, String f) { JsonNode x = n.get(f); return x == null ? "" : x.asText(""); }
    private static String norm(String s){ return s==null? "" : s.trim().toLowerCase(); }

    // 兩家都適用的特例：S001 / S002 → RESULT 為 null/空→COMMAND，否則 ACK
    private static final Set<String> specialS001S002 = Set.of("S001", "S002");

    // ─────────────────────────────────────────────────────────
    // ASE 白名單
    private static final Set<String> ASE_COMMANDS = Set.of(
            "S003","S004","S014","S015","S016","S019","S021","S022",
            "S044","S045","S065","S066","S067","S069","S074","S075","S081",
            "U020",
            "R007","R008","R018","R029","R031",
            "A009"
    );
    private static final Set<String> ASE_ACKS = Set.of(
            "S010","S011","S012","S013","S068","S072","S073",
            "L005",
            "A015"
    );

    // ─────────────────────────────────────────────────────────
    // SEEC 白名單
    private static final Set<String> SEEC_COMMANDS = Set.of(
            "S007","S008",
            "A008","A010","A013","A014","A015"
    );
    private static final Set<String> SEEC_ACKS = Set.of(
            "S014","S015","S016","S067","S074","S075",
            "R007","R008","R018",
            "A009"
    );

    // 若你環境中的系統代號可能有別名，這裡統一映射
    private static final Map<String,String> SENDER_ALIAS = Map.of(
            "ase","ase",
            "ase-mq","ase",
            "seec","seec",
            "seec-mq","seec"
    );

    /**
     * 判斷 MQTT 訊息型別
     *
     * @param payload          MQTT JSON 格式內容
     * @param senderSystem     發送方（對方）
     * @param receiverSystem   接收方（我方）
     * @return COMMAND 或 ACK（或 UNKNOWN）
     */
    public MqttMessageType resolve(String payload, String senderSystem, String receiverSystem) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String cmdId = node.path("CMD_ID").asText();
            String tid = node.path("TID").asText();

            if (!StringUtils.hasText(cmdId) || !StringUtils.hasText(tid)) {
                log.warn("[MQTT][RESOLVE] 缺少 CMD_ID 或 TID，判斷失敗");
                return MqttMessageType.UNKNOWN;
            }

            String cmd = cmdId.toUpperCase();
            String sender = SENDER_ALIAS.getOrDefault(norm(senderSystem), norm(senderSystem));

            // ── ASE / SEEC 共同特例：S001 / S002 以 RESULT 是否為空決定
            if ("ase".equals(sender) || "seec".equals(sender)) {
                if (specialS001S002.contains(cmd)) {
                    String result = text(node, "RESULT");
                    if (isBlank(result)) {
                        //log.debug("[MQTT][{}] {} (RESULT 空) → COMMAND；tid={}", sender.toUpperCase(), cmd, tid);
                        return MqttMessageType.COMMAND;
                    } else {
                        //log.debug("[MQTT][{}] {} (RESULT 有值) → ACK；tid={}", sender.toUpperCase(), cmd, tid);
                        return MqttMessageType.ACK;
                    }
                }
            }

            // ── ASE 白名單
            if ("ase".equals(sender)) {
                if (ASE_COMMANDS.contains(cmd)) {
                    //log.debug("[MQTT][ASE] {} 命中 COMMAND 白名單；tid={}", cmd, tid);
                    return MqttMessageType.COMMAND;
                }
                if (ASE_ACKS.contains(cmd)) {
                    //log.debug("[MQTT][ASE] {} 命中 ACK 白名單；tid={}", cmd, tid);
                    return MqttMessageType.ACK;
                }
                log.warn("[MQTT][ASE] 未在白名單內的 CMD_ID：{}，tid={} → UNKNOWN（僅記錄）", cmd, tid);
                return MqttMessageType.UNKNOWN;
            }

            // ── SEEC 白名單
            if ("seec".equals(sender)) {
                if (SEEC_COMMANDS.contains(cmd)) {
                    //log.debug("[MQTT][SEEC] {} 命中 COMMAND 白名單；tid={}", cmd, tid);
                    return MqttMessageType.COMMAND;
                }
                if (SEEC_ACKS.contains(cmd)) {
                    //log.debug("[MQTT][SEEC] {} 命中 ACK 白名單；tid={}", cmd, tid);
                    return MqttMessageType.ACK;
                }
                log.warn("[MQTT][SEEC] 未在白名單內的 CMD_ID：{}，tid={} → UNKNOWN（僅記錄）", cmd, tid);
                return MqttMessageType.UNKNOWN;
            }

            // ── 其他系統：沿用你原本的 fallback（如不需要可改成直接 UNKNOWN）
            boolean pendingHit = false;
            try { pendingHit = pendingSendRegistry.isPendingFrom(senderSystem, tid, cmd); } catch (Exception ignore) { }
            if (pendingHit) { // 若我方「剛對 sender 送出同 tid/cmdId」且尚在 TTL 內，直接判斷對方這筆為 ACK
                //log.debug("[MQTT][RESOLVE] 非 ASE/SEEC：pending 命中 → ACK；tid={}, cmd={}", tid, cmd);
                return MqttMessageType.ACK;
            }

            // 備援: 判斷我方是否曾對對方發出此指令（若有，即代表這是對方回 ACK）
            boolean hasSent = mqttMessageLogRepository.existsSentCommand(tid, cmd, receiverSystem, senderSystem);
            MqttMessageType type = hasSent ? MqttMessageType.ACK : MqttMessageType.COMMAND;
            log.info("[MQTT][RESOLVE] 非 ASE/SEEC fallback：tid={}, cmd={}, type={}", tid, cmd, type);
            return type;

        } catch (Exception e) {
            log.error("[MQTT][RESOLVE] JSON 解析錯誤，payload={}, error={}", payload, e.getMessage(), e);
            return MqttMessageType.UNKNOWN;
        }
    }
}
