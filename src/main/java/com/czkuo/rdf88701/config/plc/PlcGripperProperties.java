package com.czkuo.rdf88701.config.plc;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 對應 plc-gripper.yml 中的設定。
 * 用於定義每組 Gripper 的記憶體對應區（B 區 / W 區 的 read/write 區段）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "plc-gripper")
public class PlcGripperProperties {

    /** 所有 Gripper 的配置清單（預設為空列表，避免 NullPointerException） */
    private List<Gripper> grippers = new ArrayList<>();

    @Data
    public static class Gripper {
        private int id;                 // Gripper 編號（1~8）
        private String name;            // 顯示名稱，例如 "Gripper#1"
        private String plcDeviceName;   // 對應的 PLC adapter 名稱，如 "PLC-MAIN"
        private List<DeviceArea> readAreas = new ArrayList<>();   // PLC → PC 讀取區段（預設空列表）
        private List<DeviceArea> writeAreas = new ArrayList<>();  // PC → PLC 寫入區段（預設空列表）
    }
}
