package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.SafetyEventLog;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * SafetyEventLogRepository
 *
 * 對應資料表：safety_event_log
 * - 用於紀錄安全點位由「觸發 → 未觸發」或「未觸發 → 觸發」的變更事件
 * - 提供依點位/時間範圍查詢、最近 N 筆查詢、批次新增、統計與歷史清理等常用操作
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public interface SafetyEventLogRepository {

    // ===================== 基本 CRUD =====================

    /**
     * 依主鍵查詢事件紀錄。
     *
     * @param id 事件主鍵 ID
     * @return 事件紀錄（若不存在回傳 Optional.empty()）
     */
    Optional<SafetyEventLog> findById(Long id);

    /**
     * 新增單筆事件紀錄。
     *
     * @param entity 事件紀錄
     * @return 是否新增成功
     */
    boolean save(SafetyEventLog entity);

    /**
     * 批次新增事件紀錄（若需要可在 Mapper 層做 batchInsert 最佳化）。
     *
     * @param entities 事件紀錄清單
     * @return 是否全部新增成功（空清單視為 true）
     */
    boolean saveBatch(List<SafetyEventLog> entities);

    /**
     * 更新單筆事件紀錄（通常事件不會被更新；保留此方法以應對特殊情境）。
     *
     * @param entity 事件紀錄（需含 ID）
     * @return 是否更新成功
     */
    boolean update(SafetyEventLog entity);

    /**
     * 依主鍵刪除事件紀錄（較少用；多用歷史清理法）。
     *
     * @param id 事件主鍵 ID
     * @return 是否刪除成功
     */
    boolean deleteById(Long id);

    /**
     * 查詢所有事件（謹慎使用，資料量大時請改用條件查詢或分頁）。
     *
     * @return 全部事件清單
     */
    List<SafetyEventLog> findAll();

    // ===================== 依點位查詢 =====================

    /**
     * 依點位查詢全部事件（時間多時量大，建議改用時間範圍或最近 N 筆）。
     *
     * @param pointId 安全點位 ID
     * @return 該點位的全部事件（預設依時間升/降序由實作決定）
     */
    List<SafetyEventLog> findAllByPointId(Long pointId);

    /**
     * 依點位與時間區間查詢事件。
     *
     * @param pointId 安全點位 ID
     * @param from    起始時間（含）
     * @param to      結束時間（含）
     * @return 符合條件之事件清單（建議依時間升序回傳）
     */
    List<SafetyEventLog> findByPointIdAndTimeRange(Long pointId, LocalDateTime from, LocalDateTime to);

    /**
     * 查詢某點位最近 N 筆事件（依時間倒序）。
     *
     * @param pointId 安全點位 ID
     * @param limit   取得筆數上限
     * @return 最近的事件清單（最多 N 筆）
     */
    List<SafetyEventLog> findRecentByPointId(Long pointId, int limit);

    // ===================== 全域查詢/統計 =====================

    /**
     * 查詢全域最近 N 筆事件（依時間倒序）。
     *
     * @param limit 取得筆數上限
     * @return 最近的事件清單（最多 N 筆）
     */
    List<SafetyEventLog> findRecentAll(int limit);

    /**
     * 事件總筆數。
     *
     * @return 總筆數
     */
    long count();

    /**
     * 某點位的事件筆數。
     *
     * @param pointId 安全點位 ID
     * @return 筆數
     */
    long countByPointId(Long pointId);

    // ===================== 歷史清理 =====================

    /**
     * 刪除某點位在指定時間之前的歷史事件。
     *
     * @param pointId 安全點位 ID
     * @param cutoff  截止時間（刪除 < cutoff 的資料）
     * @return 刪除筆數
     */
    int deleteByPointIdBefore(Long pointId, LocalDateTime cutoff);

    /**
     * 全域刪除指定時間之前的歷史事件。
     *
     * @param cutoff 截止時間（刪除 < cutoff 的資料）
     * @return 刪除筆數
     */
    int deleteBefore(LocalDateTime cutoff);
}
