package com.czkuo.rdf88701.config.plc;

import com.czkuo.rdf88701.common.enums.ConnectionMode;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 對應 application.yml 中的 plc.* 配置。
 * 本設定用於管理 PLC 裝置清單與通訊參數。
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
@Component
@ConfigurationProperties(prefix = "plc")
public class PlcProperties {

    /**
     * 健康檢查排程的執行間隔（毫秒）。
     * 系統會定期針對啟用的裝置執行連線檢查與心跳驗證。
     */
    private Long healthCheckInterval = 10000L;

    /**
     * 是否啟用 PLC 輪詢排程（全域開關）。
     * 若為 false，所有裝置皆不會參與輪詢，即使裝置設定為 default-polling-enabled=true。
     */
    private boolean pollingEnabled = true;

    /**
     * 輪詢排程的執行間隔（毫秒）。
     * 此為全域輪詢頻率，所有裝置會依照此頻率進行資料讀寫。
     */
    private long pollInterval = 20L;

    /**
     * 所有 PLC 裝置的設定清單。
     */
    private List<Device> devices;

    @Data
    public static class Device {

        /**
         * 是否啟用該裝置。
         * 若為 false，系統啟動時將略過此裝置的載入與通訊初始化。
         */
        private boolean enabled = false;

        /**
         * 是否允許外部控制該裝置（例如透過 UI、API 指令）。
         * 僅當 enabled=true 時此設定才有效；若為 false，外部不可啟停該裝置連線或輪詢。
         */
        private boolean externalControlAllowed = true;

        /**
         * 裝置的預設連線控制模式。
         * OFF：不建立連線、也不自動補連；
         * AUTO：系統會自動連線與補連（預設）；
         * MANUAL：僅允許外部操作建立連線。
         */
        private ConnectionMode connectionMode = ConnectionMode.AUTO;

        /**
         * 啟動時是否自動加入輪詢機制。
         * 若為 true，該裝置會被加入定期讀寫排程中；否則僅保留連線但不進行資料讀取。
         */
        private boolean defaultPollingEnabled = true;

        /**
         * 裝置名稱（用於唯一識別裝置，例如 "PLC-Main"）。
         */
        private String name;

        /**
         * PLC 裝置的 IP 位址。
         */
        private String ip;

        /**
         * 支援多個 port 備援，依照順序嘗試連線。
         */
        private List<Integer> ports;

        /**
         * PLC 通訊埠號（依通訊協定而定，例如 MC 為 5000+）。
         * 向下相容用：若 YAML 設定為 port（單一值），可於初始化後轉為 ports=[port]。
         */
        private Integer port;

        /**
         * 通訊協議類型（例如：mc、modbus、opcua）。
         * 系統將根據此欄位決定對應的協議轉接器。
         */
        private String protocol;

        /**
         * 健康檢查使用的心跳位址（例如 MC 協議的 M0）。
         * 系統會定期讀取此位址以確認 PLC 是否存活。
         */
        private String heartbeatAddress = "M0";

        /**
         * PLC 寫入 → PC 讀取的資料區段清單。
         * 系統會根據這些區段進行資料讀取。
         */
        private List<AddressRange> readAreas;

        /**
         * PC 寫入 → PLC 讀取的資料區段清單。
         * 系統會根據這些區段進行資料寫入。
         */
        private List<AddressRange> writeAreas;

        /**
         * 協議專屬選項參數（如系列型號、逾時設定等）。
         * 以 Map 儲存，自動注入協議工廠時使用。
         * <p>
         * 可支援進階容錯與調控參數：
         * - series: IQ_R、Q 等（三菱系列）
         * - connect-timeout: int 單位毫秒（連線逾時）
         * - receive-timeout: int 單位毫秒（接收逾時）
         * - force-connect-if-ping-fails: boolean
         *     是否允許在 ping（ICMP）失敗時仍強制 connect()
         *     → 適用於禁 ping 的場域或防火牆阻擋 ICMP 的情況
         * - max-retry-per-port: int
         *     每個 port 最多失敗幾次後進入熔斷狀態（預設為 3）
         * - circuit-break-ms: int
         *     熔斷期間（毫秒），port 失敗後暫時不再嘗試的鎖定時長（預設為 30000）
         * - base-backoff-ms: int
         *     每次重試間的初始延遲（毫秒），將以 2^n 倍增（預設為 500）
         * - overall-timeout-ms: int
         *     每輪自動重連的最大容忍耗時（毫秒），超過此值整體重連終止（預設為 30000）
         */
        private Map<String, Object> options = new HashMap<>();
    }

    @Data
    public static class AddressRange {

        /**
         * 區段類型（例如 B、W 等）。
         * 代表該區段屬於哪一種位址範圍。
         */
        private String areaType;

        /**
         * 區段起始位址（如 B0、W100 等起始點）。
         */
        private int start;

        /**
         * 區段長度（位元/字元數）。
         */
        private int length;
    }
}
