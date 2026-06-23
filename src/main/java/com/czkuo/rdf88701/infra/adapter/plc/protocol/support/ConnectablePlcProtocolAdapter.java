package com.czkuo.rdf88701.infra.adapter.plc.protocol.support;

import com.czkuo.rdf88701.infra.adapter.plc.protocol.PlcProtocolAdapter;

/**
 * 可支援連線控制的協議轉接器介面。
 * 用於需要手動 connect/disconnect/isConnected 的協議實作。
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
