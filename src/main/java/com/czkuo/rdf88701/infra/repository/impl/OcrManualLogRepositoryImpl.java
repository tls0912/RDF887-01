package com.czkuo.rdf88701.infra.repository.impl;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.czkuo.rdf88701.domain.repository.OcrManualLogRepository;
import com.czkuo.rdf88701.infra.entity.OcrManualLog;
import com.czkuo.rdf88701.infra.mapper.OcrManualLogMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Repository
public class OcrManualLogRepositoryImpl implements OcrManualLogRepository {

    private final OcrManualLogMapper ocrManualLogMapper;

    public OcrManualLogRepositoryImpl(OcrManualLogMapper ocrManualLogMapper) {
        this.ocrManualLogMapper = ocrManualLogMapper;
    }

    @Override
    public Optional<OcrManualLog> findById(Long id) {
        return Optional.ofNullable(ocrManualLogMapper.selectById(id));
    }

    @Override
    public boolean save(OcrManualLog entity) {
        return ocrManualLogMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(OcrManualLog entity) {
        return ocrManualLogMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return ocrManualLogMapper.deleteById(id) > 0;
    }

    @Override
    public List<OcrManualLog> findAll() {
        return ocrManualLogMapper.selectList(new QueryWrapper<>());
    }

    @Override
    public Optional<OcrManualLog> findByContainerMainId(Long containerMainId) {
        QueryWrapper<OcrManualLog> qw = new QueryWrapper<>();
        qw.eq("container_main_id", containerMainId).last("LIMIT 1");
        return Optional.ofNullable(ocrManualLogMapper.selectOne(qw));
    }

    @Override
    public Optional<OcrManualLog> findLatestByContainerMainIdAndRefContainerId(Long containerMainId, Long refContainerId) {
        QueryWrapper<OcrManualLog> qw = new QueryWrapper<>();
        qw.eq("container_main_id", containerMainId)
                .eq("ref_container_id", refContainerId)
                // 取最新一筆：你若有 created_time 就用 created_time；沒有就用 id desc 也行
                .orderByDesc("created_time")
                .last("LIMIT 1");
        return Optional.ofNullable(ocrManualLogMapper.selectOne(qw));
    }
}
