package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.czkuo.rdf88701.domain.repository.S072SessionRepository;
import com.czkuo.rdf88701.infra.entity.S072Session;
import com.czkuo.rdf88701.infra.mapper.S072SessionMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Repository
public class S072SessionRepositoryImpl implements S072SessionRepository {

    private final S072SessionMapper s072SessionMapper;

    public S072SessionRepositoryImpl(S072SessionMapper s072SessionMapper) {
        this.s072SessionMapper = s072SessionMapper;
    }

    @Override
    public Optional<S072Session> findById(Long id) {
        return Optional.ofNullable(s072SessionMapper.selectById(id));
    }

    @Override
    public boolean save(S072Session entity) {
        return s072SessionMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(S072Session entity) {
        return s072SessionMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return s072SessionMapper.deleteById(id) > 0;
    }

    @Override
    public List<S072Session> findAll() {
        return s072SessionMapper.selectList(new QueryWrapper<>());
    }

    @Override
    public Optional<S072Session> findActiveByCarrierId(String carrierId) {
        QueryWrapper<S072Session> qw = new QueryWrapper<S072Session>()
                .eq("carrier_id", carrierId)
                .ne("status", "CLOSED")
                .orderByDesc("created_at")
                .orderByDesc("id")
                .last("LIMIT 1");
        return Optional.ofNullable(s072SessionMapper.selectOne(qw));
    }

    @Override
    public Optional<S072Session> findByTid(String tid) {
        QueryWrapper<S072Session> qw = new QueryWrapper<S072Session>()
                .eq("tid", tid)
                .orderByDesc("created_at")
                .orderByDesc("id")
                .last("LIMIT 1");
        return Optional.ofNullable(s072SessionMapper.selectOne(qw));
    }

    @Override
    public boolean updateFirstCapture(Long id, String imagePath1, LocalDateTime capturedAt1) {
        UpdateWrapper<S072Session> uw = new UpdateWrapper<S072Session>()
                .eq("id", id)
                .set(imagePath1 != null, "image_path_1", imagePath1)
                .set(capturedAt1 != null, "captured_at_1", capturedAt1)
                .set("updated_at", LocalDateTime.now());
        return s072SessionMapper.update(null, uw) > 0;
    }

    @Override
    public boolean updateSecondCapture(Long id, String imagePath2, LocalDateTime capturedAt2) {
        UpdateWrapper<S072Session> uw = new UpdateWrapper<S072Session>()
                .eq("id", id)
                .set(imagePath2 != null, "image_path_2", imagePath2)
                .set(capturedAt2 != null, "captured_at_2", capturedAt2)
                .set("updated_at", LocalDateTime.now());
        return s072SessionMapper.update(null, uw) > 0;
    }

    @Override
    public boolean updateTid(Long id, String tid) {
        UpdateWrapper<S072Session> uw = new UpdateWrapper<S072Session>()
                .eq("id", id)
                .set("tid", tid)
                .set("updated_at", LocalDateTime.now());
        return s072SessionMapper.update(null, uw) > 0;
    }

    @Override
    public boolean updateStatus(Long id, String status) {
        UpdateWrapper<S072Session> uw = new UpdateWrapper<S072Session>()
                .eq("id", id)
                .set("status", status)
                .set("updated_at", LocalDateTime.now());
        return s072SessionMapper.update(null, uw) > 0;
    }

    @Override
    public boolean updateTidAndStatus(Long id, String tid, String status) {
        UpdateWrapper<S072Session> uw = new UpdateWrapper<S072Session>()
                .eq("id", id)
                .set("tid", tid)
                .set("status", status)
                .set("updated_at", LocalDateTime.now());
        return s072SessionMapper.update(null, uw) > 0;
    }

    @Override
    public boolean updateStatusByTid(String tid, String status) {
        UpdateWrapper<S072Session> uw = new UpdateWrapper<S072Session>()
                .eq("tid", tid)
                .set("status", status)
                .set("updated_at", LocalDateTime.now());
        return s072SessionMapper.update(null, uw) > 0;
    }

    @Override
    public boolean markAck(Long id, String result, String resultMessage) {
        UpdateWrapper<S072Session> uw = new UpdateWrapper<S072Session>()
                .eq("id", id)
                .set("result", result)
                .set("result_message", resultMessage)
                .set("status", "ACK")
                .set("updated_at", LocalDateTime.now());
        return s072SessionMapper.update(null, uw) > 0;
    }

    @Override
    public boolean markAckByTid(String tid, String result, String resultMessage) {

        if (resultMessage != null && resultMessage.length() >255) {
            resultMessage = resultMessage.substring(0, 256);
        }

        UpdateWrapper<S072Session> uw = new UpdateWrapper<S072Session>()
                .eq("tid", tid)
                .set("result", result)
                .set("result_message", resultMessage)
                .set("status", "ACK")
                .set("updated_at", LocalDateTime.now());
        return s072SessionMapper.update(null, uw) > 0;
    }

    @Override
    public boolean markError(Long id, String errorMessage) {
        UpdateWrapper<S072Session> uw = new UpdateWrapper<S072Session>()
                .eq("id", id)
                .set("error_message", errorMessage)
                .set("status", "ERROR")
                .set("updated_at", LocalDateTime.now());
        return s072SessionMapper.update(null, uw) > 0;
    }

    @Override
    public boolean markErrorByTid(String tid, String errorMessage) {
        UpdateWrapper<S072Session> uw = new UpdateWrapper<S072Session>()
                .eq("tid", tid)
                .set("error_message", errorMessage)
                .set("status", "ERROR")
                .set("updated_at", LocalDateTime.now());
        return s072SessionMapper.update(null, uw) > 0;
    }

    @Override
    public boolean close(Long id) {
        UpdateWrapper<S072Session> uw = new UpdateWrapper<S072Session>()
                .eq("id", id)
                .set("status", "CLOSED")
                .set("updated_at", LocalDateTime.now());
        return s072SessionMapper.update(null, uw) > 0;
    }

    @Override
    public List<S072Session> findByStatus(String status, int limit) {
        QueryWrapper<S072Session> qw = new QueryWrapper<S072Session>()
                .eq("status", status)
                .orderByAsc("created_at")
                .last("LIMIT " + Math.max(1, limit));
        return s072SessionMapper.selectList(qw);
    }

    @Override
    public int closeAllActiveByCarrierId(String carrierId) {
        UpdateWrapper<S072Session> uw = new UpdateWrapper<S072Session>()
                .eq("carrier_id", carrierId)
                .ne("status", "CLOSED")
                .set("status", "CLOSED")
                .set("updated_at", LocalDateTime.now());
        return s072SessionMapper.update(null, uw);
    }
}
