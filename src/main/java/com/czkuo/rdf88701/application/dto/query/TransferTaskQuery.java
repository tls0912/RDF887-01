package com.czkuo.rdf88701.application.dto.query;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * TransferTask 查詢條件（對應 transfer_task 資料表）
 */
@Data
public class TransferTaskQuery {

    /** 主鍵 ID（精確查詢） */
    private Long id;

    /** 主鍵清單（批次查詢） */
    private List<Long> idList;

    /** Transfer 請求 ID（對應 transfer_request.id） */
    private Long requestId;

    /** Transfer 裝置 ID（對應 transfer.id） */
    private Long transferId;

    /** 指令類型（MOVE / PICK / DROP） */
    private String taskType;

    /** 任務狀態（如：PENDING, IN_PROGRESS, COMPLETED, CANCELLED, FAILED, etc.） */
    private String taskStatus;

    /** 優先等級 */
    private Integer priorityLevel;

    /** 關聯容器 */
    private Long containerMainId;

    /** 目標位置 ID（對應 location_id） */
    private Long locationId;

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
