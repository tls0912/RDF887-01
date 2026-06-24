package com.czkuo.rdf88701.application.dto.query;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * LocationPoint 主表查詢條件（對應 location_point 資料表）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
public class LocationPointQuery {

    /** 主鍵 ID（精確查詢） */
    private Long id;

    /** 主鍵清單（支援批次查詢） */
    private List<Long> idList;

    /** 區域代碼（如 A 倉、B 倉） */
    private String zoneCode;

    /** 儲位代碼（模糊查詢） */
    private String code;

    /** 名稱（模糊查詢） */
    private String name;

    /** 儲位類型（如 STORAGE、SITE） */
    private String locationType;

    /** 是否啟用 */
    private String enabled;

    /** 是否佔用 */
    private String isOccupied;

    /** 是否鎖定 */
    private String isLocked;

    /** 是否預約 */
    private String isReserved;

    /** 優先狀態 */
    private String preferredStatus;

    /** 建立時間起（含） */
    private LocalDateTime createdAfter;

    /** 建立時間迄（含） */
    private LocalDateTime createdBefore;

    /** 第幾頁（預設為第 1 頁） */
    private Integer pageNum = 1;

    /** 每頁筆數（預設為 100 筆，最大為 500 筆） */
    private Integer pageSize = 100;

    public int getSafePageNum() {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    public int getSafePageSize() {
        return pageSize == null || pageSize < 1 ? 100 : Math.min(pageSize, 500);
    }
}