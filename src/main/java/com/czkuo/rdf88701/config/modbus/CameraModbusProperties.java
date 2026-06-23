package com.czkuo.rdf88701.config.modbus;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Camera / Modbus 設定
 *
 * <p>重點：
 * <ul>
 *   <li><b>referenceBase</b>：地址基準（通常為 40000；若設備採 1-based，改 40001）。
 *       任何 4xxxx 參考位址要換成方法地址（PDU 起點）時：<br/>
 *       <code>methodAddress = ref400xx - referenceBase</code></li>
 *   <li><b>mapping</b>：一機兩虛擬站（第一次 / 第二次各對應一個虛擬站）。</li>
 *   <li><b>debugLog</b>：是否列印 Modbus 收發報文（十六進位），方便現場除錯。</li>
 * </ul>
 *
 * <p>YAML 示例（請將 mapping 放在 camera.modbus 之下，虛擬站名稱含 # 要加引號）：<br/>
 * <pre>
 * camera:
 *   modbus:
 *     host: 192.168.0.100
 *     port: 502
 *     unitId: 1
 *     referenceBase: 40000
 *     triggerPulseMs: 120
 *     debugLog: true
 *     poll:
 *       enabled: true
 *       periodMs: 250
 *       autoTriggerSecond: true
 *     registers:
 *       cam1: { state: 40000, error: 40001, firstCount: 40002, secondCount: 40003, total: 40004, times: 40005 }
 *       cam2: { state: 40010, error: 40011, firstCount: 40012, secondCount: 40013, total: 40014, times: 40015 }
 *       cmd:  { c1First: 40020, c1Second: 40021, c2First: 40022, c2Second: 40023, resetAll: 40024 }
 *     mapping:
 *       CAM1:
 *         virtualSites:
 *           first:  "VIRTUAL#5"
 *           second: "VIRTUAL#6"
 *       CAM2:
 *         virtualSites:
 *           first:  "VIRTUAL#7"
 *           second: "VIRTUAL#8"
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "camera.modbus")
public class CameraModbusProperties {

    /** Modbus TCP 目標 IP（或主機名） */
    private String host = "192.168.0.100";

    /** Modbus TCP 端口（預設 502） */
    private int port = 502;

    /** 單位位址（Unit ID / Slave ID），單從站通常為 1 */
    private int unitId = 1;

    /**
     * 位址基準：40000 或 40001
     * <p>方法地址（PDU 起點）換算公式：methodAddress = ref400xx - referenceBase</p>
     * <p>若讀值不合理，從 40000 改試 40001。</p>
     */
    private int referenceBase = 40000;

    /** 觸發命令（寫 1）維持的毫秒數，之後會寫回 0（避免卡住） */
    private int triggerPulseMs = 120;

    /** 是否列印 Modbus 收發報文（十六進位），利於現場除錯 */
    private boolean debugLog = false;

    /** 輪詢設定 */
    private Poll poll = new Poll();

    /** 寄存器位址配置（文件參考位址 4xxxx） */
    private Registers registers = new Registers();

    /**
     * 一機兩虛擬站對應（第一次 / 第二次各一個站點）
     * <p>key 為設備代號（例如 "CAM1", "CAM2"）</p>
     */
    private Map<String, CameraBinding> mapping = new HashMap<>();

    // ========= 子類別 =========

    @Data
    public static class Poll {
        /** 是否啟用輪詢 */
        private boolean enabled = true;
        /** 輪詢間隔（毫秒） */
        private int periodMs = 250;
        /** 狀態=2（第一次完成，等待第二次）時是否自動觸發第二次 */
        private boolean autoTriggerSecond = true;
    }

    @Data
    public static class Registers {
        /** 相機 1 的只讀欄位（狀態、數量、次數） */
        private Cam cam1 = new Cam();
        /** 相機 2 的只讀欄位 */
        private Cam cam2 = new Cam();
        /** 命令寄存器（寫入觸發/重置） */
        private Cmd cmd = new Cmd();
    }

    @Data
    public static class Cam {
        /** 狀態（0..5） */
        private int state;
        /** 異常代碼（0 無、1 相機斷線…） */
        private int error;
        /** 第一次檢測 IC 數量 */
        private int firstCount;
        /** 第二次檢測 IC 數量 */
        private int secondCount;
        /** 檢測總 IC 數量（第二次完成後更新） */
        private int total;
        /** 檢測次數（做第一次或第二次都 +1） */
        private int times;
    }

    @Data
    public static class Cmd {
        /** 觸發相機1 第一次（脈衝：寫 1 → 等待 → 寫 0） */
        private int c1First;
        /** 觸發相機1 第二次 */
        private int c1Second;
        /** 觸發相機2 第一次 */
        private int c2First;
        /** 觸發相機2 第二次 */
        private int c2Second;
        /** 清除相機1/2 所有暫存器（具破壞性，慎用） */
        private int resetAll;
    }

    @Data
    public static class CameraBinding {
        /** 一機兩虛擬站（第一次 / 第二次） */
        private VirtualSites virtualSites = new VirtualSites();
    }

    @Data
    public static class VirtualSites {
        /** 第一次拍照對應的虛擬站點（例："VIRTUAL#5"；注意 # 要加引號） */
        private String first;
        /** 第二次拍照對應的虛擬站點（例："VIRTUAL#6"） */
        private String second;
    }
}
