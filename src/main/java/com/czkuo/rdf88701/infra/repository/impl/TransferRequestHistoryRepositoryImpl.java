package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.TransferRequestHistoryRepository;
import com.czkuo.rdf88701.infra.entity.TransferRequestHistory;
import com.czkuo.rdf88701.infra.mapper.TransferRequestHistoryMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class TransferRequestHistoryRepositoryImpl implements TransferRequestHistoryRepository {

    private final TransferRequestHistoryMapper transferRequestHistoryMapper;

    public TransferRequestHistoryRepositoryImpl(TransferRequestHistoryMapper transferRequestHistoryMapper) {
        this.transferRequestHistoryMapper = transferRequestHistoryMapper;
    }

    @Override
    public Optional<TransferRequestHistory> findById(Long id) {
        return Optional.ofNullable(transferRequestHistoryMapper.selectById(id));
    }

    @Override
    public boolean save(TransferRequestHistory entity) {
        return transferRequestHistoryMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(TransferRequestHistory entity) {
        return transferRequestHistoryMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return transferRequestHistoryMapper.deleteById(id) > 0;
    }

    @Override
    public List<TransferRequestHistory> findAll() {
        return transferRequestHistoryMapper.selectList(new QueryWrapper<>());
    }
}
