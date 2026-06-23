package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.MqttEventStatusLogRepository;
import com.czkuo.rdf88701.infra.entity.MqttEventStatusLog;
import com.czkuo.rdf88701.infra.mapper.MqttEventStatusLogMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MqttEventStatusLogRepositoryImpl implements MqttEventStatusLogRepository {

    private final MqttEventStatusLogMapper mqttEventStatusLogMapper;

    public MqttEventStatusLogRepositoryImpl(MqttEventStatusLogMapper mqttEventStatusLogMapper) {
        this.mqttEventStatusLogMapper = mqttEventStatusLogMapper;
    }

    @Override
    public Optional<MqttEventStatusLog> findById(Long id) {
        return Optional.ofNullable(mqttEventStatusLogMapper.selectById(id));
    }

    @Override
    public boolean save(MqttEventStatusLog entity) {
        return mqttEventStatusLogMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(MqttEventStatusLog entity) {
        return mqttEventStatusLogMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return mqttEventStatusLogMapper.deleteById(id) > 0;
    }

    @Override
    public List<MqttEventStatusLog> findAll() {
        return mqttEventStatusLogMapper.selectList(new QueryWrapper<>());
    }
}
