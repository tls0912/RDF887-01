package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.OcrAlarmRepository;
import com.czkuo.rdf88701.infra.entity.OcrAlarm;
import com.czkuo.rdf88701.infra.mapper.OcrAlarmMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Repository
public class OcrAlarmRepositoryImpl implements OcrAlarmRepository {

    private final OcrAlarmMapper ocrAlarmMapper;

    public OcrAlarmRepositoryImpl(OcrAlarmMapper ocrAlarmMapper) {
        this.ocrAlarmMapper = ocrAlarmMapper;
    }

    @Override
    public Optional<OcrAlarm> findById(Long id) {
        return Optional.ofNullable(ocrAlarmMapper.selectById(id));
    }

    @Override
    public boolean save(OcrAlarm entity) {
        return ocrAlarmMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(OcrAlarm entity) {
        return ocrAlarmMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return ocrAlarmMapper.deleteById(id) > 0;
    }

    @Override
    public List<OcrAlarm> findAll() {
        return ocrAlarmMapper.selectList(new QueryWrapper<>());
    }
}
