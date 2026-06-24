package com.czkuo.rdf88701.application.dto.query;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * LocationFlow 查詢條件（對應 location_flow 資料表）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
public class LocationFlowQuery {

    /** 主鍵 ID（精確查詢） */
    private Long id;

    /** 主鍵清單（支援批次查詢） */
    private List<Long> idList;

    /** 容器 ID（對應 container_main.id） */
    private Long containerMainId;

    /** 位置 ID（對應 location_point.id） */
    private Long locationPointId;

    /** 進帳方式（PLC、MANUAL、EXTERNAL、SYSTEM_REBUILD） */
    private String entryType;

    /** 出帳方式（NORMAL、MANUAL、FORCE_REMOVED、TIMEOUT、PLC_LOST） */
    private String exitType;

    /** 抵達時間起（含） */
    private LocalDateTime arrivedAfter;

    /** 抵達時間迄（含） */
    private LocalDateTime arrivedBefore;

    /** 離開時間起（含） */
    private LocalDateTime leftAfter;

    /** 離開時間迄（含） */
    private LocalDateTime leftBefore;

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
