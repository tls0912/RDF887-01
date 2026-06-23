package com.czkuo.rdf88701.application.dto.query;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Container 主表查詢條件（對應 container_main 資料表）
 */
@Data
public class ContainerMainQuery {

    /** 主鍵 ID（精確查詢） */
    private Long id;

    /** 主鍵清單（支援批次查詢） */
    private List<Long> idList;

    /** 虛擬容器代碼（模糊查詢） */
    private String aliasCode;

    /** 容器類型（TRAY、CASSETTE、FOUP 等） */
    private String containerType;

    /** 容器條碼（模糊查詢） */
    private String containerCode;

    /** 批號（模糊查詢） */
    private String lotNo;

    /** 料號（模糊查詢） */
    private String partNo;

    /** 建立時間起（含） */
    private LocalDateTime createdAfter;

    /** 建立時間迄（含） */
    private LocalDateTime createdBefore;

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
