package com.czkuo.rdf88701.config.zip;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Data
@Component
@ConfigurationProperties(prefix = "zipstocker")
public class ZipStockerProperties {

    /** 多目標設定（key 建議小寫：zipa/zipb） */
    private Map<String, Target> targets = new HashMap<>();

    /** 預設目標（ZIPA/ZIPB，不分大小寫） */
    private String defaultTarget = "ZIPA";

    /** 舊相容：單一 base-url（可不填） */
    private String baseUrl;

    @Data
    public static class Target {
        private String baseUrl;
    }
}