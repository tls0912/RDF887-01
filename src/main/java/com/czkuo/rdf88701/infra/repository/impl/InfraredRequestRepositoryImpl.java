package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.application.service.History.InfraredRequestHistoryInsertService;
import com.czkuo.rdf88701.domain.repository.InfraredRequestRepository;
import com.czkuo.rdf88701.infra.entity.InfraredRequest;
import com.czkuo.rdf88701.infra.mapper.InfraredRequestHistoryMapper;
import com.czkuo.rdf88701.infra.mapper.InfraredRequestMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * InfraredRequestRepository 實作
 * - 負責 Infrared 請求相關資料庫存取
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Repository
@RequiredArgsConstructor
public class InfraredRequestRepositoryImpl implements InfraredRequestRepository {

    private final InfraredRequestMapper infraredRequestMapper;
    private final InfraredRequestHistoryMapper infraredRequestHistoryMapper;
    private final InfraredRequestHistoryInsertService infraredHistoryQueueService;

    @Override
    public Optional<InfraredRequest> findById(Long id) {
        return Optional.ofNullable(infraredRequestMapper.selectById(id));
    }

    @Override
    public boolean save(InfraredRequest entity) {
        boolean success = infraredRequestMapper.insert(entity) > 0;
        if (success) {
            insertHistory(entity, "INSERT");
        }
        return success;
        //return infraredRequestMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(InfraredRequest entity) {
        boolean success = infraredRequestMapper.updateById(entity) > 0;
        if (success) {
            insertHistory(entity, "UPDATE");
        }
        return success;
        //return infraredRequestMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        InfraredRequest beforeDelete = infraredRequestMapper.selectById(id);
        boolean success = infraredRequestMapper.deleteById(beforeDelete) > 0;
        if (success) {
            insertHistory(beforeDelete, "DELETE");
        }
        return success;
        //return infraredRequestMapper.deleteById(id) > 0;
    }

    @Override
    public List<InfraredRequest> findAll() {
        return infraredRequestMapper.selectList(new QueryWrapper<>());
    }

    /**
     * 根據唯一鍵 request_key 查詢對應的請求資料
     */
    @Override
    public Optional<InfraredRequest> findByRequestKey(String requestKey) {
        QueryWrapper<InfraredRequest> wrapper = new QueryWrapper<>();
        wrapper.eq("request_key", requestKey);
        return Optional.ofNullable(infraredRequestMapper.selectOne(wrapper));
    }

    /**
     * 判斷 request_key 是否已存在（避免重複建立）
     */
    @Override
    public boolean existsByRequestKey(String requestKey) {
        QueryWrapper<InfraredRequest> wrapper = new QueryWrapper<>();
        wrapper.eq("request_key", requestKey);
        return infraredRequestMapper.selectCount(wrapper) > 0;
    }

    /**
     * 判斷指定 Infrared 是否仍有尚未接受的請求（accepted = 'N'）
     * - 建議僅在「MEASURE」型別下檢查，如需可加上 .eq("task_type","MEASURE")
     */
    @Override
    public boolean existsUnfinishedRequestForInfrared(Long infraredId) {
        QueryWrapper<InfraredRequest> wrapper = new QueryWrapper<>();
        wrapper.eq("infrared_id", infraredId)
                .eq("accepted", "N");
        return infraredRequestMapper.selectCount(wrapper) > 0;
    }

    /**
     * 查詢所有尚未被接受的請求（accepted = 'N'）
     */
    @Override
    public List<InfraredRequest> findUnacceptedRequests() {
        QueryWrapper<InfraredRequest> wrapper = new QueryWrapper<>();
        wrapper.eq("accepted", "N")
                .orderByAsc("created_time");
        return infraredRequestMapper.selectList(wrapper);
    }

    // ===== 這裡將「ByInfraredName」改為以 ID 查詢（資料表無 name 欄位）=====

    /**
     * 取得某 Infrared 最早一筆尚未接受的請求（依建立時間排序）
     */
    public Optional<InfraredRequest> findFirstUnacceptedByInfraredId(Long infraredId) {
        QueryWrapper<InfraredRequest> wrapper = new QueryWrapper<>();
        wrapper.eq("infrared_id", infraredId)
                .eq("accepted", "N")
                .orderByAsc("created_time")
                .last("LIMIT 1");
        return Optional.ofNullable(infraredRequestMapper.selectOne(wrapper));
    }

    // ===== 新增：建立量測請求（帶 container_main_id + infraredId）=====

    @Override
    public boolean createMeasureRequestForContainer(Long containerMainId, Long infraredId) {
        // 建立新請求（就像你示範的那樣）
        InfraredRequest request = new InfraredRequest();
        request.setRequestKey(UUID.randomUUID().toString());
        request.setVersion(1);
        request.setRequestSource("SYSTEM");
        request.setInfraredId(infraredId);
        request.setTaskType("MEASURE");
        request.setAccepted("N");
        request.setRequestTime(LocalDateTime.now());
        request.setCreatedTime(LocalDateTime.now());
        // 帶入 container_main_id
        request.setContainerMainId(containerMainId);

        return save(request);
    }

    private void insertHistory(InfraredRequest entity, String changeType) {
        infraredHistoryQueueService.offer(entity,changeType);
//        InfraredRequestHistory history = new InfraredRequestHistory();
//        BeanUtils.copyProperties(entity, history, "id");
//        history.setOriginId(entity.getId());
//        history.setChangeType(changeType);
//        history.setArchivedTime(LocalDateTime.now());
//        infraredRequestHistoryMapper.insert(history);
    }
}
