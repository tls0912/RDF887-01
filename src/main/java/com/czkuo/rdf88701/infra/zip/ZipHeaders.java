package com.czkuo.rdf88701.infra.zip;

import com.czkuo.rdf88701.domain.dto.zip.common.Header;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

public final class ZipHeaders {
    private ZipHeaders() {}

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    public static Header of(String eventName, String direction, String sender) {
        Header h = new Header();
        h.setEventName(eventName);
        h.setDirection(direction);
        h.setSender(sender);
        h.setSendTime(LocalDateTime.now().format(FMT));
        return h;
    }
}
