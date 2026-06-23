package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.czkuo.rdf88701.domain.repository.StrappingLogRepository;
import com.czkuo.rdf88701.infra.entity.StrappingLog;
import com.czkuo.rdf88701.infra.mapper.StrappingLogMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class StrappingLogRepositoryImpl implements StrappingLogRepository {

    private final StrappingLogMapper strappingLogMapper;

    public StrappingLogRepositoryImpl(StrappingLogMapper strappingLogMapper) {
        this.strappingLogMapper = strappingLogMapper;
    }

    @Override
    public Optional<StrappingLog> findById(Long id) {
        return Optional.ofNullable(strappingLogMapper.selectById(id));
    }

    @Override
    public boolean save(StrappingLog entity) {
        return strappingLogMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(StrappingLog entity) {
        return strappingLogMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return strappingLogMapper.deleteById(id) > 0;
    }

    @Override
    public List<StrappingLog> findAll() {
        return strappingLogMapper.selectList(new QueryWrapper<>());
    }

    @Override
    public boolean existsByMachinePosEpochAndSeqIndex(Byte machinePos, Integer seqEpoch, Integer seqIndex) {
        Long cnt = strappingLogMapper.selectCount(
                Wrappers.<StrappingLog>lambdaQuery()
                        .eq(StrappingLog::getMachinePos, machinePos)
                        .eq(StrappingLog::getSeqEpoch, seqEpoch)
                        .eq(StrappingLog::getSeqIndex, seqIndex)
                        .last("LIMIT 1")
        );
        return cnt != null && cnt > 0;
    }
    @Override
    public boolean existsByMachinePosEpochAndEventTime(Byte machinePos, Integer seqEpoch, LocalDateTime eventTime) {
        Long cnt = strappingLogMapper.selectCount(
                Wrappers.<StrappingLog>lambdaQuery()
                        .eq(StrappingLog::getMachinePos, machinePos)
                        .eq(StrappingLog::getSeqEpoch, seqEpoch)
                        .eq(StrappingLog::getEventTime, eventTime)
                        .last("LIMIT 1")
        );
        return cnt != null && cnt > 0;
    }
    @Override
    public Optional<SeqCursor> findLastCursor() {
        // 以 id 最新的一筆當作游標（created_time 也行，但 id 最穩）
        StrappingLog row = strappingLogMapper.selectOne(
                Wrappers.<StrappingLog>lambdaQuery()
                        .select(StrappingLog::getSeqEpoch, StrappingLog::getSeqIndex)
                        .orderByDesc(StrappingLog::getId)
                        .last("LIMIT 1")
        );

        if (row == null || row.getSeqEpoch() == null || row.getSeqIndex() == null) return Optional.empty();
        return Optional.of(new SeqCursor(row.getSeqEpoch(), row.getSeqIndex()));
    }

    @Override
    public List<StrappingLog> findByTimeRange(LocalDateTime start, LocalDateTime end) {
        return strappingLogMapper.selectList(
                Wrappers.<StrappingLog>lambdaQuery()
                        .ge(StrappingLog::getEventTime, start)
                        .le(StrappingLog::getEventTime, end)
                        .orderByAsc(StrappingLog::getEventTime)
        );
    }

    @Override
    public List<StrappingLog> findByTimeRangeAndMachine(LocalDateTime start, LocalDateTime end, Byte machinePos) {
        return strappingLogMapper.selectList(
                Wrappers.<StrappingLog>lambdaQuery()
                        .eq(StrappingLog::getMachinePos, machinePos)
                        .ge(StrappingLog::getEventTime, start)
                        .le(StrappingLog::getEventTime, end)
                        .orderByAsc(StrappingLog::getEventTime)
        );
    }
}
