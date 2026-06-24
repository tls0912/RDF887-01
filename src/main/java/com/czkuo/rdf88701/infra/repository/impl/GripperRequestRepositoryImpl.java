package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.application.service.History.GripperRequestHistoryInsertService;
import com.czkuo.rdf88701.domain.repository.GripperRequestRepository;
import com.czkuo.rdf88701.infra.entity.GripperRequest;
import com.czkuo.rdf88701.infra.mapper.GripperRequestHistoryMapper;
import com.czkuo.rdf88701.infra.mapper.GripperRequestMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * GripperRequestRepositoryImpl
 * <p>
 * GripperRequestRepository 的 MyBatis Plus 實作
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Repository
@RequiredArgsConstructor
public class GripperRequestRepositoryImpl implements GripperRequestRepository {

    private final GripperRequestMapper gripperRequestMapper;
    private final GripperRequestHistoryMapper gripperRequestHistoryMapper;
    private final GripperRequestHistoryInsertService gripperHistoryQueueService;

    @Override
    public Optional<GripperRequest> findById(Long id) {
        return Optional.ofNullable(gripperRequestMapper.selectById(id));
    }

    @Override
    public boolean save(GripperRequest entity) {
        boolean success = gripperRequestMapper.insert(entity) > 0;
        if (success) {
            insertHistory(entity, "INSERT");
        }
        return success;
        //return gripperRequestMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(GripperRequest entity) {
        boolean success = gripperRequestMapper.updateById(entity) > 0;
        if (success) {
            insertHistory(entity, "UPDATE");
        }
        return success;
        //return gripperRequestMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        GripperRequest beforeDelete = gripperRequestMapper.selectById(id);
        boolean success = gripperRequestMapper.deleteById(beforeDelete) > 0;
        if (success) {
            insertHistory(beforeDelete, "DELETE");
        }
        return success;
        //return gripperRequestMapper.deleteById(id) > 0;
    }

    @Override
    public List<GripperRequest> findAll() {
        return gripperRequestMapper.selectList(new QueryWrapper<>());
    }

    @Override
    public Optional<GripperRequest> findByRequestKey(String requestKey) {
        QueryWrapper<GripperRequest> wrapper = new QueryWrapper<>();
        wrapper.eq("request_key", requestKey);
        return Optional.ofNullable(gripperRequestMapper.selectOne(wrapper));
    }

    @Override
    public boolean existsByRequestKey(String requestKey) {
        QueryWrapper<GripperRequest> wrapper = new QueryWrapper<>();
        wrapper.eq("request_key", requestKey).last("LIMIT 1");
        return gripperRequestMapper.selectCount(wrapper) > 0;
    }

    @Override
    public boolean existsUnfinishedRequestForDevice(Long gripperId) {
        QueryWrapper<GripperRequest> wrapper = new QueryWrapper<>();
        wrapper.eq("gripper_id", gripperId)
                .eq("accepted", "N")
                .last("LIMIT 1");
        return gripperRequestMapper.selectCount(wrapper) > 0;
    }

    @Override
    public List<GripperRequest> findUnacceptedRequests() {
        QueryWrapper<GripperRequest> wrapper = new QueryWrapper<>();
        wrapper.eq("accepted", "N")
                .orderByAsc("created_time");
        return gripperRequestMapper.selectList(wrapper);
    }

    @Override
    public Optional<GripperRequest> findFirstUnacceptedByDeviceId(String gripperId) {
        QueryWrapper<GripperRequest> wrapper = new QueryWrapper<>();
        wrapper.eq("gripper_id", gripperId)
                .eq("accepted", "N")
                .orderByAsc("created_time")
                .last("LIMIT 1");
        return Optional.ofNullable(gripperRequestMapper.selectOne(wrapper));
    }

    @Override
    public boolean existsUnfinishedRequestForDeviceToTargetAndType(Long gripperId, Long targetLocationId, String taskType) {
        QueryWrapper<GripperRequest> wrapper = new QueryWrapper<>();
        wrapper.eq("gripper_id", gripperId)
                .eq("accepted", "N")
                .eq("target_location_id", targetLocationId)
                .eq("task_type", taskType)
                .last("LIMIT 1");

        return gripperRequestMapper.selectCount(wrapper) > 0;
    }

    /**
     * 寫入歷程表（共用方法）
     */
    private void insertHistory(GripperRequest entity, String changeType) {
        gripperHistoryQueueService.offer(entity, changeType);
//        GripperRequestHistory history = new GripperRequestHistory();
//        BeanUtils.copyProperties(entity, history, "id");
//        history.setOriginId(entity.getId());
//        history.setChangeType(changeType);
//        history.setArchivedTime(LocalDateTime.now());
//        gripperRequestHistoryMapper.insert(history);
    }
}
