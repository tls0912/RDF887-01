package com.czkuo.rdf88701.config.plc;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 對應 plc-safety.yml 的設定。
 *
 * 結構說明：
 * plc-safety:
 *   devices:                 # 一個或多個安全點位的「Bank」
 *     - id: 1
 *       name: "Safety-Sensor-Bank"
 *       plcDeviceName: "PLC-Safety"
 *       read-areas:          # 從 PLC 讀的區段（通常是 W 區，一次讀多個 word）
 *         - type: W
 *           address: 0x1040  # 起始位址（支援 0x 前綴的 16 進位）
 *           length: 8        # 連續 word 數
 *       points:              # 具名的安全點位清單（只列有意義的 bit）
 *         - addr: "W1040.0"
 *           type: "DOOR"     # DOOR / LIGHT_CURTAIN / EMO / OTHER
 *           name: "Crane操作側"
 *           remark: ""
 *           enabled: true
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
@Component
@ConfigurationProperties(prefix = "plc-safety")
public class PlcSafetyProperties {

    /** 所有安全感測 Bank 的配置清單（預設為空避免 NPE） */
    private List<Device> devices = new ArrayList<>();

    @Data
    public static class Device {
        private int id;                    // 裝置群組編號
        private String name;               // 顯示名稱（例：Safety-Sensor-Bank）
        private String plcDeviceName;      // 對應 PlcSafeAccess 的 deviceName
        private List<DeviceArea> readAreas = new ArrayList<>(); // PLC→PC 讀取區段
        private List<Point> points = new ArrayList<>();         // 具名的安全點位
    }

    /**
     * 單一安全點位描述：對應到某個 Wxxxx.bit
     */
    @Data
    public static class Point {
        /**
         * 位址字串，強烈建議固定格式 "W1040.0"（bit 用 0~F 的十六進位大寫亦可）
         * 例：W1040.0、W1042.A
         */
        private String addr;

        /**
         * 安全型別：DOOR / LIGHT_CURTAIN / EMO / OTHER
         * 若你要更嚴謹可改 Enum（如下列註解），這裡先用字串跟 YAML 對齊。
         */
        private String type;

        /** 顯示名稱（中文名稱） */
        private String name;

        /** 備註 */
        private String remark;

        /** 是否啟用（預設 true） */
        private boolean enabled = true;
    }
}
