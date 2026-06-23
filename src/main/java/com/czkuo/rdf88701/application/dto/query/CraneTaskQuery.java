package com.czkuo.rdf88701.application.dto.query;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * CraneTask 查詢條件（對應 crane_task 資料表）
 */
@Data
public class CraneTaskQuery {

    /** 主鍵 ID（精確查詢） */
    private Long id;

    /** 主鍵清單（批次查詢） */
    private List<Long> idList;

    /** 來源請求 ID（對應 crane_request.id） */
    private Long requestId;

    /** Crane ID（對應 crane 設備） */
    private Integer craneId;

    /** 任務類型（如：MOVE, RELOCATE） */
    private String taskType;

    /** 任務狀態（如：PENDING, IN_PROGRESS, COMPLETED, CANCELLED） */
    private String taskStatus;

    /** 優先等級 */
    private Integer priorityLevel;

    /** 虛擬容器 ID（container_main_id） */
    private Long containerMainId;

    /** 來源位置 ID（source_location_id） */
    private Long sourceLocationId;

    /** 目標位置 ID（target_location_id） */
    private Long targetLocationId;

    /** 派送時間起 */
    private LocalDateTime dispatchedAfter;

    /** 派送時間迄 */
    private LocalDateTime dispatchedBefore;

    /** 完成時間起 */
    private LocalDateTime completedAfter;

    /** 完成時間迄 */
    private LocalDateTime completedBefore;

    /** 取消時間起 */
    private LocalDateTime cancelledAfter;

    /** 取消時間迄 */
    private LocalDateTime cancelledBefore;

    /** 關鍵字模糊查詢（remark, cancelled_reason） */
    private String keyword;

    /** 第幾頁（預設為第 1 頁） */
    private Integer pageNum = 1;

    /** 每頁筆數（預設 100 筆，最大 500 筆） */
    private Integer pageSize = 100;

    /**
     * 取得合法化的每頁筆數（最大 500）
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
