package com.czkuo.rdf88701.application.dto.query;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 使用者查詢條件（對應 users 資料表）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
public class UsersQuery {

    /** 主鍵 ID */
    private Long id;

    /** 使用者名稱（模糊查詢） */
    private String username;

    /** 角色 ID */
    private Long roleId;

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

    /** 每頁筆數（預設 100，最大 500） */
    private Integer pageSize = 100;

    /**
     * 取得合法化的頁碼（最小為 1）
     */
    public int getSafePageNum() {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    /**
     * 取得合法化的每頁筆數（最大 500）
     */
    public int getSafePageSize() {
        return pageSize == null || pageSize < 1 ? 100 : Math.min(pageSize, 500);
    }
}
