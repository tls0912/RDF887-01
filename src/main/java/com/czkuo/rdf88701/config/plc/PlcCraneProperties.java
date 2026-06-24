package com.czkuo.rdf88701.config.plc;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 對應 plc-crane.yml 中的設定。
 * 用於定義 Crane 的記憶體對應區（B 區 / W 區 的 read/write 區段）。
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
@Component
@ConfigurationProperties(prefix = "plc-crane")
public class PlcCraneProperties {

    /** Crane 裝置的配置（目前僅支援一隻） */
    private List<PlcCraneProperties.Crane> cranes = new ArrayList<>();

    @Data
    public static class Crane {
        private int id;                 // Crane 編號（通常為 1）
        private String name;            // 顯示名稱，例如 "Crane#1"
        private String plcDeviceName;   // 對應的 PLC adapter 名稱，如 "PLC-MAIN"
        private List<DeviceArea> readAreas = new ArrayList<>();   // PLC → PC 讀取區段
        private List<DeviceArea> writeAreas = new ArrayList<>();  // PC → PLC 寫入區段
    }
}
