package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.ClearHistoryRepository;
import com.czkuo.rdf88701.domain.repository.ContainerAttrRepository;
import com.czkuo.rdf88701.infra.entity.ContainerAttr;
import com.czkuo.rdf88701.infra.mapper.ClearHistoryMapper;
import com.czkuo.rdf88701.infra.mapper.ContainerAttrMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Repository
public class ClearHistoryRepositoryImpl implements ClearHistoryRepository {

    private final ClearHistoryMapper clearHistoryMapper;

    public ClearHistoryRepositoryImpl(ClearHistoryMapper clearHistoryMapper) {
        this.clearHistoryMapper = clearHistoryMapper;
    }

    public int deleteTableDataBeforeTime(String tableName,
                                         String colName,
                                         LocalDateTime clearBefore,
                                         int limit) {
        return clearHistoryMapper.deleteTableDataBeforeTime(tableName, colName, clearBefore, limit);
    }

    @Override
    public int deleteMqttEventLogBeforeTime(LocalDateTime clearBefore, int limit) {
        return clearHistoryMapper.deleteMqttEventLogBeforeTime(clearBefore, limit);
    }

    @Override
    public int deleteMqttMessageLogBeforeTimeByIdDesc(LocalDateTime clearBefore, int limit) {
        return clearHistoryMapper.deleteMqttMessageLogBeforeTimeByIdDesc(clearBefore, limit);
    }

    @Override
    public int deleteMqttEventStatusLogBeforeTime(LocalDateTime clearBefore, int limit) {
        return clearHistoryMapper.deleteMqttEventStatusLogBeforeTime(clearBefore, limit);
    }

}
