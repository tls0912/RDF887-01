package com.czkuo.rdf88701.infra.repository.impl;


import com.czkuo.rdf88701.domain.plc.state.transfer.TransferHandshakeContext;
import com.czkuo.rdf88701.domain.repository.TransferHandshakeContextRepository;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory 實作：Transfer 握手上下文快取
 * - 僅在記憶體中保存，不會持久化
 * - 任務重啟後 context 會重新建立
 */
@Repository
public class InMemoryTransferHandshakeContextRepository implements TransferHandshakeContextRepository {

    /** 任務 ID 對應的握手上下文 */
    private final Map<Long, TransferHandshakeContext> contextMap = new ConcurrentHashMap<>();

    /**
     * 取得或初始化握手上下文
     *
     * @param taskId 任務 ID
     * @param transferName 裝置名稱（如 Transfer#1）
     * @return 握手上下文實體
     */
    @Override
    public TransferHandshakeContext getOrInit(Long taskId, String transferName) {
        return contextMap.computeIfAbsent(taskId, id -> TransferHandshakeContext.init(taskId, transferName));
    }

    /**
     * 儲存上下文
     */
    @Override
    public void save(TransferHandshakeContext context) {
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
