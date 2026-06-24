package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.MqttMessageLogRepository;
import com.czkuo.rdf88701.infra.entity.MqttMessageLog;
import com.czkuo.rdf88701.infra.mapper.MqttMessageLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * MqttMessageLogRepositoryImpl 實作
 * - 透過 MyBatis-Plus 操作 mqtt_message_log 資料表
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Repository
@RequiredArgsConstructor
public class MqttMessageLogRepositoryImpl implements MqttMessageLogRepository {

    private final MqttMessageLogMapper mqttMessageLogMapper;

    /**
     * 依主鍵查詢單筆紀錄
     */
    @Override
    public Optional<MqttMessageLog> findById(Long id) {
        return Optional.ofNullable(mqttMessageLogMapper.selectById(id));
    }

    /**
     * 新增一筆指令或回應訊息紀錄
     */
    @Override
    public boolean save(MqttMessageLog entity) {
        return mqttMessageLogMapper.insert(entity) > 0;
    }

    @Override
    @Transactional
    public boolean saveBatch(List<MqttMessageLog> entities) {
        if (entities == null || entities.isEmpty()) {
            return true;
        }
        return mqttMessageLogMapper.batchInsert(entities) == entities.size();
    }

    /**
     * 更新既有紀錄（以 ID 為主）
     */
    @Override
    public boolean update(MqttMessageLog entity) {
        return mqttMessageLogMapper.updateById(entity) > 0;
    }

    /**
     * 刪除指定 ID 的紀錄
     */
    @Override
    public boolean deleteById(Long id) {
        return mqttMessageLogMapper.deleteById(id) > 0;
    }

    /**
     * 查詢所有紀錄（小量使用）
     */
    @Override
    public List<MqttMessageLog> findAll() {
        return mqttMessageLogMapper.selectList(new QueryWrapper<>());
    }

    /**
     * 查詢同一 TID 的所有紀錄（COMMAND / ACK）
     */
    @Override
    public List<MqttMessageLog> findAllByTid(String tid) {
        QueryWrapper<MqttMessageLog> wrapper = new QueryWrapper<>();
        wrapper.eq("tid", tid).orderByAsc("timestamp");
        return mqttMessageLogMapper.selectList(wrapper);
    }

    /**
     * 判斷是否存在我方發出的指定指令（COMMAND）
     * 用於辨識對方發來的是不是 ACK
     */
    @Override
    public boolean existsSentCommand(String tid, String cmdId, String sender, String receiver) {
        QueryWrapper<MqttMessageLog> wrapper = new QueryWrapper<>();
        wrapper.eq("tid", tid)
                .eq("cmd_id", cmdId)
                .eq("message_type", "COMMAND")
                .eq("sender", sender)
                .eq("receiver", receiver)
                .last("LIMIT 1");
        return mqttMessageLogMapper.selectCount(wrapper) > 0;
    }

    /**
     * 是否已送過「ACK=START」
     * - 以 tid + cmd_id + (ACK, START) 判斷
     */
    @Override
    public boolean existsAckStart(String tid, String cmdId) {
        QueryWrapper<MqttMessageLog> wrapper = new QueryWrapper<>();
        wrapper.eq("tid", tid)
                .eq("cmd_id", cmdId)
                .eq("message_type", "ACK")
                .eq("result", "START")
                .last("LIMIT 1");
        return mqttMessageLogMapper.selectCount(wrapper) > 0;
    }
}
