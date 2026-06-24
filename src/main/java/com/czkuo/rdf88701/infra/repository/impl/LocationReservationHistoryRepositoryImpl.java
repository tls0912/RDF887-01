package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.LocationReservationHistoryRepository;
import com.czkuo.rdf88701.infra.entity.LocationReservationHistory;
import com.czkuo.rdf88701.infra.mapper.LocationReservationHistoryMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Repository
public class LocationReservationHistoryRepositoryImpl implements LocationReservationHistoryRepository {

    private final LocationReservationHistoryMapper locationReservationHistoryMapper;

    public LocationReservationHistoryRepositoryImpl(LocationReservationHistoryMapper locationReservationHistoryMapper) {
        this.locationReservationHistoryMapper = locationReservationHistoryMapper;
    }

    @Override
    public Optional<LocationReservationHistory> findById(Long id) {
        return Optional.ofNullable(locationReservationHistoryMapper.selectById(id));
    }

    @Override
    public boolean save(LocationReservationHistory entity) {
        return locationReservationHistoryMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(LocationReservationHistory entity) {
        return locationReservationHistoryMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return locationReservationHistoryMapper.deleteById(id) > 0;
    }

    @Override
    public List<LocationReservationHistory> findAll() {
        return locationReservationHistoryMapper.selectList(new QueryWrapper<>());
    }
}
