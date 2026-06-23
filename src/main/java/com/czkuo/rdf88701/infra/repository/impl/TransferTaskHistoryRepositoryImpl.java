package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.TransferTaskHistoryRepository;
import com.czkuo.rdf88701.infra.entity.TransferTaskHistory;
import com.czkuo.rdf88701.infra.mapper.TransferTaskHistoryMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class TransferTaskHistoryRepositoryImpl implements TransferTaskHistoryRepository {

    private final TransferTaskHistoryMapper transferTaskHistoryMapper;

    public TransferTaskHistoryRepositoryImpl(TransferTaskHistoryMapper transferTaskHistoryMapper) {
        this.transferTaskHistoryMapper = transferTaskHistoryMapper;
    }

    @Override
    public Optional<TransferTaskHistory> findById(Long id) {
        return Optional.ofNullable(transferTaskHistoryMapper.selectById(id));
    }

    @Override
    public boolean save(TransferTaskHistory entity) {
        return transferTaskHistoryMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(TransferTaskHistory entity) {
        return transferTaskHistoryMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return transferTaskHistoryMapper.deleteById(id) > 0;
    }

    @Override
    public List<TransferTaskHistory> findAll() {
        return transferTaskHistoryMapper.selectList(new QueryWrapper<>());
    }
}
