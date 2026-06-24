package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.application.service.History.WorkingBeamRequestHistoryInsertService;
import com.czkuo.rdf88701.domain.repository.WorkingBeamRequestRepository;
import com.czkuo.rdf88701.infra.entity.WorkingBeamRequest;
import com.czkuo.rdf88701.infra.mapper.WorkingBeamRequestHistoryMapper;
import com.czkuo.rdf88701.infra.mapper.WorkingBeamRequestMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * WorkingBeamRequestRepository 實作
 * - 負責實體操作資料庫
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Repository
@RequiredArgsConstructor
public class WorkingBeamRequestRepositoryImpl implements WorkingBeamRequestRepository {

    private final WorkingBeamRequestMapper workingBeamRequestMapper;
    private final WorkingBeamRequestHistoryMapper workingBeamRequestHistoryMapper;
    private final WorkingBeamRequestHistoryInsertService workingBeamHistoryQueueService;

    @Override
    public Optional<WorkingBeamRequest> findById(Long id) {
        return Optional.ofNullable(workingBeamRequestMapper.selectById(id));
    }

    @Override
    public boolean save(WorkingBeamRequest entity) {
        boolean success = workingBeamRequestMapper.insert(entity) > 0;
        if (success) {
            insertHistory(entity, "INSERT");
        }
        return success;
        //return workingBeamRequestMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(WorkingBeamRequest entity) {
        boolean success = workingBeamRequestMapper.updateById(entity) > 0;
        if (success) {
            insertHistory(entity, "UPDATE");
        }
        return success;
        //return workingBeamRequestMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        WorkingBeamRequest beforeDelete = workingBeamRequestMapper.selectById(id);
        boolean success = workingBeamRequestMapper.deleteById(beforeDelete) > 0;
        if (success) {
            insertHistory(beforeDelete, "DELETE");
        }
        return success;
        //return workingBeamRequestMapper.deleteById(id) > 0;
    }

    @Override
    public List<WorkingBeamRequest> findAll() {
        return workingBeamRequestMapper.selectList(new QueryWrapper<>());
    }

    /**
     * 根據唯一鍵 request_key 查詢對應的請求資料
     */
    @Override
    public Optional<WorkingBeamRequest> findByRequestKey(String requestKey) {
        QueryWrapper<WorkingBeamRequest> wrapper = new QueryWrapper<>();
        wrapper.eq("request_key", requestKey);
        return Optional.ofNullable(workingBeamRequestMapper.selectOne(wrapper));
    }

    /**
     * 判斷 request_key 是否已存在（避免重複建立）
     */
    @Override
    public boolean existsByRequestKey(String requestKey) {
        QueryWrapper<WorkingBeamRequest> wrapper = new QueryWrapper<>();
        wrapper.eq("request_key", requestKey);
        return workingBeamRequestMapper.selectCount(wrapper) > 0;
    }

    /**
     * 判斷指定 WorkingBeam 是否仍有尚未接受的請求（accepted = 'N'）
     * - 通常應搭配 task 判斷是否需要產生新 request
     */
    @Override
    public boolean existsUnfinishedRequestForBeam(Long workingBeamId) {
        QueryWrapper<WorkingBeamRequest> wrapper = new QueryWrapper<>();
        wrapper.eq("working_beam_id", workingBeamId)
                .eq("accepted", "N");
        return workingBeamRequestMapper.selectCount(wrapper) > 0;
    }

    /**
     * 查詢所有尚未被接受的請求
     * - 通常提供給定期監控任務使用
     */
    @Override
    public List<WorkingBeamRequest> findUnacceptedRequests() {
        QueryWrapper<WorkingBeamRequest> wrapper = new QueryWrapper<>();
        wrapper.eq("accepted", "N")
                .orderByAsc("created_time");
        ;
        return workingBeamRequestMapper.selectList(wrapper);
    }

    /**
     * 取得某 WorkingBeam 最早一筆尚未接受的請求（依建立時間排序）
     * - 通常用於排隊管理或輪詢處理順序
     */
    @Override
    public Optional<WorkingBeamRequest> findFirstUnacceptedByWorkingBeamName(String workingBeamId) {
        QueryWrapper<WorkingBeamRequest> wrapper = new QueryWrapper<>();
        wrapper.eq("working_beam_id", workingBeamId)
                .eq("accepted", "N")
                .orderByAsc("created_time")
                .last("LIMIT 1");

        return Optional.ofNullable(workingBeamRequestMapper.selectOne(wrapper));
    }

    /**
     * 寫入歷程表（共用方法）
     */
    private void insertHistory(WorkingBeamRequest entity, String changeType) {
        workingBeamHistoryQueueService.offer(entity, changeType);
//        WorkingBeamRequestHistory history = new WorkingBeamRequestHistory();
//        BeanUtils.copyProperties(entity, history, "id");
//        history.setOriginId(entity.getId());
//        history.setChangeType(changeType);
//        history.setArchivedTime(LocalDateTime.now());
//        workingBeamRequestHistoryMapper.insert(history);
    }
}
