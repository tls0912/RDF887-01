package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.RobotInR029LotRepository;
import com.czkuo.rdf88701.infra.entity.RobotInR029Lot;
import com.czkuo.rdf88701.infra.mapper.RobotInR029LotMapper;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Repository
public class RobotInR029LotRepositoryImpl implements RobotInR029LotRepository {

    private final RobotInR029LotMapper robotInR029LotMapper;

    public RobotInR029LotRepositoryImpl(RobotInR029LotMapper robotInR029LotMapper) {
        this.robotInR029LotMapper = robotInR029LotMapper;
    }

    // ===== 既有 CRUD =====

    @Override
    public Optional<RobotInR029Lot> findById(Long id) {
        return Optional.ofNullable(robotInR029LotMapper.selectById(id));
    }

    @Override
    public boolean save(RobotInR029Lot entity) {
        return robotInR029LotMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(RobotInR029Lot entity) {
        return robotInR029LotMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return robotInR029LotMapper.deleteById(id) > 0;
    }

    @Override
    public List<RobotInR029Lot> findAll() {
        return robotInR029LotMapper.selectList(new QueryWrapper<>());
    }

    // ===== 擴充 API =====

    @Override
    public List<RobotInR029Lot> findByLogId(Long logId) {
        return robotInR029LotMapper.selectList(
                new QueryWrapper<RobotInR029Lot>()
                        .eq("log_id", logId)
                        .orderByAsc("id")
        );
    }

    @Override
    public List<String> findCarrierIdsByLogId(Long logId) {
        // 兩種作法：1) 直接 selectList 再 map；2) 走自訂 selectLotIdsByLogId（效能較佳）
        // 這裡採 2)，需搭配對應的 Mapper XML
        List<String> ids = robotInR029LotMapper.selectLotIdsByLogId(logId);
        return ids != null ? ids : Collections.emptyList();
    }

    @Override
    public List<String> findIdByCarrierId(String carrierId) {
        List<String> ids = robotInR029LotMapper.selectIdByLotId(carrierId);
        return ids != null ? ids : Collections.emptyList();
    }

    @Override
    public boolean batchUpsert(Long logId, List<String> lotIds) {
        if (logId == null || lotIds == null || lotIds.isEmpty()) return false;
        // 去空白 + 去重
        List<String> cleaned = lotIds.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
        if (cleaned.isEmpty()) return false;

        // 使用 INSERT IGNORE 批量寫入（依 DB 語法；MySQL 可用）
        int affected = robotInR029LotMapper.bulkInsertIgnore(logId, cleaned);
        // 可能因為 IGNORE 全部是重複而回傳 0，這個行為是合理的
        return affected >= 0;
    }

    @Override
    public boolean deleteByLogIdAndLotId(Long logId, String lotId) {
        if (logId == null || lotId == null) return false;
        return robotInR029LotMapper.delete(
                new QueryWrapper<RobotInR029Lot>()
                        .eq("log_id", logId)
                        .eq("lot_id", lotId)
        ) > 0;
    }

    @Override
    public boolean deleteByLogId(Long logId) {
        if (logId == null) return false;
        return robotInR029LotMapper.delete(
                new QueryWrapper<RobotInR029Lot>()
                        .eq("log_id", logId)
        ) > 0;
    }
}
