package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.application.service.History.TransferRequestHistoryInsertService;
import com.czkuo.rdf88701.domain.repository.TransferRequestRepository;
import com.czkuo.rdf88701.infra.entity.TransferRequest;
import com.czkuo.rdf88701.infra.mapper.TransferRequestHistoryMapper;
import com.czkuo.rdf88701.infra.mapper.TransferRequestMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * TransferRequestRepositoryImpl
 * <p>
 * TransferRequestRepository 的 MyBatis Plus 實作
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Repository
@RequiredArgsConstructor
public class TransferRequestRepositoryImpl implements TransferRequestRepository {

    private final TransferRequestMapper transferRequestMapper;
    private final TransferRequestHistoryMapper transferRequestHistoryMapper;
    private final TransferRequestHistoryInsertService transferHistoryQueueService;


    @Override
    public Optional<TransferRequest> findById(Long id) {
        return Optional.ofNullable(transferRequestMapper.selectById(id));
    }

    @Override
    public boolean save(TransferRequest entity) {
        boolean success = transferRequestMapper.insert(entity) > 0;
        if (success) {
            insertHistory(entity, "INSERT");
        }
        return success;
        //return transferRequestMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(TransferRequest entity) {
        boolean success = transferRequestMapper.updateById(entity) > 0;
        if (success) {
            insertHistory(entity, "UPDATE");
        }
        return success;
        //return transferRequestMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        TransferRequest beforeDelete = transferRequestMapper.selectById(id);
        boolean success = transferRequestMapper.deleteById(beforeDelete) > 0;
        if (success) {
            insertHistory(beforeDelete, "DELETE");
        }
        return success;
        //return transferRequestMapper.deleteById(id) > 0;
    }

    @Override
    public List<TransferRequest> findAll() {
        return transferRequestMapper.selectList(new QueryWrapper<>());
    }


    /**
     * 根據 request_key 查詢 TransferRequest（通常為外部唯一識別用）
     */
    public Optional<TransferRequest> findByRequestKey(String requestKey) {
        QueryWrapper<TransferRequest> wrapper = new QueryWrapper<>();
        wrapper.eq("request_key", requestKey);
        return Optional.ofNullable(transferRequestMapper.selectOne(wrapper));
    }

    /**
     * 判斷 request_key 是否已存在，避免重複建立
     */
    public boolean existsByRequestKey(String requestKey) {
        QueryWrapper<TransferRequest> wrapper = new QueryWrapper<>();
        wrapper.eq("request_key", requestKey);
        return transferRequestMapper.selectCount(wrapper) > 0;
    }

    /**
     * 查詢指定裝置是否仍有尚未接受的 TransferRequest（通常為 accepted='N'）
     * - 可依照實際表欄位補上 transfer_id 或其他條件
     */
    public boolean existsUnfinishedRequestForDevice(Long deviceId) {
        QueryWrapper<TransferRequest> wrapper = new QueryWrapper<>();
        wrapper.eq("transfer_id", deviceId)
                .eq("accepted", "N");
        return transferRequestMapper.selectCount(wrapper) > 0;
    }

    /**
     * 查詢所有尚未被接受的 TransferRequest
     */
    public List<TransferRequest> findUnacceptedRequests() {
        QueryWrapper<TransferRequest> wrapper = new QueryWrapper<>();
        wrapper.eq("accepted", "N")
                .orderByAsc("created_time");
        return transferRequestMapper.selectList(wrapper);
    }

    /**
     * 取得指定裝置最早的尚未接受 TransferRequest
     */
    public Optional<TransferRequest> findFirstUnacceptedByDeviceId(String deviceId) {
        QueryWrapper<TransferRequest> wrapper = new QueryWrapper<>();
        wrapper.eq("transfer_id", deviceId)
                .eq("accepted", "N")
                .orderByAsc("created_time")
                .last("LIMIT 1");
        return Optional.ofNullable(transferRequestMapper.selectOne(wrapper));
    }

    /**
     * 寫入歷程表（共用方法）
     */
    private void insertHistory(TransferRequest entity, String changeType) {
        transferHistoryQueueService.offer(entity, changeType);
//        TransferRequestHistory history = new TransferRequestHistory();
//        BeanUtils.copyProperties(entity, history, "id");
//        history.setOriginId(entity.getId());
//        history.setChangeType(changeType);
//        history.setArchivedTime(LocalDateTime.now());
//        transferRequestHistoryMapper.insert(history);
    }
}
