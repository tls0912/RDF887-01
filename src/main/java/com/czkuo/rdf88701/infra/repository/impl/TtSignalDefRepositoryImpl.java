package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.TtSignalDefRepository;
import com.czkuo.rdf88701.infra.entity.TtSignalDef;
import com.czkuo.rdf88701.infra.mapper.TtSignalDefMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class TtSignalDefRepositoryImpl implements TtSignalDefRepository {

    private static final int DEFAULT_BATCH_SIZE = 200;

    private final TtSignalDefMapper ttSignalDefMapper;

    public TtSignalDefRepositoryImpl(TtSignalDefMapper ttSignalDefMapper) {
        this.ttSignalDefMapper = ttSignalDefMapper;
    }

    @Override
    public Optional<TtSignalDef> findById(Long id) {
        return Optional.ofNullable(ttSignalDefMapper.selectById(id));
    }

    @Override
    public boolean save(TtSignalDef entity) {
        return ttSignalDefMapper.insert(entity) > 0;
    }

    @Override
    public boolean saveBatch(List<TtSignalDef> items) {
        if (items == null || items.isEmpty()) {
            return true;
        }

        int total = items.size();
        for (int from = 0; from < total; from += DEFAULT_BATCH_SIZE) {
            int to = Math.min(from + DEFAULT_BATCH_SIZE, total);
            List<TtSignalDef> sub = items.subList(from, to);
            for (TtSignalDef it : sub) {
                ttSignalDefMapper.insert(it);
            }
        }
        return true;
    }

    @Override
    public boolean update(TtSignalDef entity) {
        return ttSignalDefMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return ttSignalDefMapper.deleteById(id) > 0;
    }

    @Override
    public List<TtSignalDef> findAll() {
        return ttSignalDefMapper.selectList(new QueryWrapper<>());
    }

    @Override
    public List<TtSignalDef> findByDevice(String deviceType, String deviceName) {
        return ttSignalDefMapper.selectList(new QueryWrapper<TtSignalDef>()
                .eq("device_type", deviceType)
                .eq("device_name", deviceName)
                .orderByAsc("step_no"));
    }

    @Override
    public List<TtSignalDef> findByPlcArea(String plcArea) {
        return ttSignalDefMapper.selectList(new QueryWrapper<TtSignalDef>()
                .eq("plc_area", plcArea)
                .orderByAsc("step_no"));
    }

    @Override
    public Optional<TtSignalDef> findByDeviceAndPlcWord(String deviceType, String deviceName, String plcWord) {
        if (plcWord == null) {
            return Optional.empty();
        }
        TtSignalDef row = ttSignalDefMapper.selectOne(new QueryWrapper<TtSignalDef>()
                .eq("device_type", deviceType)
                .eq("device_name", deviceName)
                .eq("plc_word", plcWord)
                .last("LIMIT 1"));
        return Optional.ofNullable(row);
    }

    @Override
    public boolean existsByDeviceAndPlcWord(String deviceType, String deviceName, String plcWord) {
        if (plcWord == null) {
            return false;
        }
        Long cnt = ttSignalDefMapper.selectCount(new QueryWrapper<TtSignalDef>()
                .eq("device_type", deviceType)
                .eq("device_name", deviceName)
                .eq("plc_word", plcWord)
                .last("LIMIT 1"));
        return cnt != null && cnt > 0;
    }
}
