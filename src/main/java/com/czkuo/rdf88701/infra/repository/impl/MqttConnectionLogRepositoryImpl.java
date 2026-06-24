package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.MqttConnectionLogRepository;
import com.czkuo.rdf88701.infra.entity.MqttConnectionLog;
import com.czkuo.rdf88701.infra.mapper.MqttConnectionLogMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Repository
public class MqttConnectionLogRepositoryImpl implements MqttConnectionLogRepository {

    private final MqttConnectionLogMapper mqttConnectionLogMapper;

    public MqttConnectionLogRepositoryImpl(MqttConnectionLogMapper mqttConnectionLogMapper) {
        this.mqttConnectionLogMapper = mqttConnectionLogMapper;
    }

    @Override
    public Optional<MqttConnectionLog> findById(Long id) {
        return Optional.ofNullable(mqttConnectionLogMapper.selectById(id));
    }

    @Override
    public boolean save(MqttConnectionLog entity) {
        return mqttConnectionLogMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(MqttConnectionLog entity) {
        return mqttConnectionLogMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return mqttConnectionLogMapper.deleteById(id) > 0;
    }

    @Override
    public List<MqttConnectionLog> findAll() {
        return mqttConnectionLogMapper.selectList(new QueryWrapper<>());
    }
}
