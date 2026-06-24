package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.GripperRepository;
import com.czkuo.rdf88701.infra.entity.Gripper;
import com.czkuo.rdf88701.infra.mapper.GripperMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Gripper 資料存取實作
 * - 提供 Gripper 裝置的查詢、新增、更新、刪除等功能
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Repository
public class GripperRepositoryImpl implements GripperRepository {

    private final GripperMapper gripperMapper;

    public GripperRepositoryImpl(GripperMapper gripperMapper) {
        this.gripperMapper = gripperMapper;
    }

    @Override
    public Optional<Gripper> findById(Long id) {
        return Optional.ofNullable(gripperMapper.selectById(id));
    }

    @Override
    public boolean save(Gripper entity) {
        return gripperMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(Gripper entity) {
        return gripperMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return gripperMapper.deleteById(id) > 0;
    }

    @Override
    public List<Gripper> findAll() {
        return gripperMapper.selectList(new QueryWrapper<>());
    }
}
