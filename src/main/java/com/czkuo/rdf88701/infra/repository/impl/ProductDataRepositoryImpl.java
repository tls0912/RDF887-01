package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.ProductDataRepository;
import com.czkuo.rdf88701.infra.entity.ProductData;
import com.czkuo.rdf88701.infra.mapper.ProductDataMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Repository
public class ProductDataRepositoryImpl implements ProductDataRepository {

    private final ProductDataMapper productDataMapper;

    public ProductDataRepositoryImpl(ProductDataMapper productDataMapper) {
        this.productDataMapper = productDataMapper;
    }

    @Override
    public Optional<ProductData> findById(Long id) {
        return Optional.ofNullable(productDataMapper.selectById(id));
    }

    @Override
    public boolean save(ProductData entity) {
        return productDataMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(ProductData entity) {
        return productDataMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return productDataMapper.deleteById(id) > 0;
    }

    @Override
    public List<ProductData> findAll() {
        return productDataMapper.selectList(new QueryWrapper<>());
    }
}
