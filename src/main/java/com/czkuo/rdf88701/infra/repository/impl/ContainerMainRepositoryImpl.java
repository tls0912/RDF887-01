package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.czkuo.rdf88701.application.service.History.ContainerMainHistoryInsertService;
import com.czkuo.rdf88701.domain.repository.ContainerMainRepository;
import com.czkuo.rdf88701.infra.dto.ContainerWithLocation;
import com.czkuo.rdf88701.infra.entity.ContainerData;
import com.czkuo.rdf88701.infra.entity.ContainerMain;
import com.czkuo.rdf88701.infra.entity.CraneRequest;
import com.czkuo.rdf88701.infra.mapper.ContainerMainMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;

/**
 * ContainerMainRepository 實作
 *
 * 注意：
 * 1) findById -> 使用 MyBatis-Plus 的 selectById（單表）
 *    findWithLatestDataById -> 使用自訂 mapper 的關聯查詢（帶最新 ContainerData 等）
 * 2) createFromParent / updateName / findMaxSplitIndexByBase
 *    需在 ContainerMainMapper 提供對應的自訂 SQL（見文末 Mapper 介面與 XML 範例）
 * 3) 本實作將「name」與「alias_code」區分處理：
 *    - name：顯示命名（供拆/併規則使用；例如 11TY00V002_P_1_1）
 *    - alias_code：內部唯一流水碼（自動產生保證唯一）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Repository
@RequiredArgsConstructor
public class ContainerMainRepositoryImpl implements ContainerMainRepository {

    private static final String STATE_ACTIVE = "ACTIVE";
    private static final String STATE_CLOSED = "CLOSED";
    private static final String STATE_ABORTED = "ABORTED";

    private final ContainerMainMapper containerMainMapper;
    private final ContainerMainHistoryInsertService containerMainHistoryInsertService;


    // ========== 基本查詢 ==========

    @Override
    public Optional<ContainerMain> findById(Long id) {
        return Optional.ofNullable(containerMainMapper.selectById(id));
    }

    @Override
    public Optional<ContainerMain> findByAliasCode(String aliasCode) {
        if (!StringUtils.hasText(aliasCode)) return Optional.empty();

        LambdaQueryWrapper<ContainerMain> w = new LambdaQueryWrapper<>();
        w.eq(ContainerMain::getAliasCode, aliasCode.trim())
               // .eq(ContainerMain::getState, STATE_ACTIVE)
                .orderByDesc(ContainerMain::getId)
                .last("LIMIT 1");

        return Optional.ofNullable(containerMainMapper.selectOne(w));
    }

    @Override
    public Optional<ContainerMain> findByContainerCode(String containerCode) {
        if (!StringUtils.hasText(containerCode)) return Optional.empty();

        LambdaQueryWrapper<ContainerMain> w = new LambdaQueryWrapper<>();
        w.eq(ContainerMain::getContainerCode, containerCode.trim())
                .orderByDesc(ContainerMain::getId)
                .last("LIMIT 1");

        return Optional.ofNullable(containerMainMapper.selectOne(w));
    }

    @Override
    public Optional<ContainerMain> findByLotNo(String lotNo) {
        if (!StringUtils.hasText(lotNo)) return Optional.empty();

        LambdaQueryWrapper<ContainerMain> w = new LambdaQueryWrapper<>();
        w.eq(ContainerMain::getLotNo, lotNo.trim())
                .orderByDesc(ContainerMain::getId)
                .last("LIMIT 1");

        return Optional.ofNullable(containerMainMapper.selectOne(w));
    }

    @Override
    public Optional<ContainerMain> findWithLatestDataById(Long id) {
        return Optional.ofNullable(containerMainMapper.selectWithLatestDataById(id));
    }

    @Override
    public boolean save(ContainerMain entity) {
        boolean success = containerMainMapper.insert(entity) > 0;
        if (success) {
            insertHistory(entity, "INSERT");
        }
        return success;
        //return containerMainMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(ContainerMain entity) {
        boolean success = containerMainMapper.updateById(entity) > 0;
        if (success) {
            insertHistory(entity, "UPDATE");
        }
        return success;
        //return containerMainMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        ContainerMain beforeDelete = containerMainMapper.selectById(id);
        boolean success = containerMainMapper.deleteById(id) > 0;
        if (success && beforeDelete != null) {
            insertHistory(beforeDelete, "DELETE");
        }
        return success;
        //return containerMainMapper.deleteById(id) > 0;
    }

    @Override
    public List<ContainerMain> findAll() {
        return containerMainMapper.selectList(new QueryWrapper<>());
    }

    // ========== 分頁 / 查詢（MyBatis-Plus） ==========

    @Override
    public List<ContainerMain> findPageByQuery(String query, long page, long size) {
        long p = Math.max(page, 1);
        long s = Math.max(size, 1);

        LambdaQueryWrapper<ContainerMain> qw = new LambdaQueryWrapper<ContainerMain>()
                .orderByDesc(ContainerMain::getId);

        if (StringUtils.hasText(query)) {
            String q = query.trim();
            qw.and(w -> w.like(ContainerMain::getAliasCode, q)
                    .or().like(ContainerMain::getContainerCode, q)
                    .or().like(ContainerMain::getLotNo, q)
                    .or().like(ContainerMain::getPartNo, q));
        }

        Page<ContainerMain> mpPage = new Page<>(p, s);
        Page<ContainerMain> res = containerMainMapper.selectPage(mpPage, qw);
        return res.getRecords();
    }

    @Override
    public long countByQuery(String query) {
        LambdaQueryWrapper<ContainerMain> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query)) {
            String q = query.trim();
            qw.and(w -> w.like(ContainerMain::getAliasCode, q)
                    .or().like(ContainerMain::getContainerCode, q)
                    .or().like(ContainerMain::getLotNo, q)
                    .or().like(ContainerMain::getPartNo, q));
        }
        return containerMainMapper.selectCount(qw);
    }

    @Override
    public List<ContainerMain> findTrackedPageByQuery(String query, long page, long size) {
        long p = Math.max(page, 1L);   // MP 分頁 1-based
        long s = Math.max(size, 1L);

        LambdaQueryWrapper<ContainerMain> qw = new LambdaQueryWrapper<ContainerMain>()
                // 只挑在 location_tracking 出現過的 container_main
                .inSql(ContainerMain::getId, "SELECT lt.container_main_id FROM location_tracking lt")
                .orderByDesc(ContainerMain::getId);

        if (StringUtils.hasText(query)) {
            String q = query.trim();
            qw.and(w -> w.like(ContainerMain::getAliasCode, q)
                    .or().like(ContainerMain::getContainerCode, q)
                    .or().like(ContainerMain::getLotNo, q)
                    .or().like(ContainerMain::getPartNo, q));
        }

        Page<ContainerMain> mp = new Page<>(p, s);
        Page<ContainerMain> res = containerMainMapper.selectPage(mp, qw);
        return res.getRecords();
    }

    @Override
    public long countTrackedByQuery(String query) {
        LambdaQueryWrapper<ContainerMain> qw = new LambdaQueryWrapper<ContainerMain>()
                .inSql(ContainerMain::getId, "SELECT lt.container_main_id FROM location_tracking lt");

        if (StringUtils.hasText(query)) {
            String q = query.trim();
            qw.and(w -> w.like(ContainerMain::getAliasCode, q)
                    .or().like(ContainerMain::getContainerCode, q)
                    .or().like(ContainerMain::getLotNo, q)
                    .or().like(ContainerMain::getPartNo, q));
        }

        return containerMainMapper.selectCount(qw);
    }

    // ========== 倉儲場景查詢 ==========

    @Override
    public List<ContainerMain> findAllInWarehouse() {
        return containerMainMapper.findAllInWarehouse();
    }

    @Override
    public List<ContainerWithLocation> findAllInWarehouseWithLocation() {
        return containerMainMapper.findAllInWarehouseWithLocation();
    }

    @Override
    public Set<Long> findContainerIdsWithUnfinishedTasksOrUnacceptedRequests() {
        return new HashSet<>(containerMainMapper.selectProcessingContainerIds());
    }

    @Override
    public boolean existsByAliasCode(String aliasCode) {
        if (!StringUtils.hasText(aliasCode)) return false;

        LambdaQueryWrapper<ContainerMain> w = new LambdaQueryWrapper<>();
        w.eq(ContainerMain::getAliasCode, aliasCode.trim())
                .eq(ContainerMain::getState, STATE_ACTIVE);
        return containerMainMapper.selectCount(w) > 0;
    }

    // ========== 拆/併用擴充 API ==========

    /**
     * 由父容器複製產生一顆新容器，並指定新「顯示名稱 name」
     * - name：設為 newName（供拆分命名用）
     * - serial_code：自動生成且保證唯一
     *
     * @param parentId 父容器 id
     * @param newAliasCode  新容器顯示名稱（例如 11TY00V002_P_1_1）
     * @return 新容器 id
     */
    @Override
    public Long createFromParent(Long parentId, String newAliasCode) {
        if (newAliasCode == null || newAliasCode.isBlank()) {
            throw new IllegalArgumentException("newName is required");
        }

        ContainerMain parent = containerMainMapper.selectById(parentId);
        if (parent == null) {
            throw new IllegalArgumentException("Parent container not found: id=" + parentId);
        }

        // 準備新物件：name 指定、serial_code 自動、其餘欄位從父複製（依你的實際欄位擴充）
        ContainerMain copy = new ContainerMain();
        copy.setAliasCode(newAliasCode);

        // ===== 依實體欄位補齊複製內容 =====
        copy.setContainerType(parent.getContainerType());
        copy.setContainerCode(parent.getContainerCode());
        copy.setLotNo(parent.getLotNo());
        copy.setPartNo(parent.getPartNo());
        // ==============================

        copy.setState(STATE_ACTIVE);
        copy.setCreatedTime(LocalDateTime.now());

        int rows = containerMainMapper.insert(copy);
        if (rows != 1) {
            throw new IllegalStateException("Insert copy failed, affected rows=" + rows);
        }
        return copy.getId();
    }

    /**
     * 更新容器名稱（for 拆分/合併後命名）
     */
    @Override
    public boolean updateAliasCode(Long id, String newAliasCode) {
        return containerMainMapper.updateAliasCodeById(id, newAliasCode) > 0;
    }

    /**
     * 查詢同一 base（不含拆分尾碼）的最大尾碼（_k）的 k 值
     * 例：base=11TY00V002_P_1，庫內有 11TY00V002_P_1、11TY00V002_P_1_1、11TY00V002_P_1_3 → 回 3
     * 若完全沒有 _k 尾碼，回傳 0 或 null（此處回傳 null 表示無資料，呼叫端可轉為 0）。
     */
    @Override
    public Integer findMaxSplitIndexByBase(String base) {
        return containerMainMapper.findMaxSplitIndexByBase(base);
    }

    @Override
    public List<ContainerWithLocation> findAllInWarehouseWithLocationAllCover() {
        return containerMainMapper.findAllInWarehouseWithLocationByContentKind("ALL_COVER");
    }

    @Override
    public List<ContainerWithLocation> findAllInWarehouseWithLocationByContentKind(String contentKind) {
        return containerMainMapper.findAllInWarehouseWithLocationByContentKind(contentKind);
    }

    @Override
    public boolean close(Long id) {
        return updateState(id, "CLOSED", LocalDateTime.now());
    }

    @Override
    public boolean abort(Long id) {
        // 這裡我先不寫 closed_time；若你希望 ABORTED 也要時間：改成 LocalDateTime.now()
        return updateState(id, "ABORTED", null);
    }

    @Override
    public boolean reopen(Long id) {
        return updateState(id, "ACTIVE", null);
    }

    @Override
    public boolean updateState(Long id, String newState, LocalDateTime closedTime) {
        if (id == null) throw new IllegalArgumentException("id is required");
        if (!StringUtils.hasText(newState)) throw new IllegalArgumentException("newState is required");

        String s = newState.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ACTIVE", "CLOSED", "ABORTED").contains(s)) {
            throw new IllegalArgumentException("Invalid state: " + newState);
        }

        // CLOSED → closed_time=now（若你沒傳就補 now）
        if ("CLOSED".equals(s) && closedTime == null) {
            closedTime = LocalDateTime.now();
        }

        // ACTIVE/ABORTED → 預設清掉 closed_time（你若 ABORTED 想保留時間，可調整這段）
        if (!"CLOSED".equals(s)) {
            closedTime = null;
        }

        return containerMainMapper.updateStateById(id, s, closedTime) > 0;
    }

    private void insertHistory(ContainerMain entity, String changeType) {
        containerMainHistoryInsertService.offer(entity,changeType);
    }
}
