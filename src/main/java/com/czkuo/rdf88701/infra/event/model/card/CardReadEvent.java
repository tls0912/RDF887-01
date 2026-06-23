package com.czkuo.rdf88701.infra.event.model.card;

import org.springframework.context.ApplicationEvent;

public class CardReadEvent extends ApplicationEvent {
    private final String alias;     // 例如 card1
    private final String cardId;    // 解析後的卡號（依設定可能 uppercase / reverse）
    private final byte[] raw;       // 原始 payload（不含分隔符）

    public CardReadEvent(Object source, String alias, String cardId, byte[] raw) {
        super(source);
        this.alias = alias;
        this.cardId = cardId;
        this.raw = raw;
    }

    public String getAlias() { return alias; }
    public String getCardId() { return cardId; }
    public byte[] getRaw() { return raw; }
}
