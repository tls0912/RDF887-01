package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.LabelingInfoRepository;
import com.czkuo.rdf88701.infra.entity.LabelingInfo;
import com.czkuo.rdf88701.infra.mapper.LabelingInfoMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Repository
public class LabelingInfoRepositoryImpl implements LabelingInfoRepository {

    private final LabelingInfoMapper labelingInfoMapper;

    public LabelingInfoRepositoryImpl(LabelingInfoMapper labelingInfoMapper) {
        this.labelingInfoMapper = labelingInfoMapper;
    }

    @Override
    public Optional<LabelingInfo> findById(Long id) {
        return Optional.ofNullable(labelingInfoMapper.selectById(id));
    }

    @Override
    public Optional<LabelingInfo> findByRequestKey(String requestKey) {
        return Optional.ofNullable(labelingInfoMapper.findByRequestKey(requestKey));
    }

    @Override
    public boolean save(LabelingInfo entity) {
        return labelingInfoMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(LabelingInfo entity) {
        return labelingInfoMapper.updateById(entity) > 0;
    }

    @Override
    public boolean updateStatus(Long id, String status) {
        return labelingInfoMapper.updateStatus(id, status) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return labelingInfoMapper.deleteById(id) > 0;
    }

    @Override
    public List<LabelingInfo> findAll() {
        return labelingInfoMapper.selectList(new QueryWrapper<>());
    }

    @Override
    public Long selectMaxId() {
        Long v = labelingInfoMapper.selectMaxId();
        return v == null ? 0L : v;
    }

    @Override
    public Optional<LabelingInfo> selectReadyForClaim(String siteCode) {
        return Optional.ofNullable(labelingInfoMapper.selectReadyForClaim(siteCode));
    }

    @Override
    public boolean bindToSiteAndContainer(Long id, String siteCode, Long containerMainId, Integer labelNo) {
        return labelingInfoMapper.bindToSiteAndContainer(id, siteCode, containerMainId, labelNo) > 0;
    }

    @Override
    public Optional<LabelingInfo> selectReadyAfterId(String siteCode, Long afterId) {
        return Optional.ofNullable(labelingInfoMapper.selectReadyAfterId(siteCode, afterId));
    }

    @Override
    public Optional<LabelingInfo> findReady(Long containerMainId, String siteCode) {
        return Optional.ofNullable(labelingInfoMapper.findReady(containerMainId, siteCode));
    }
}
