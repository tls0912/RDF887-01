package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.czkuo.rdf88701.domain.repository.TtRecordRepository;
import com.czkuo.rdf88701.infra.entity.TtRecord;
import com.czkuo.rdf88701.infra.mapper.TtRecordMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class TtRecordRepositoryImpl implements TtRecordRepository {

    private final TtRecordMapper ttRecordMapper;

    public TtRecordRepositoryImpl(TtRecordMapper ttRecordMapper) {
        this.ttRecordMapper = ttRecordMapper;
    }

    @Override
    public Optional<TtRecord> findById(Long id) {
        return Optional.ofNullable(ttRecordMapper.selectById(id));
    }

    @Override
    public boolean save(TtRecord entity) {
        return ttRecordMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(TtRecord entity) {
        return ttRecordMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return ttRecordMapper.deleteById(id) > 0;
    }

    @Override
    public List<TtRecord> findAll() {
        return ttRecordMapper.selectList(new QueryWrapper<>());
    }

    @Override
    public Optional<String> findLastIndex(String deviceType, String deviceName) {
        // 取最新一筆 tt_index（依 created_time 或 id 皆可；我用 id 保守）
        QueryWrapper<TtRecord> qw = new QueryWrapper<TtRecord>()
                .select("tt_index")
                .eq("device_type", deviceType)
                .eq("device_name", deviceName)
                .orderByDesc("created_time")
                .last("LIMIT 1");

        TtRecord row = ttRecordMapper.selectOne(qw);
        if (row == null || row.getTtIndex() == null) {
            return Optional.empty();
        }

        return Optional.of(row.getTtIndex());
    }

    @Override
    public boolean existsByDeviceAndIndex(String deviceType, String deviceName, String ttIndex) {
        Long cnt = ttRecordMapper.selectCount(new QueryWrapper<TtRecord>()
                .eq("device_type", deviceType)
                .eq("device_name", deviceName)
                .eq("tt_index", ttIndex)
                .last("LIMIT 1"));
        return cnt != null && cnt > 0;
    }

    @Override
    public List<TtRecord> findLatestByDevice(String deviceType, String deviceName, int limit) {
        int n = Math.max(1, Math.min(limit, 500)); // 避免有人亂給超大
        return ttRecordMapper.selectList(new QueryWrapper<TtRecord>()
                .eq("device_type", deviceType)
                .eq("device_name", deviceName)
                .orderByDesc("created_time")
                .last("LIMIT " + n));
    }

}
