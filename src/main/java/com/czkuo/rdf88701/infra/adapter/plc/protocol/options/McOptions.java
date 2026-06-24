package com.czkuo.rdf88701.infra.adapter.plc.protocol.options;

import lombok.Data;

/**
 * MC 協議專屬設定
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
public class McOptions {

    /**
     * 三菱 PLC 系列（例如：IQ_R、QnA 等）
     * 對應 application.yml 中的 `series`
     */
    private String series;

    /**
     * 連線 timeout（毫秒）
     * 對應 application.yml 中的 `connect-timeout`
     */
    private Integer connectTimeout;

    /**
     * 接收 timeout（毫秒）
     * 對應 application.yml 中的 `receive-timeout`
     */
    private Integer receiveTimeout;

    /**
     * 總重試時間上限（毫秒）
     * 對應 application.yml 中的 `overall-timeout-ms`
     */
    private Long overallTimeoutMs;

    /**
     * 每個 port 最大重試次數
     * 對應 application.yml 中的 `max-retry-per-port`
     */
    private Integer maxRetryPerPort;

    /**
     * 每次重試的初始 backoff 時間（毫秒）
     * 對應 application.yml 中的 `base-backoff-ms`
     */
    private Long baseBackoffMs;

    /**
     * 熔斷冷卻時間（毫秒），在此期間暫停對該 port 嘗試連線
     * 對應 application.yml 中的 `circuit-break-ms`
     */
    private Long circuitBreakMs;

    /**
     * 是否在 ping 失敗時仍強制嘗試 connect（預設 false）
     * 對應 application.yml 中的 `force-connect-if-ping-fails`
     */
    private Boolean forceConnectIfPingFails;
}
