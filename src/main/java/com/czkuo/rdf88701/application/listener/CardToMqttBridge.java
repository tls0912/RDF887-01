package com.czkuo.rdf88701.application.listener;

import com.czkuo.rdf88701.application.service.mqtt.MqttCommandService;
import com.czkuo.rdf88701.common.dto.MqttSendResult;
import com.czkuo.rdf88701.config.mqtt.MqttCardBridgeProperties;
import com.czkuo.rdf88701.infra.event.model.card.CardReadEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 監聽 CardReadEvent → 發送 S010(Card Number Check)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CardToMqttBridge {

    private final MqttCommandService mqtt;
    private final MqttCardBridgeProperties props;

    /** 去抖：記錄每個 alias 最近一次觸發時間與卡號 */
    private final Map<String, LastHit> lastByAlias = new ConcurrentHashMap<>();

    private static final Map<String, Meta> ALIAS_META = Map.of(
            "card1", new Meta("拆併區",   "拆併區_打帶2"),
            "card2", new Meta("拆併區",   "拆併區_維修門"),
            "card3", new Meta("拆併區",  "拆併區_打帶3"),
            "card4", new Meta("拆併區",  "拆併區_打帶1"),
            "card5", new Meta("拆併區", "拆併區_貼標"),
            "card6", new Meta("WIP", "Crane維修側"),
            "card7", new Meta("WIP", "Crane操作側")
    );

    @EventListener
    public void onCard(CardReadEvent ev) {
        if (!props.isEnabled()) return;

        final String alias = ev.getAlias();
        final String card = ev.getCardId();
        final long now = System.currentTimeMillis();

        // 去抖（同一 reader 在 debounceMs 內重複卡號則略過）
        long debounce = Math.max(0, props.getDebounceMs());
        if (debounce > 0) {
            LastHit last = lastByAlias.get(alias);
            if (last != null && last.cardId.equals(card) && (now - last.ts) < debounce) {
                //log.debug("[Card→S010] {} 忽略重複卡號 {}（{}ms 內）", alias, card, debounce);
                return;
            }
            lastByAlias.put(alias, new LastHit(card, now));
        }

        // 目標系統：alias 覆蓋 > 預設
        String target = props.getAliasTarget().getOrDefault(alias, props.getTargetSystem());

        // 依 alias 取固定的 DEVICE_NAME / SAFE_DOOR_NAME；若無對照，fallback
        Meta meta = ALIAS_META.getOrDefault(alias, new Meta("WIP", alias));

        MqttSendResult result = mqtt.sendS010(target, card, meta.deviceName, meta.safeDoorName);
        if (result.isSuccess()) {
            log.info("[Card→S010] alias={} card={} → {} OK tid={}", alias, card, target, result.getTid());
        } else {
            log.warn("[Card→S010] alias={} card={} → {} FAIL: {} tid={}",
                    alias, card, target, result.getMessage(), result.getTid());

        }
    }

    private record LastHit(String cardId, long ts) {}

    private record Meta(String deviceName, String safeDoorName) {}
}
