package com.czkuo.rdf88701.infra.event.model.serial;

import org.springframework.context.ApplicationEvent;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * SerialFrameEvent
 * ------------------------------------------------------------
 * 由 SerialPortManager 在「切出完整一帧」時發佈的事件。
 *
 * - LINE 模式：已去除行分隔符（例如 \n 或 \r\n）
 * - STX_ETX 模式：已去除 STX/ETX 包裝位元
 * - FIXED 模式：payload 即固定長度資料
 *
 * 欄位：
 *  - alias   ：對應 application.yml 的 serial.ports[*].alias（例如 "card1"）
 *  - payload ：完整一帧位元組（不含分隔符/標記）
 *  - createdAt：事件建立時間（方便追蹤/除錯）
 *
 * 工具方法：
 *  - payloadHex()   ：把 payload 轉為 HEX 字串（大寫、無空白）
 *  - payloadAscii() ：把 payload 以 US-ASCII 直譯成字串（常用於條碼/卡號）
 *  - length()       ：payload 長度
 */
public class SerialFrameEvent extends ApplicationEvent {

    private final String alias;
    private final byte[] payload;
    private final Instant createdAt = Instant.now();

    public SerialFrameEvent(Object source, String alias, byte[] payload) {
        super(source);
        this.alias = alias;
        this.payload = payload;
    }

    public String getAlias() {
        return alias;
    }

    public byte[] getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public int length() {
        return payload == null ? 0 : payload.length;
    }

    /** 將 payload 轉為 HEX 字串（例如 "3031320A"） */
    public String payloadHex() {
        if (payload == null) return "";
        StringBuilder sb = new StringBuilder(payload.length * 2);
        for (byte b : payload) sb.append(String.format("%02X", b));
        return sb.toString();
    }

    /** 以 US-ASCII 直譯 payload（常見於讀卡機/條碼機） */
    public String payloadAscii() {
        if (payload == null) return "";
        return new String(payload, StandardCharsets.US_ASCII);
    }
}
