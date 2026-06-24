package com.czkuo.rdf88701.application.dto.query;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Gripper 任務查詢條件（對應 gripper_task 資料表）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
public class GripperTaskQuery {

    /** 主鍵 ID */
    private Long id;

    /** 請求 ID */
    private Long requestId;

    /** 請求版本 */
    private Integer requestVersion;

    /** Gripper 裝置 ID */
    private Integer gripperId;

    /** 任務類型（如 PICK、MOVE） */
    private String taskType;

    /** 任務狀態（如 PENDING、RUNNING、COMPLETED） */
    private String taskStatus;

    /** 虛擬容器 ID */
    private Long containerMainId;

    /** 來源位置 */
    private Long sourceLocationId;

    /** 目標位置 */
    private Long targetLocationId;

    /** 目標高度（mm） */
    private Double targetHeightMm;

    /** 層數 */
    private Integer layerCount;

    /** 已派工時間起 */
    private LocalDateTime dispatchedAfter;

    /** 已派工時間迄 */
    private LocalDateTime dispatchedBefore;

    /** 完成時間起 */
    private LocalDateTime completedAfter;

    /** 完成時間迄 */
    private LocalDateTime completedBefore;

    /** 取消時間起 */
    private LocalDateTime cancelledAfter;

    /** 取消時間迄 */
    private LocalDateTime cancelledBefore;

    /** 操作人員 */
    private String operator;

    /** 關鍵字模糊查詢（針對 remark 或取消原因） */
    private String keyword;

    /** 建立時間起 */
    private LocalDateTime createdAfter;

    /** 建立時間迄 */
    private LocalDateTime createdBefore;

    /** 更新時間起 */
    private LocalDateTime updatedAfter;

    /** 更新時間迄 */
    private LocalDateTime updatedBefore;

    /** 第幾頁（預設 1） */
    private Integer pageNum = 1;

    /** 每頁幾筆（預設 100，最大 500） */
    private Integer pageSize = 100;

    /**
     * 取得合法化的分頁筆數（最大 500）
     */
    public int getSafePageSize() {
        return pageSize != null && pageSize > 0
                ? Math.min(pageSize, 500)
                : 100;
    }

    /**
     * 取得合法化的頁數（最小為 1）
     */
    public int getSafePageNum() {
        return pageNum != null && pageNum > 0 ? pageNum : 1;
    }
}
