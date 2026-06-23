package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.ContainerAttrRepository;
import com.czkuo.rdf88701.infra.entity.ContainerAttr;
import com.czkuo.rdf88701.infra.mapper.ContainerAttrMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static javax.print.attribute.standard.MediaSizeName.B;

@Repository
public class ContainerAttrRepositoryImpl implements ContainerAttrRepository {

    private final ContainerAttrMapper containerAttrMapper;

    public ContainerAttrRepositoryImpl(ContainerAttrMapper containerAttrMapper) {
        this.containerAttrMapper = containerAttrMapper;
    }

    /* ---------------- 既有 CRUD ---------------- */

    @Override
    public Optional<ContainerAttr> findById(Long id) {
        return Optional.ofNullable(containerAttrMapper.selectById(id));
    }

    @Override
    public boolean save(ContainerAttr entity) {
        return containerAttrMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(ContainerAttr entity) {
        return containerAttrMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return containerAttrMapper.deleteById(id) > 0;
    }

    @Override
    public List<ContainerAttr> findAll() {
        return containerAttrMapper.selectList(new QueryWrapper<>());
    }

    /* ---------------- 新增便捷方法 ---------------- */

    @Override
    public Optional<ContainerAttr> findOne(Long containerMainId, String attrKey) {
        if (containerMainId == null || StringUtils.isBlank(attrKey)) {
            return Optional.empty();
        }
        LambdaQueryWrapper<ContainerAttr> qw = new LambdaQueryWrapper<ContainerAttr>()
                .eq(ContainerAttr::getContainerMainId, containerMainId)
                .eq(ContainerAttr::getAttrKey, attrKey)
                .last("LIMIT 1");
        return Optional.ofNullable(containerAttrMapper.selectOne(qw));
    }

    @Override
    public Map<String, ContainerAttr> findContainerAttrs(Long containerMainId) {
        LambdaQueryWrapper<ContainerAttr> qw = new LambdaQueryWrapper<ContainerAttr>()
                .eq(ContainerAttr::getContainerMainId, containerMainId)
                .orderByDesc(ContainerAttr::getUpdatedTime);;
        return containerAttrMapper.selectList(qw)
                .stream()
                .collect(Collectors.toMap(
                        ContainerAttr::getAttrKey,
                        Function.identity(),
                        (a, b) -> a
                ));
    }

    @Override
    public boolean upsert(ContainerAttr entity) {
        if (entity == null) return false;
        return containerAttrMapper.upsert(entity) > 0;
    }

    @Override
    public int batchUpsert(List<ContainerAttr> list) {
        if (list == null || list.isEmpty()) return 0;
        return containerAttrMapper.batchUpsert(list);
    }

    @Override
    public List<ContainerAttr> findByKeyAndValue(String attrKey, String attrValue) {
        if (StringUtils.isBlank(attrKey) || StringUtils.isBlank(attrValue)) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<ContainerAttr> qw = new LambdaQueryWrapper<ContainerAttr>()
                .eq(ContainerAttr::getAttrKey, attrKey)
                .eq(ContainerAttr::getAttrValue, attrValue);
        return containerAttrMapper.selectList(qw);
    }

    @Override
    public boolean deleteByContainerMainId(Long containerMainId) {
        if (containerMainId == null) return false;
        int rows = containerAttrMapper.delete(
                new LambdaQueryWrapper<ContainerAttr>()
                        .eq(ContainerAttr::getContainerMainId, containerMainId)
        );
        return rows > 0;
    }

    @Override
    public boolean deleteOne(Long containerMainId, String attrKey) {
        if (containerMainId == null || StringUtils.isBlank(attrKey)) return false;
        int rows = containerAttrMapper.delete(
                new LambdaQueryWrapper<ContainerAttr>()
                        .eq(ContainerAttr::getContainerMainId, containerMainId)
                        .eq(ContainerAttr::getAttrKey, attrKey)
        );
        return rows > 0;
    }

    /* ---------------- 新增（R029 / 占用檢查 / 釋放） ---------------- */

    @Override
    public List<Long> findContainerIdsByAttrKey(String attrKey) {
        if (StringUtils.isBlank(attrKey)) return Collections.emptyList();
        return containerAttrMapper.selectContainerIdsByAttrKey(attrKey);
    }

    @Override
    public List<Long> findContainerIdsByAttrKeyAndValue(String attrKey, String attrValue) {
        if (StringUtils.isBlank(attrKey) || StringUtils.isBlank(attrValue)) {
            return Collections.emptyList();
        }
        return containerAttrMapper.selectContainerIdsByAttrKeyAndValue(attrKey, attrValue);
    }

    @Override
    public boolean existsByContainerIdAndKey(Long containerMainId, String attrKey) {
        if (containerMainId == null || StringUtils.isBlank(attrKey)) return false;
        Integer x = containerAttrMapper.existsByContainerIdAndKey(containerMainId, attrKey);
        return x != null && x == 1;
    }

    @Override
    public int deleteByAttrKeyAndValue(String attrKey, String attrValue) {
        if (StringUtils.isBlank(attrKey) || StringUtils.isBlank(attrValue)) return 0;
        return containerAttrMapper.deleteByAttrKeyAndValue(attrKey, attrValue);
    }

    @Override
    public int deleteByContainerIdsAndKeys(List<Long> containerMainIds, List<String> keys) {
        if (containerMainIds == null || containerMainIds.isEmpty()
                || keys == null || keys.isEmpty()) return 0;
        return containerAttrMapper.deleteByContainerIdsAndKeys(containerMainIds, keys);
    }
}
