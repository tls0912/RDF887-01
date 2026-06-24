package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.ProductMainRepository;
import com.czkuo.rdf88701.infra.entity.ProductMain;
import com.czkuo.rdf88701.infra.mapper.ProductMainMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Repository
public class ProductMainRepositoryImpl implements ProductMainRepository {

    private final ProductMainMapper productMainMapper;

    public ProductMainRepositoryImpl(ProductMainMapper productMainMapper) {
        this.productMainMapper = productMainMapper;
    }

    @Override
    public Optional<ProductMain> findById(Long id) {
        return Optional.ofNullable(productMainMapper.selectById(id));
    }

    @Override
    public boolean save(ProductMain entity) {
        return productMainMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(ProductMain entity) {
        return productMainMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return productMainMapper.deleteById(id) > 0;
    }

    @Override
    public List<ProductMain> findAll() {
        return productMainMapper.selectList(new QueryWrapper<>());
    }
}
