package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.AlarmActionLogRepository;
import com.czkuo.rdf88701.infra.entity.AlarmActionLog;
import com.czkuo.rdf88701.infra.mapper.AlarmActionLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;


@Repository
public class AlarmActionLogRepositoryImpl implements AlarmActionLogRepository {

    private final AlarmActionLogMapper alarmActionLogMapper;
    public AlarmActionLogRepositoryImpl(AlarmActionLogMapper alarmActionLogMapper) {
        this.alarmActionLogMapper = alarmActionLogMapper;
    }
    @Override
    public Optional<AlarmActionLog> findById(Long id) {
        return Optional.ofNullable(alarmActionLogMapper.selectById(id));
    }

    @Override
    public boolean save(AlarmActionLog entity) {
        return alarmActionLogMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(AlarmActionLog entity) {
        return alarmActionLogMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return alarmActionLogMapper.deleteById(id) > 0;
    }

    @Override
    public List<AlarmActionLog> findAll() {
        return alarmActionLogMapper.selectList(new QueryWrapper<>());
    }

}
