package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.GripperStatusTransitionRepository;
import com.czkuo.rdf88701.infra.entity.GripperStatusTransition;
import com.czkuo.rdf88701.infra.mapper.GripperStatusTransitionMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Repository
public class GripperStatusTransitionRepositoryImpl implements GripperStatusTransitionRepository {

    private final GripperStatusTransitionMapper gripperStatusTransitionMapper;

    public GripperStatusTransitionRepositoryImpl(GripperStatusTransitionMapper gripperStatusTransitionMapper) {
        this.gripperStatusTransitionMapper = gripperStatusTransitionMapper;
    }

    @Override
    public Optional<GripperStatusTransition> findById(Long id) {
        return Optional.ofNullable(gripperStatusTransitionMapper.selectById(id));
    }

    @Override
    public boolean save(GripperStatusTransition entity) {
        return gripperStatusTransitionMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(GripperStatusTransition entity) {
        return gripperStatusTransitionMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return gripperStatusTransitionMapper.deleteById(id) > 0;
    }

    @Override
    public List<GripperStatusTransition> findAll() {
        return gripperStatusTransitionMapper.selectList(new QueryWrapper<>());
    }
}
