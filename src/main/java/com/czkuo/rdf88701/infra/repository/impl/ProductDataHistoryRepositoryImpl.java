package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.ProductDataHistoryRepository;
import com.czkuo.rdf88701.infra.entity.ProductDataHistory;
import com.czkuo.rdf88701.infra.mapper.ProductDataHistoryMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Repository
public class ProductDataHistoryRepositoryImpl implements ProductDataHistoryRepository {

    private final ProductDataHistoryMapper productDataHistoryMapper;

    public ProductDataHistoryRepositoryImpl(ProductDataHistoryMapper productDataHistoryMapper) {
        this.productDataHistoryMapper = productDataHistoryMapper;
    }

    @Override
    public Optional<ProductDataHistory> findById(Long id) {
        return Optional.ofNullable(productDataHistoryMapper.selectById(id));
    }

    @Override
    public boolean save(ProductDataHistory entity) {
        return productDataHistoryMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(ProductDataHistory entity) {
        return productDataHistoryMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return productDataHistoryMapper.deleteById(id) > 0;
    }

    @Override
    public List<ProductDataHistory> findAll() {
        return productDataHistoryMapper.selectList(new QueryWrapper<>());
    }
}
