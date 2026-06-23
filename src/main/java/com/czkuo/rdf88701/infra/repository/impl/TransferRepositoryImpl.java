package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.TransferRepository;
import com.czkuo.rdf88701.infra.entity.Transfer;
import com.czkuo.rdf88701.infra.mapper.TransferMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class TransferRepositoryImpl implements TransferRepository {

    private final TransferMapper transferMapper;

    public TransferRepositoryImpl(TransferMapper transferMapper) {
        this.transferMapper = transferMapper;
    }

    @Override
    public Optional<Transfer> findById(Long id) {
        return Optional.ofNullable(transferMapper.selectById(id));
    }

    @Override
    public boolean save(Transfer entity) {
        return transferMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(Transfer entity) {
        return transferMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return transferMapper.deleteById(id) > 0;
    }

    @Override
    public List<Transfer> findAll() {
        return transferMapper.selectList(new QueryWrapper<>());
    }
}
