package com.czkuo.rdf88701.config.plc;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 對應 plc-transfer.yml 中的設定。
 * 用於定義每組 Transfer 的記憶體對應區（B 區 / W 區 的 read/write 區段）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "plc-transfer")
public class PlcTransferProperties {

    /** 所有 Transfer 的配置清單（預設為空列表） */
    private List<Transfer> transfers = new ArrayList<>();

    @Data
    public static class Transfer {
        private int id;                   // Transfer 編號（1~9）
        private String name;              // 顯示名稱，例如 "Transfer#1"
        private String plcDeviceName;     // 對應的 PLC adapter 名稱，例如 "PLC-Packer"
        private List<DeviceArea> readAreas = new ArrayList<>();   // PLC → PC 讀取區段
        private List<DeviceArea> writeAreas = new ArrayList<>();  // PC → PLC 寫入區段
    }
}
