package com.czkuo.rdf88701.application.dto.query;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Crane 任務歷史查詢條件（對應 crane_task_history 資料表）
 */
@Data
public class CraneTaskHistoryQuery {

    /** 主鍵 ID（精確查詢） */
    private Long id;

    /** 主鍵清單（支援批次查詢） */
    private List<Long> idList;

    /** 對應 crane_task.id */
    private Long originId;

    /** 任務請求 ID（對應 crane_request.id） */
    private Long requestId;

    /** 起重機識別碼（如 CRANE#1） */
    private String craneId;

    /** 任務類型（INBOUND / OUTBOUND / RELOCATE） */
    private String taskType;

    /** 任務狀態（PENDING / DISPATCHED / COMPLETED / FAILED / CANCELLED / SKIPPED） */
    private String taskStatus;

    /** 優先等級（數字越小優先越高） */
    private Integer priorityLevel;

    /** 對應 container_main.id（容器主鍵） */
    private Long containerMainId;

    /** 起始儲位 ID */
    private Long sourceLocationId;

    /** 目標儲位 ID */
    private Long targetLocationId;

    /** 異動類型（INSERT / UPDATE / DELETE） */
    private String changeType;

    /** 任務建立時間起（含） */
    private LocalDateTime createdAfter;

    /** 任務建立時間迄（含） */
    private LocalDateTime createdBefore;

    /** 任務更新時間起（含） */
    private LocalDateTime updatedAfter;

    /** 任務更新時間迄（含） */
    private LocalDateTime updatedBefore;

    /** 任務派發時間起（含） */
    private LocalDateTime dispatchedAfter;

    /** 任務派發時間迄（含） */
    private LocalDateTime dispatchedBefore;

    /** 任務完成時間起（含） */
    private LocalDateTime completedAfter;

    /** 任務完成時間迄（含） */
    private LocalDateTime completedBefore;

    /** 任務取消時間起（含） */
    private LocalDateTime cancelledAfter;

    /** 任務取消時間迄（含） */
    private LocalDateTime cancelledBefore;

    /** 歷史歸檔時間起（含） */
    private LocalDateTime archivedAfter;

    /** 歷史歸檔時間迄（含） */
    private LocalDateTime archivedBefore;

    /** 第幾頁（預設為第 1 頁） */
    private Integer pageNum = 1;

    /** 每頁筆數（預設為 100 筆，最大為 500 筆） */
    private Integer pageSize = 100;

    /**
     * 取得合法的頁碼（最小為 1）
     */
    public int getSafePageNum() {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    /**
     * 取得合法的每頁筆數（最小為 1，最大為 500）
     */
    public int getSafePageSize() {
        return pageSize == null || pageSize < 1 ? 100 : Math.min(pageSize, 500);
    }
}
