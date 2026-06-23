package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.domain.plc.state.infrared.InfraredHandshakeContext;

/**
 * Infrared 任務握手上下文快取/儲存介面
 * 可實作為記憶體、Redis 或 DB
 */
public interface InfraredHandshakeContextRepository {

    /**
     * 取得上下文，若不存在則初始化
     * @param taskId 任務 ID
     * @param sensorName Infrared 裝置 Name
     * @return 握手上下文
     */
    InfraredHandshakeContext getOrInit(Long taskId, String sensorName);

    /**
     * 儲存上下文
     */
    void save(InfraredHandshakeContext context);

    /**
     * 清除上下文
     */
    void clear(Long taskId);
}
