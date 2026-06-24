package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.czkuo.rdf88701.application.dto.query.CraneTaskHistoryQuery;
import com.czkuo.rdf88701.common.dto.PageResult;
import com.czkuo.rdf88701.common.util.LambdaQueryHelper;
import com.czkuo.rdf88701.domain.repository.CraneTaskHistoryRepository;
import com.czkuo.rdf88701.infra.entity.CraneTaskHistory;
import com.czkuo.rdf88701.infra.mapper.CraneTaskHistoryMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Crane 任務歷史資料存取實作
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Repository
public class CraneTaskHistoryRepositoryImpl implements CraneTaskHistoryRepository {

    private final CraneTaskHistoryMapper craneTaskHistoryMapper;

    public CraneTaskHistoryRepositoryImpl(CraneTaskHistoryMapper craneTaskHistoryMapper) {
        this.craneTaskHistoryMapper = craneTaskHistoryMapper;
    }

    @Override
    public Optional<CraneTaskHistory> findById(Long id) {
        return Optional.ofNullable(craneTaskHistoryMapper.selectById(id));
    }

    @Override
    public List<CraneTaskHistory> findByCondition(CraneTaskHistoryQuery query) {
        LambdaQueryHelper<CraneTaskHistory> helper = buildQueryWrapper(query);
        int offset = (query.getSafePageNum() - 1) * query.getSafePageSize();
        helper.getWrapper().last("LIMIT " + offset + "," + query.getSafePageSize());
        return craneTaskHistoryMapper.selectList(helper.getWrapper());
    }

    @Override
    public PageResult<CraneTaskHistory> findPageByCondition(CraneTaskHistoryQuery query) {
        LambdaQueryHelper<CraneTaskHistory> helper = buildQueryWrapper(query);
        Page<CraneTaskHistory> page = new Page<>(query.getSafePageNum(), query.getSafePageSize());
        IPage<CraneTaskHistory> result = craneTaskHistoryMapper.selectPage(page, helper.getWrapper());
        return new PageResult<>(query.getSafePageNum(), query.getSafePageSize(), result.getTotal(), result.getRecords());
    }

    @Override
    public boolean save(CraneTaskHistory entity) {
        return craneTaskHistoryMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(CraneTaskHistory entity) {
        return craneTaskHistoryMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return craneTaskHistoryMapper.deleteById(id) > 0;
    }

    @Override
    public List<CraneTaskHistory> findAll() {
        return craneTaskHistoryMapper.selectList(null);
    }

    /**
     * 建立查詢條件 Wrapper（支援所有欄位查詢）
     */
    private LambdaQueryHelper<CraneTaskHistory> buildQueryWrapper(CraneTaskHistoryQuery query) {
        return LambdaQueryHelper.<CraneTaskHistory>of()
                .eqIfPresent(CraneTaskHistory::getId, query::getId)
                .inIfPresent(CraneTaskHistory::getId, query::getIdList)
                .eqIfPresent(CraneTaskHistory::getOriginId, query::getOriginId)
                .eqIfPresent(CraneTaskHistory::getRequestId, query::getRequestId)
                .eqIfPresent(CraneTaskHistory::getCraneId, query::getCraneId)
                .eqIfPresent(CraneTaskHistory::getTaskType, query::getTaskType)
                .eqIfPresent(CraneTaskHistory::getTaskStatus, query::getTaskStatus)
                .eqIfPresent(CraneTaskHistory::getPriorityLevel, query::getPriorityLevel)
                .eqIfPresent(CraneTaskHistory::getContainerMainId, query::getContainerMainId)
                .eqIfPresent(CraneTaskHistory::getFromLocationId, query::getSourceLocationId)
                .eqIfPresent(CraneTaskHistory::getToLocationId, query::getTargetLocationId)
                .eqIfPresent(CraneTaskHistory::getChangeType, query::getChangeType)
                .geIfPresent(CraneTaskHistory::getCreatedTime, query::getCreatedAfter)
                .leIfPresent(CraneTaskHistory::getCreatedTime, query::getCreatedBefore)
                .geIfPresent(CraneTaskHistory::getUpdatedTime, query::getUpdatedAfter)
                .leIfPresent(CraneTaskHistory::getUpdatedTime, query::getUpdatedBefore)
                .geIfPresent(CraneTaskHistory::getDispatchedTime, query::getDispatchedAfter)
                .leIfPresent(CraneTaskHistory::getDispatchedTime, query::getDispatchedBefore)
                .geIfPresent(CraneTaskHistory::getCompletedTime, query::getCompletedAfter)
                .leIfPresent(CraneTaskHistory::getCompletedTime, query::getCompletedBefore)
                .geIfPresent(CraneTaskHistory::getCancelledTime, query::getCancelledAfter)
                .leIfPresent(CraneTaskHistory::getCancelledTime, query::getCancelledBefore)
                .geIfPresent(CraneTaskHistory::getArchivedTime, query::getArchivedAfter)
                .leIfPresent(CraneTaskHistory::getArchivedTime, query::getArchivedBefore);
    }
}
