package com.czkuo.rdf88701.common.dto;

import lombok.Data;

import java.util.List;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Data
public class PageResult<T> {

    /** 當前頁數（從 1 開始） */
    private Integer pageNum;

    /** 每頁筆數 */
    private Integer pageSize;

    /** 總筆數 */
    private Long total;

    /** 總頁數 */
    private Long pages;

    /** 資料內容 */
    private List<T> data;

    public PageResult(Integer pageNum, Integer pageSize, Long total, List<T> data) {
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.total = total;
        this.data = data;
        this.pages = (long) Math.ceil((double) total / pageSize);
    }
}
