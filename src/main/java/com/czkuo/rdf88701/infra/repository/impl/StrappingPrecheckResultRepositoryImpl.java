package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.StrappingPrecheckResultRepository;
import com.czkuo.rdf88701.infra.entity.StrappingPrecheckResult;
import com.czkuo.rdf88701.infra.mapper.StrappingPrecheckResultMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class StrappingPrecheckResultRepositoryImpl implements StrappingPrecheckResultRepository {

    private final StrappingPrecheckResultMapper strappingPrecheckResultMapper;

    public StrappingPrecheckResultRepositoryImpl(StrappingPrecheckResultMapper strappingPrecheckResultMapper) {
        this.strappingPrecheckResultMapper = strappingPrecheckResultMapper;
    }

    /* ==================== 原本的 CRUD (by id) ==================== */

    @Override
    public Optional<StrappingPrecheckResult> findById(Long id) {
        return Optional.ofNullable(strappingPrecheckResultMapper.selectById(id));
    }

    @Override
    public boolean save(StrappingPrecheckResult entity) {
        return strappingPrecheckResultMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(StrappingPrecheckResult entity) {
        return strappingPrecheckResultMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return strappingPrecheckResultMapper.deleteById(id) > 0;
    }

    @Override
    public List<StrappingPrecheckResult> findAll() {
        return strappingPrecheckResultMapper.selectList(new QueryWrapper<>());
    }


    /* ==================== 新增的 (by tid) ==================== */

    @Override
    public Optional<StrappingPrecheckResult> findByTid(String tid) {
        return Optional.ofNullable(
                strappingPrecheckResultMapper.selectOne(
                        new QueryWrapper<StrappingPrecheckResult>().eq("tid", tid)
                )
        );
    }

    @Override
    public boolean saveOrUpdateByTid(StrappingPrecheckResult entity) {
        StrappingPrecheckResult existing = strappingPrecheckResultMapper.selectOne(
                new QueryWrapper<StrappingPrecheckResult>().eq("tid", entity.getTid())
        );
        if (existing != null) {
            // 更新（只覆蓋 result 與 resultMessage，其餘欄位可自行擴充）
            entity.setId(existing.getId()); // 確保 updateById 能找到
            return strappingPrecheckResultMapper.updateById(entity) > 0;
        } else {
            return strappingPrecheckResultMapper.insert(entity) > 0;
        }
    }

    @Override
    public boolean deleteByTid(String tid) {
        return strappingPrecheckResultMapper.delete(
                new QueryWrapper<StrappingPrecheckResult>().eq("tid", tid)
        ) > 0;
    }
}
