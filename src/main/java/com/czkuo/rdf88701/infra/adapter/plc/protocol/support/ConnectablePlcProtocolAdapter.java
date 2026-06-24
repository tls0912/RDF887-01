package com.czkuo.rdf88701.infra.adapter.plc.protocol.support;

import com.czkuo.rdf88701.infra.adapter.plc.protocol.PlcProtocolAdapter;

/**
 * 支援手動連線控制的 PLC 協議 adapter。
 *
 * <p>在基本讀寫介面外，額外提供 connect、disconnect、isConnected，供
 * PlcClientManager 在初始化、failover 與執行前檢查實體連線。</p>
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public interface ConnectablePlcProtocolAdapter extends PlcProtocolAdapter {

    /**
     * 建立連線
     *
     * @return 是否連線成功
     */
    boolean connect();

    /**
     * 中斷連線
     */
    void disconnect();

    /**
     * 是否已連線中
     *
     * @return true 表示已建立連線
     */
    boolean isConnected();
}
