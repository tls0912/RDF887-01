package com.czkuo.rdf88701.config.plc;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 對應 plc-labeling.yml 中的設定。
 * 用於定義 Labeling 裝置的記憶體對應區（B/W 區的 read/write 區段）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "plc-labeling")
public class PlcLabelingProperties {

    /** Labeling 裝置的配置（支援多台） */
    private List<Labeling> devices = new ArrayList<>();

    @Data
    public static class Labeling {
        private int id;                      // Labeling 裝置 ID
        private String name;                 // 顯示名稱，例如 "Labeling#1"
        private String plcDeviceName;        // 對應的 PLC adapter 名稱，例如 "PLC-Packer"
        private List<DeviceArea> readAreas = new ArrayList<>();   // PLC → PC 讀取區段
        private List<DeviceArea> writeAreas = new ArrayList<>();  // PC → PLC 寫入區段
    }
}
