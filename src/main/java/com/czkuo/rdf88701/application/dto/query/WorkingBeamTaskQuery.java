package com.czkuo.rdf88701.application.dto.query;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * WorkingBeamTask 查詢條件（對應 working_beam_task 資料表）
 */
@Data
public class WorkingBeamTaskQuery {

    /** 主鍵 ID（精確查詢） */
    private Long id;

    /** 主鍵清單（批次查詢） */
    private List<Long> idList;

    /** 對應 working_beam_request.id */
    private Long requestId;

    /** 對應 working_beam.id */
    private Long workingBeamId;

    /** 移動方向（IN / OUT） */
    private String direction;

    /** 任務狀態（如：PENDING, IN_PROGRESS, COMPLETED, CANCELLED） */
    private String taskStatus;

    /** 任務優先等級 */
    private Integer priorityLevel;

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
