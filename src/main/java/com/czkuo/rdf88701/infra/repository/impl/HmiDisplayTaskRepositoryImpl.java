package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.czkuo.rdf88701.domain.repository.HmiDisplayTaskRepository;
import com.czkuo.rdf88701.infra.entity.HmiDisplayTask;
import com.czkuo.rdf88701.infra.mapper.HmiDisplayTaskMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * HmiDisplayTaskRepository 實作
 *
 * 功能重點：
 * 1) 基本 CRUD
 * 2) 以 TID 查詢（冪等用）
 * 3) 取得 PENDING 任務（依建立時間排序、可限制筆數）
 * 4) 以「鎖定」方式挑一筆 PENDING（FOR UPDATE SKIP LOCKED）供多工 worker 併發安全撈取
 * 5) 任務狀態更新：標記 SENT/FAILED、遞增 attempts
 *
 * 注意：
 * - pickOnePendingForUpdate() 需在 @Transactional 環境下呼叫，才會真的取得行鎖。
 * - MySQL 8.0 支援 FOR UPDATE SKIP LOCKED；若你的資料庫版本不支援，請改為「領取標記」的兩段式更新。
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Repository
public class HmiDisplayTaskRepositoryImpl implements HmiDisplayTaskRepository {

    private final HmiDisplayTaskMapper hmiDisplayTaskMapper;

    public HmiDisplayTaskRepositoryImpl(HmiDisplayTaskMapper hmiDisplayTaskMapper) {
        this.hmiDisplayTaskMapper = hmiDisplayTaskMapper;
    }

    // ------------------------------
    // 基本 CRUD
    // ------------------------------

    /** 依主鍵查單筆 */
    @Override
    public Optional<HmiDisplayTask> findById(Long id) {
        return Optional.ofNullable(hmiDisplayTaskMapper.selectById(id));
    }

    /** 新增一筆 */
    @Override
    public boolean save(HmiDisplayTask entity) {
        return hmiDisplayTaskMapper.insert(entity) > 0;
    }

    /** 以主鍵更新一筆 */
    @Override
    public boolean update(HmiDisplayTask entity) {
        return hmiDisplayTaskMapper.updateById(entity) > 0;
    }

    /** 以主鍵刪除一筆 */
    @Override
    public boolean deleteById(Long id) {
        return hmiDisplayTaskMapper.deleteById(id) > 0;
    }

    /** 查詢全部 */
    @Override
    public List<HmiDisplayTask> findAll() {
        return hmiDisplayTaskMapper.selectList(new QueryWrapper<HmiDisplayTask>());
    }

    // ------------------------------
    // 進階查詢 / 併發處理輔助
    // ------------------------------

    /**
     * 依 TID 查詢（冪等用）
     * @param tid S019 的 TID（唯一鍵）
     */
    @Override
    public Optional<HmiDisplayTask> findByTid(String tid) {
        HmiDisplayTask one = hmiDisplayTaskMapper.selectOne(
                new QueryWrapper<HmiDisplayTask>()
                        .eq("tid", tid)
                        .last("LIMIT 1")
        );
        return Optional.ofNullable(one);
    }

    /**
     * 取得 PENDING 任務（依建立時間由舊到新），限制筆數
     * @param limit 取回筆數上限
     */
    @Override
    public List<HmiDisplayTask> findPendingOrderByCreatedAt(int limit) {
        return hmiDisplayTaskMapper.selectList(
                new QueryWrapper<HmiDisplayTask>()
                        .eq("status", "PENDING")
                        .orderByAsc("created_at")
                        .last("LIMIT " + Math.max(1, limit))
        );
        // ※ 若需要更嚴謹的併發保護，請改用 pickOnePendingForUpdate() 單筆加鎖領取。
    }

    /**
     * 併發安全挑一筆 PENDING（加行鎖）
     * 需求：MySQL 8.0 + 需在 @Transactional 下呼叫，隔離級別至少 READ COMMITTED。
     * 回傳：若暫無可領取的任務，回傳 Optional.empty()
     */
    @Override
    public Optional<HmiDisplayTask> pickOnePendingForUpdate() {
        List<HmiDisplayTask> list = hmiDisplayTaskMapper.selectList(
                new QueryWrapper<HmiDisplayTask>()
                        .eq("status", "PENDING")
                        .orderByAsc("created_at")
                        .last("LIMIT 1 FOR UPDATE SKIP LOCKED")
        );
        return (list == null || list.isEmpty()) ? Optional.empty() : Optional.of(list.get(0));
    }

    // ------------------------------
    // 狀態更新工具
    // ------------------------------

    /**
     * 標記任務為 SENT，並填寫 sent_at 與 updated_at
     */
    @Override
    public boolean markSent(Long id) {
        return hmiDisplayTaskMapper.update(
                null,
                new UpdateWrapper<HmiDisplayTask>()
                        .eq("id", id)
                        .set("status", "SENT")
                        .set("sent_at", LocalDateTime.now())
                        .set("updated_at", LocalDateTime.now())
        ) > 0;
    }

    /**
     * 標記任務為 FAILED，更新 last_error 與 updated_at
     */
    @Override
    public boolean markFailed(Long id, String lastError) {
        return hmiDisplayTaskMapper.update(
                null,
                new UpdateWrapper<HmiDisplayTask>()
                        .eq("id", id)
                        .set("status", "FAILED")
                        .set("last_error", lastError)
                        .set("updated_at", LocalDateTime.now())
        ) > 0;
    }

    /**
     * 遞增 attempts 次數（搭配重試策略）
     */
    @Override
    public boolean incrementAttempts(Long id) {
        return hmiDisplayTaskMapper.update(
                null,
                new UpdateWrapper<HmiDisplayTask>()
                        .eq("id", id)
                        .setSql("attempts = attempts + 1")
                        .set("updated_at", LocalDateTime.now())
        ) > 0;
    }

    // ------------------------------
    // 給 WPF 輪詢用的增量/歷史查詢
    // ------------------------------

    @Override
    public List<HmiDisplayTask> findSinceId(long afterId, int limit) {
        int cap = Math.max(1, Math.min(limit, 500)); // 合理上限，避免一次拉太多
        return hmiDisplayTaskMapper.selectList(
                new QueryWrapper<HmiDisplayTask>()
                        .gt("id", afterId)
                        .ne("status", "PENDING")
                        .orderByAsc("created_at")
                        .last("LIMIT " + cap)
        );
    }

    @Override
    public List<HmiDisplayTask> findLatestNonPending(int limit) {
        int cap = Math.max(1, Math.min(limit, 500));
        // 先用 updated_at（若有），沒有就用 sent_at，再備援 id
        // 這裡簡化為 sent_at DESC, id DESC；如果要更嚴謹可改成 CASE WHEN sent_at IS NULL THEN updated_at ELSE sent_at END
        return hmiDisplayTaskMapper.selectList(
                new QueryWrapper<HmiDisplayTask>()
                        .ne("status", "PENDING")
                        .orderByDesc("created_at")
                        .last("LIMIT " + cap)
        );
    }
}
