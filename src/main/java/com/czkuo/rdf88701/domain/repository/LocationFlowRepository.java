package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.application.dto.query.LocationFlowQuery;
import com.czkuo.rdf88701.common.dto.PageResult;
import com.czkuo.rdf88701.common.enums.ExitType;
import com.czkuo.rdf88701.infra.entity.LocationFlow;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * LocationFlow 資料存取介面
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public interface LocationFlowRepository {

    /**
     * 根據主鍵 ID 查詢單筆資料
     */
    Optional<LocationFlow> findById(Long id);

    /**
     * 根據查詢條件查詢清單（不使用分頁）
     */
    List<LocationFlow> findByCondition(LocationFlowQuery query);

    /**
     * 根據查詢條件查詢分頁資料
     */
    PageResult<LocationFlow> findPageByCondition(LocationFlowQuery query);

    /**
     * 儲存新資料
     */
    boolean save(LocationFlow entity);

    /**
     * 根據 ID 更新資料
     */
    boolean update(LocationFlow entity);

    /**
     * 根據 ID 刪除資料
     */
    boolean deleteById(Long id);

    /**
     * 查詢所有資料（不含條件）
     * 請小心使用，大量資料建議改用分頁
     */
    List<LocationFlow> findAll();

    /**
     * 關閉指定 container 尚未離開的 location_flow 記錄
     * 將 left_time、exit_type、exit_operator 等補上
     */
    boolean closeActiveFlow(Long containerMainId, Long sourceTaskId, String exitOperator);

    /**
     * 插入一筆新的帳籍紀錄（進帳）
     */
    boolean insert(LocationFlow entity);

    /**
     * 將 container 尚未離開的 location_flow 記錄補上 left_time
     * 專用於 Crane 自動轉帳用途，不設定 exit_operator 或 exit_type
     */
    int  markPreviousAsLeft(Long containerMainId, LocalDateTime leftTime);

    /**
     * 補上 container 在指定儲位的離開時間與相關資訊
     * - 專用於補帳用途，會更新 left_time、exit_type、exit_operator
     *
     * @param containerMainId 容器 ID
     * @param locationPointId 離開位置 ID（可用於額外條件強化）
     * @param leftTime 離開時間
     * @param exitType 離開類型（人工、自動、異常等）
     * @param exitOperator 操作者帳號或系統
     * @return 是否成功更新紀錄
     */
    boolean markExit(Long containerMainId, Long locationPointId, LocalDateTime leftTime, ExitType exitType, String exitOperator);

}

