package com.czkuo.rdf88701.infra.repository.impl;

import com.czkuo.rdf88701.domain.plc.state.infrared.InfraredHandshakeContext;
import com.czkuo.rdf88701.domain.repository.InfraredHandshakeContextRepository;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory 實作：Infrared 握手上下文快取
 * - 僅在記憶體中保存，不會持久化
 * - 任務重啟後 context 會重新建立
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Repository
public class InMemoryInfraredHandshakeContextRepository implements InfraredHandshakeContextRepository {

    /** 任務 ID 對應的握手上下文 */
    private final Map<Long, InfraredHandshakeContext> contextMap = new ConcurrentHashMap<>();

    /**
     * 取得或初始化握手上下文
     *
     * @param taskId 任務 ID
     * @param sensorName 紅外線裝置名稱（如 Infrared#1）
     * @return 握手上下文實體
     */
    @Override
    public InfraredHandshakeContext getOrInit(Long taskId, String sensorName) {
        return contextMap.computeIfAbsent(taskId, id -> InfraredHandshakeContext.init(taskId, sensorName));
    }

    /**
     * 儲存上下文
     */
    @Override
    public void save(InfraredHandshakeContext context) {
        contextMap.put(context.getTaskId(), context);
    }

    /**
     * 清除指定任務的上下文
     */
    @Override
    public void clear(Long taskId) {
        contextMap.remove(taskId);
    }
}
