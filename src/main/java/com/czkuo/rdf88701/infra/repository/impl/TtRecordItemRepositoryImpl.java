package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.TtRecordItemRepository;
import com.czkuo.rdf88701.infra.entity.TtRecordItem;
import com.czkuo.rdf88701.infra.mapper.TtRecordItemMapper;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
public class TtRecordItemRepositoryImpl implements TtRecordItemRepository {

    private static final int DEFAULT_BATCH_SIZE = 200; // 可視 DB/網路調整

    private final TtRecordItemMapper ttRecordItemMapper;

    public TtRecordItemRepositoryImpl(TtRecordItemMapper ttRecordItemMapper) {
        this.ttRecordItemMapper = ttRecordItemMapper;
    }

    @Override
    public Optional<TtRecordItem> findById(Long id) {
        return Optional.ofNullable(ttRecordItemMapper.selectById(id));
    }

    @Override
    public boolean save(TtRecordItem entity) {
        return ttRecordItemMapper.insert(entity) > 0;
    }

    @Override
    public boolean saveBatch(List<TtRecordItem> items) {
        if (items == null || items.isEmpty()) {
            return true;
        }

        // 分批 insert，避免一次太大
        int total = items.size();
        for (int from = 0; from < total; from += DEFAULT_BATCH_SIZE) {
            int to = Math.min(from + DEFAULT_BATCH_SIZE, total);
            List<TtRecordItem> sub = items.subList(from, to);
            for (TtRecordItem it : sub) {
                ttRecordItemMapper.insert(it);
            }
        }
        return true;
    }

    @Override
    public boolean update(TtRecordItem entity) {
        return ttRecordItemMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return ttRecordItemMapper.deleteById(id) > 0;
    }

    @Override
    public List<TtRecordItem> findAll() {
        return ttRecordItemMapper.selectList(new QueryWrapper<>());
    }

    @Override
    public List<TtRecordItem> findByRecordId(Long recordId) {
        if (recordId == null) {
            return Collections.emptyList();
        }
        return ttRecordItemMapper.selectList(
                new QueryWrapper<TtRecordItem>()
                        .eq("record_id", recordId)
                        .orderByAsc("step_no")
        );
    }
}
