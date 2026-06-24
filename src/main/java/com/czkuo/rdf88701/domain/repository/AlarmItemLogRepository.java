package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.AlarmItemLog;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * AlarmItemLogRepository
 * ------------------------------------------------------------
 * 對應 alarm_item_log 的 DDD Repository。
 *
 * 建議：此表為「append-only」審計表（只 INSERT，不 UPDATE/DELETE）。
 * 若需嚴格保障，請用 DB 權限或觸發器阻擋更新/刪除。
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public interface AlarmItemLogRepository {

    // =========================
    // 基本 CRUD（僅供工具情境）
    // =========================

    /** 依 PK 取得 */
    Optional<AlarmItemLog> findById(Long id);

    /** 新增（append；成功回 true） */
    boolean save(AlarmItemLog entity);

    /**
     * 更新（不建議在正式環境使用；審計表應 append-only）
     * 僅供修正極端錯誤時的管理操作
     */
    boolean update(AlarmItemLog entity);

    /**
     * 刪除（不建議在正式環境使用；審計表應 append-only）
     * 僅供誤植資料之緊急處置
     */
    boolean deleteById(Long id);

    /** 取全部（僅限小量或工具用） */
    List<AlarmItemLog> findAll();


    // =========================
    // 常用查詢（語意化）
    // =========================

    /** 取某 global_code 最新一筆事件 */
    Optional<AlarmItemLog> findLastByGlobalCode(int globalCode);

    /** 取某 global_code 最近 N 筆事件（倒序） */
    List<AlarmItemLog> findRecentByGlobalCode(int globalCode, int limit);

    /** 取某段時間內的事件（可選 eventType 過濾；null/空集合代表不限） */
    List<AlarmItemLog> findByGlobalCodeBetween(
            int globalCode, LocalDateTime from, LocalDateTime to, Collection<String> eventTypes);

    /** 最近 N 筆 PLC 佇列事件（PLC_ON/PLC_OFF，倒序） */
    List<AlarmItemLog> findRecentPlcQueueEvents(int limit);

    /** 批次新增（append-only） */
    int saveBatch(List<AlarmItemLog> entities);
}
