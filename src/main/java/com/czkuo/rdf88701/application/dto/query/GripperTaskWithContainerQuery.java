package com.czkuo.rdf88701.application.dto.query;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 查詢條件：包含容器資訊的 Gripper 任務查詢
 */
@Data
public class GripperTaskWithContainerQuery {

    /** Gripper 裝置 ID（選填） */
    private Integer gripperId;

    /** 任務狀態（選填） */
    private String taskStatus;

    /** 建立時間起（選填） */
    private LocalDateTime createdAfter;

    /** 建立時間迄（選填） */
    private LocalDateTime createdBefore;

    /** 第幾頁（預設為第 1 頁） */
    private Integer pageNum = 1;

    /** 每頁筆數（預設為 100 筆，最大為 500 筆） */
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
