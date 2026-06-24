package com.czkuo.rdf88701.config.serial;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Data
@Component
@ConfigurationProperties(prefix = "card-reader")
public class CardReaderProperties {
    private boolean enabled = true;
    /** 解析後轉大寫 */
    private boolean uppercase = true;
    /** 若 LINE delimiter 是 CRLF，是否去掉殘留 CR */
    private boolean trimCr = true;
    /** 預設是否做 Reverse（兩兩倒序） */
    private boolean defaultReverse = false;
    /** 要監聽的讀卡機（以 serial.alias 為 key） */
    private List<Reader> readers = new ArrayList<>();

    @Data
    public static class Reader {
        /** 對應 serial.ports[*].alias，例如 "card1" */
        private String alias;
        /** 是否採用 Reverse 處理（覆蓋 defaultReverse） */
        private Boolean reverse;
    }
}
