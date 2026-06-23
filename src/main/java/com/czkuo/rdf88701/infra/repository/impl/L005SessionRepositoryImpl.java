package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.czkuo.rdf88701.domain.repository.L005SessionRepository;
import com.czkuo.rdf88701.infra.entity.L005Session;
import com.czkuo.rdf88701.infra.mapper.L005SessionMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class L005SessionRepositoryImpl implements L005SessionRepository {

    private final L005SessionMapper l005SessionMapper;

    public L005SessionRepositoryImpl(L005SessionMapper l005SessionMapper) {
        this.l005SessionMapper = l005SessionMapper;
    }

    @Override
    public Optional<L005Session> findById(Long id) {
        return Optional.ofNullable(l005SessionMapper.selectById(id));
    }

    @Override
    public boolean save(L005Session entity) {
        return l005SessionMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(L005Session entity) {
        return l005SessionMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return l005SessionMapper.deleteById(id) > 0;
    }

    @Override
    public List<L005Session> findAll() {
        return l005SessionMapper.selectList(new QueryWrapper<>());
    }

    // ================= 查詢 =================

    @Override
    public Optional<L005Session> findByTid(String tid) {
        if (tid == null || tid.isBlank()) return Optional.empty();
        QueryWrapper<L005Session> qw = new QueryWrapper<L005Session>()
                .eq("tid", tid.trim())
                .last("LIMIT 1");
        return Optional.ofNullable(l005SessionMapper.selectOne(qw));
    }

    @Override
    public Optional<L005Session> findActiveByBarcode(String barcode) {
        if (barcode == null || barcode.isBlank()) return Optional.empty();
        QueryWrapper<L005Session> qw = new QueryWrapper<L005Session>()
                .eq("barcode", barcode.trim())
                .eq("is_valid", 1)
                .orderByDesc("created_at")
                .last("LIMIT 1");
        return Optional.ofNullable(l005SessionMapper.selectOne(qw));
    }

    @Override
    public Optional<String> findCurrentTidByBarcode(String barcode) {
        return findActiveByBarcode(barcode).map(L005Session::getTid);
    }

    @Override
    public List<L005Session> findByBarcode(String barcode) {
        QueryWrapper<L005Session> qw = new QueryWrapper<L005Session>()
                .eq("barcode", barcode)
                .orderByDesc("created_at");
        return l005SessionMapper.selectList(qw);
    }

    @Override
    public List<L005Session> findRecentByBarcode(String barcode, int limit) {
        QueryWrapper<L005Session> qw = new QueryWrapper<L005Session>()
                .eq("barcode", barcode)
                .orderByDesc("created_at")
                .last("LIMIT " + Math.max(1, limit));
        return l005SessionMapper.selectList(qw);
    }

    @Override
    public Optional<L005Session> findLatestByPeerCarrierId(String carrierId) {
        if (carrierId == null || carrierId.isBlank()) return Optional.empty();
        QueryWrapper<L005Session> qw = new QueryWrapper<L005Session>()
                .eq("peer_carrier_id", carrierId.trim())
                .orderByDesc("created_at")
                .last("LIMIT 1");
        return Optional.ofNullable(l005SessionMapper.selectOne(qw));
    }

    // ================= 狀態更新 =================

    @Override
    public boolean invalidateAllActiveByBarcode(String barcode, String newTid) {
        if (barcode == null || barcode.isBlank() || newTid == null || newTid.isBlank())
            return false;
        UpdateWrapper<L005Session> uw = new UpdateWrapper<L005Session>()
                .eq("barcode", barcode.trim())
                .eq("is_valid", 1)
                .set("is_valid", 0)
                .set("invalid_by_tid", newTid.trim())
                .set("updated_at", new Timestamp(System.currentTimeMillis()));
        return l005SessionMapper.update(null, uw) > 0;
    }

    @Override
    public boolean updatePeerAckByTid(
            String tid,
            String result, String resultMsg,
            String carrierId, String lotId,
            String trayHigh, String trayType, String msgType,
            String payloadJson
    ) {
        if (tid == null || tid.isBlank()) return false;

        UpdateWrapper<L005Session> uw = new UpdateWrapper<L005Session>()
                .eq("tid", tid.trim())
                .set("peer_result", safeEnum(result))
                .set("peer_result_msg", nvl(resultMsg))
                .set("peer_carrier_id", nvl(carrierId))
                .set("peer_lot_id", nvl(lotId))
                .set("peer_tray_high", nvl(trayHigh))
                .set("peer_tray_type", nvl(trayType))
                .set("peer_msg_type", nvl(msgType))
                .setSql("peer_ack_at = COALESCE(peer_ack_at, NOW())")
                .set("peer_ack_payload_json", payloadJson)
                .set("updated_at", new Timestamp(System.currentTimeMillis()));
        return l005SessionMapper.update(null, uw) > 0;
    }

    @Override
    public boolean updateInternalStateByTid(String tid, String internalState, String failReason) {
        if (tid == null || tid.isBlank() || internalState == null || internalState.isBlank())
            return false;
        UpdateWrapper<L005Session> uw = new UpdateWrapper<L005Session>()
                .eq("tid", tid.trim())
                .set("internal_state", internalState.trim().toUpperCase())
                .set("fail_reason", nvl(failReason))
                .set("updated_at", new Timestamp(System.currentTimeMillis()));
        return l005SessionMapper.update(null, uw) > 0;
    }

    @Override
    public boolean updateExternalResultByTid(String tid, String result, String reason) {
        if (tid == null || tid.isBlank()) return false;
        UpdateWrapper<L005Session> uw = new UpdateWrapper<L005Session>()
                .eq("tid", tid.trim())
                .set("external_last_result", nvl(result))
                .set("external_last_time", new Timestamp(System.currentTimeMillis()))
                .set("fail_reason", nvl(reason))
                .set("updated_at", new Timestamp(System.currentTimeMillis()));
        return l005SessionMapper.update(null, uw) > 0;
    }

    // ================= utils =================

    private static String nvl(String s) { return s == null ? "" : s; }

    /** 安全規整對方結果（允許 PASS/FAIL/空字串） */
    private static String safeEnum(String result) {
        if (result == null) return "";
        String r = result.trim().toUpperCase();
        return ("PASS".equals(r) || "FAIL".equals(r)) ? r : "";
    }
}
