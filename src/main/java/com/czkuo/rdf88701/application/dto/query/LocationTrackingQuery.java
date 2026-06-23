package com.czkuo.rdf88701.application.dto.query;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * LocationTracking 查詢條件（對應 location_tracking 資料表）
 */
@Data
public class LocationTrackingQuery {

    /** 主鍵 ID（精確查詢） */
    private Long id;

    /** 主鍵清單（支援批次查詢） */
    private List<Long> idList;

    /** 容器 ID（對應 container_main.id） */
    private Long containerMainId;

    /** 位置 ID（對應 location_point.id） */
    private Long locationPointId;

    /** 抵達時間起（含） */
    private LocalDateTime arrivedAfter;

    /** 抵達時間迄（含） */
    private LocalDateTime arrivedBefore;

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
     * 取得合法的每頁筆數（最大 500）
     */
    public int getSafePageSize() {
        return pageSize == null || pageSize < 1 ? 100 : Math.min(pageSize, 500);
    }
}
