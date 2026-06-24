package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.ProductMainHistoryRepository;
import com.czkuo.rdf88701.infra.entity.ProductMainHistory;
import com.czkuo.rdf88701.infra.mapper.ProductMainHistoryMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Repository
public class ProductMainHistoryRepositoryImpl implements ProductMainHistoryRepository {

    private final ProductMainHistoryMapper productMainHistoryMapper;

    public ProductMainHistoryRepositoryImpl(ProductMainHistoryMapper productMainHistoryMapper) {
        this.productMainHistoryMapper = productMainHistoryMapper;
    }

    @Override
    public Optional<ProductMainHistory> findById(Long id) {
        return Optional.ofNullable(productMainHistoryMapper.selectById(id));
    }

    @Override
    public boolean save(ProductMainHistory entity) {
        return productMainHistoryMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(ProductMainHistory entity) {
        return productMainHistoryMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return productMainHistoryMapper.deleteById(id) > 0;
    }

    @Override
    public List<ProductMainHistory> findAll() {
        return productMainHistoryMapper.selectList(new QueryWrapper<>());
    }
}
