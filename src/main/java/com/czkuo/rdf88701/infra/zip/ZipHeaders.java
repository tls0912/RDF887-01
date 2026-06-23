package com.czkuo.rdf88701.infra.zip;

import com.czkuo.rdf88701.domain.dto.zip.common.Header;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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
