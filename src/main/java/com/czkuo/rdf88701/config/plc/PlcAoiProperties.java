package com.czkuo.rdf88701.config.plc;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 對應 plc-aoi.yaml 中的設定。
 * 用於定義 AOI 裝置的記憶體對應區（B 區 / W 區 的 read/write 區段）。
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
@Component
@ConfigurationProperties(prefix = "plc-aoi")
public class PlcAoiProperties {

    /** AOI 裝置的配置（支援多台） */
    private List<Aoi> devices = new ArrayList<>();

    @Data
    public static class Aoi {
        private int id;                      // AOI 裝置 ID
        private String name;                 // 顯示名稱，例如 "AOI#1"
        private String plcDeviceName;        // 對應的 PLC adapter 名稱，例如 "PLC-Packer"
        private List<DeviceArea> readAreas = new ArrayList<>();   // PLC → PC 讀取區段
        private List<DeviceArea> writeAreas = new ArrayList<>();  // PC → PLC 寫入區段
    }
}
