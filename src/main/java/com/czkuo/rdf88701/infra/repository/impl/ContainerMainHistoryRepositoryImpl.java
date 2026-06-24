package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.ContainerMainHistoryRepository;
import com.czkuo.rdf88701.infra.entity.ContainerMainHistory;
import com.czkuo.rdf88701.infra.mapper.ContainerMainHistoryMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Repository
public class ContainerMainHistoryRepositoryImpl implements ContainerMainHistoryRepository {

    private final ContainerMainHistoryMapper containerMainHistoryMapper;

    public ContainerMainHistoryRepositoryImpl(ContainerMainHistoryMapper containerMainHistoryMapper) {
        this.containerMainHistoryMapper = containerMainHistoryMapper;
    }

    @Override
    public Optional<ContainerMainHistory> findById(Long id) {
        return Optional.ofNullable(containerMainHistoryMapper.selectById(id));
    }

    @Override
    public boolean save(ContainerMainHistory entity) {
        return containerMainHistoryMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(ContainerMainHistory entity) {
        return containerMainHistoryMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return containerMainHistoryMapper.deleteById(id) > 0;
    }

    @Override
    public List<ContainerMainHistory> findAll() {
        return containerMainHistoryMapper.selectList(new QueryWrapper<>());
    }

    @Override
    public List<ContainerMainHistory> findByContainerMainId(Long containerMainId) {
        QueryWrapper<ContainerMainHistory> wrapper = new QueryWrapper<>();
        wrapper.eq("container_main_id", containerMainId).orderByDesc("change_time");
        return containerMainHistoryMapper.selectList(wrapper);
    }
}
