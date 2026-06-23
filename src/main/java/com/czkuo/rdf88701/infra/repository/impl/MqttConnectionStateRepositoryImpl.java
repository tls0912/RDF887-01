package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.czkuo.rdf88701.domain.repository.MqttConnectionStateRepository;
import com.czkuo.rdf88701.infra.entity.MqttConnectionState;
import com.czkuo.rdf88701.infra.mapper.MqttConnectionStateMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * MqttConnectionStateRepositoryImpl
 * - 使用 MyBatis Plus 操作 mqtt_connection_state 資料表
 * - 管理對方系統的 MQTT 連線狀態
 */
@Repository
public class MqttConnectionStateRepositoryImpl implements MqttConnectionStateRepository {

    private final MqttConnectionStateMapper mqttConnectionStateMapper;

    public MqttConnectionStateRepositoryImpl(MqttConnectionStateMapper mqttConnectionStateMapper) {
        this.mqttConnectionStateMapper = mqttConnectionStateMapper;
    }

    /**
     * 根據主鍵 ID 查詢紀錄
     */
    @Override
    public Optional<MqttConnectionState> findById(Long id) {
        return Optional.ofNullable(mqttConnectionStateMapper.selectById(id));
    }

    /**
     * 新增一筆連線狀態
     */
    @Override
    public boolean insert(MqttConnectionState entity) {
        return mqttConnectionStateMapper.insert(entity) > 0;
    }

    /**
     * 更新一筆連線狀態（依據主鍵）
     */
    @Override
    public boolean update(MqttConnectionState entity) {
        return mqttConnectionStateMapper.updateById(entity) > 0;
    }

    /**
     * 根據主鍵 ID 刪除紀錄
     */
    @Override
    public boolean deleteById(Long id) {
        return mqttConnectionStateMapper.deleteById(id) > 0;
    }

    /**
     * 查詢所有連線狀態清單
     */
    @Override
    public List<MqttConnectionState> findAll() {
        return mqttConnectionStateMapper.selectList(new LambdaQueryWrapper<>());
    }

    /**
     * 根據 remoteSystem 查詢對應狀態紀錄
     */
    @Override
    public Optional<MqttConnectionState> findByRemoteSystem(String remoteSystem) {
        LambdaQueryWrapper<MqttConnectionState> query = new LambdaQueryWrapper<>();
        query.eq(MqttConnectionState::getRemoteSystem, remoteSystem);
        return Optional.ofNullable(mqttConnectionStateMapper.selectOne(query));
    }

    /**
     * 依 remoteSystem 執行 upsert（有則更新，無則新增）
     */
    @Override
    public boolean upsertByRemoteSystem(MqttConnectionState entity) {
        Optional<MqttConnectionState> existing = findByRemoteSystem(entity.getRemoteSystem());
        if (existing.isPresent()) {
            entity.setId(existing.get().getId());
            return update(entity);
        } else {
            return insert(entity);
        }
    }

    /**
     * 查詢目前處於斷線狀態的所有對象（connected = false）
     */
    @Override
    public List<MqttConnectionState> findDisconnectedSystems() {
        return mqttConnectionStateMapper.selectList(
                new LambdaQueryWrapper<MqttConnectionState>()
                        .eq(MqttConnectionState::getConnected, false)
        );
    }

    /**
     * 查詢心跳逾時對象（目前已連線但 last_heartbeat_time < now - timeout）
     */
    @Override
    public List<MqttConnectionState> findExpiredHeartbeat(long timeoutSeconds) {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(timeoutSeconds);
        return mqttConnectionStateMapper.selectList(
                new LambdaQueryWrapper<MqttConnectionState>()
                        .eq(MqttConnectionState::getConnected, true)
                        .lt(MqttConnectionState::getLastHeartbeatTime, cutoff)
        );
    }

    /**
     * 更新指定對象的最後心跳時間
     */
    @Override
    public boolean updateHeartbeatTime(String remoteSystem, LocalDateTime heartbeatTime) {
        LambdaUpdateWrapper<MqttConnectionState> update = new LambdaUpdateWrapper<>();
        update.eq(MqttConnectionState::getRemoteSystem, remoteSystem)
                .set(MqttConnectionState::getLastHeartbeatTime, heartbeatTime);
        return mqttConnectionStateMapper.update(null, update) > 0;
    }

    /**
     * 將指定對象狀態標記為已斷線（connected = false）
     */
    @Override
    public boolean markAsDisconnected(String remoteSystem) {
        LambdaUpdateWrapper<MqttConnectionState> update = new LambdaUpdateWrapper<>();
        update.eq(MqttConnectionState::getRemoteSystem, remoteSystem)
                .set(MqttConnectionState::getConnected, false);
        return mqttConnectionStateMapper.update(null, update) > 0;
    }

    /**
     * 檢查指定系統是否為連線中（connected = true）
     */
    @Override
    public boolean isConnected(String remoteSystem) {
        return findByRemoteSystem(remoteSystem)
                .map(MqttConnectionState::getConnected)
                .orElse(false);
    }

    /**
     * 查詢所有目前已連線的對象系統代號清單
     */
    @Override
    public List<String> listCurrentlyConnectedSystems() {
        return mqttConnectionStateMapper.selectList(
                        new LambdaQueryWrapper<MqttConnectionState>()
                                .eq(MqttConnectionState::getConnected, true)
                ).stream()
                .map(MqttConnectionState::getRemoteSystem)
                .collect(Collectors.toList());
    }

    /**
     * 回傳所有系統的目前連線狀態對照表（Map<系統代號, 是否連線>）
     */
    @Override
    public Map<String, Boolean> getAllConnectionStatusMap() {
        List<MqttConnectionState> list = mqttConnectionStateMapper.selectList(null);
        Map<String, Boolean> result = new HashMap<>();
        for (MqttConnectionState state : list) {
            result.put(state.getRemoteSystem(), state.getConnected());
        }
        return result;
    }
}
