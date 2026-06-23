package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.HmiDisplayTask;
import java.util.List;
import java.util.Optional;

/**
 * HMI 顯示任務（plc_hmi_display_task）存取介面
 *
 * <p>用途：
 * <ul>
 *   <li>入列由 S019 指令產生的顯示任務（僅英文寫 PLC）。</li>
 *   <li>由背景 worker 併發安全地撈取 PENDING 任務、寫入 PLC 後標記 SENT/FAILED。</li>
 * </ul>
 * </p>
 *
 * <p>併發建議：
 * <ul>
 *   <li>{@link #pickOnePendingForUpdate()} 需在 {@code @Transactional} 情境下呼叫，資料庫建議 MySQL 8.0（支援
 *   {@code FOR UPDATE SKIP LOCKED}）。</li>
 *   <li>若使用較舊版本資料庫，請改採兩段式「領取標記」設計。</li>
 * </ul>
 * </p>
 */
public interface HmiDisplayTaskRepository {

    // ---------------------------
    // 基本 CRUD
    // ---------------------------

    /** 依主鍵查單筆 */
    Optional<HmiDisplayTask> findById(Long id);

    /** 新增一筆 */
    boolean save(HmiDisplayTask entity);

    /** 以主鍵更新一筆 */
    boolean update(HmiDisplayTask entity);

    /** 以主鍵刪除一筆 */
    boolean deleteById(Long id);

    /** 查詢全部 */
    List<HmiDisplayTask> findAll();

    // ---------------------------
    // 進階查詢 / 併發處理輔助
    // ---------------------------

    /**
     * 依 TID 查詢（冪等用：避免同一 S019 任務重複入列）
     * @param tid S019 的 TID（唯一鍵）
     */
    Optional<HmiDisplayTask> findByTid(String tid);

    /**
     * 取得 PENDING 任務（依建立時間由舊到新），限制筆數
     * <br/>適合單工或低併發情境。高併發請改用 {@link #pickOnePendingForUpdate()}。
     * @param limit 取回筆數上限
     */
    List<HmiDisplayTask> findPendingOrderByCreatedAt(int limit);

    /**
     * 併發安全挑一筆 PENDING（加行鎖）
     * <br/>需在 {@code @Transactional} 下呼叫，且資料庫需支援 {@code FOR UPDATE SKIP LOCKED}。
     * @return 可處理的任務（若暫無任務則回傳 empty）
     */
    Optional<HmiDisplayTask> pickOnePendingForUpdate();

    // ---------------------------
    // 狀態更新工具
    // ---------------------------

    /**
     * 標記任務為 SENT，並更新 sent_at/updated_at
     * @param id 任務主鍵
     */
    boolean markSent(Long id);

    /**
     * 標記任務為 FAILED，並寫入 last_error/updated_at
     * @param id 任務主鍵
     * @param lastError 錯誤描述
     */
    boolean markFailed(Long id, String lastError);

    /**
     * 將 attempts 欄位 +1（重試計數）
     * @param id 任務主鍵
     */
    boolean incrementAttempts(Long id);

    // ---------------------------
    // 給 WPF 輪詢用的增量/歷史查詢
    // ---------------------------

    /**
     * 以自增主鍵做游標：取回「id > afterId」且非 PENDING 的資料
     * 依 id 遞增（ASC）回傳，便於前端依序附加
     */
    List<HmiDisplayTask> findSinceId(long afterId, int limit);

    /**
     * 取回最近的非 PENDING 記錄，依 sent_at/updated_at 由新到舊
     * 適合前端初次載入「最近 N 筆」歷史
     */
    List<HmiDisplayTask> findLatestNonPending(int limit);
}
