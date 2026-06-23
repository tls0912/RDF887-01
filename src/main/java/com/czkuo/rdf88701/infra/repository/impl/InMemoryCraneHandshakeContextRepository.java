package com.czkuo.rdf88701.infra.repository.impl;

import com.czkuo.rdf88701.domain.plc.state.crane.CraneHandshakeContext;
import com.czkuo.rdf88701.domain.repository.CraneHandshakeContextRepository;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory 實作：Crane 握手上下文快取
 * - 僅在記憶體中保存，不會持久化
 * - 任務重啟後 context 會重新建立
 */
@Repository
public class InMemoryCraneHandshakeContextRepository implements CraneHandshakeContextRepository {

    private final Map<Long, CraneHandshakeContext> contextMap = new ConcurrentHashMap<>();

    @Override
    public CraneHandshakeContext getOrInit(Long taskId, String craneName) {
        return contextMap.computeIfAbsent(taskId, id -> CraneHandshakeContext.init(taskId, craneName));
    }

    @Override
    public void save(CraneHandshakeContext context) {
        contextMap.put(context.getTaskId(), context);
    }

    @Override
    public void clear(Long taskId) {
        contextMap.remove(taskId);
    }
}
