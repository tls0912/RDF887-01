package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.MqttInboxStatusLogRepository;
import com.czkuo.rdf88701.infra.entity.MqttInboxStatusLog;
import com.czkuo.rdf88701.infra.mapper.MqttInboxStatusLogMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MqttInboxStatusLogRepositoryImpl implements MqttInboxStatusLogRepository {

    private final MqttInboxStatusLogMapper mqttInboxStatusLogMapper;

    public MqttInboxStatusLogRepositoryImpl(MqttInboxStatusLogMapper mqttInboxStatusLogMapper) {
        this.mqttInboxStatusLogMapper = mqttInboxStatusLogMapper;
    }

    @Override
    public Optional<MqttInboxStatusLog> findById(Long id) {
        return Optional.ofNullable(mqttInboxStatusLogMapper.selectById(id));
    }

    @Override
    public boolean save(MqttInboxStatusLog entity) {
        return mqttInboxStatusLogMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(MqttInboxStatusLog entity) {
        return mqttInboxStatusLogMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return mqttInboxStatusLogMapper.deleteById(id) > 0;
    }

    @Override
    public List<MqttInboxStatusLog> findAll() {
        return mqttInboxStatusLogMapper.selectList(new QueryWrapper<>());
    }
}
