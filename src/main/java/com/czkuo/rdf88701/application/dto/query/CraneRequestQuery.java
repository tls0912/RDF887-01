package com.czkuo.rdf88701.application.dto.query;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * CraneRequest 查詢條件（對應 crane_request 資料表）
 */
@Data
public class CraneRequestQuery {

    /** 主鍵 ID（精確查詢） */
    private Long id;

    /** 主鍵清單（批次查詢） */
    private List<Long> idList;

    /** 外部識別用唯一鍵（request_key） */
    private String requestKey;

    /** 請求類型（INBOUND, OUTBOUND, RELOCATE） */
    private String requestType;

    /** 請求來源（UI, ASE, SYSTEM） */
    private String requestSource;

    /** 原始請求參考編號（供對應來源系統追蹤） */
    private String sourceRequestRef;

    /** 虛擬容器 ID（container_main_id） */
    private Long containerMainId;

    /** 來源位置 ID（source_location_id） */
    private Long sourceLocationId;

    /** 目標位置 ID（target_location_id） */
    private Long targetLocationId;

    /** 是否已接受（Y/N） */
    private String accepted;

    /** 接受時間起 */
    private LocalDateTime acceptAfter;

    /** 接受時間迄 */
    private LocalDateTime acceptBefore;

    /** 請求時間起 */
    private LocalDateTime requestAfter;

    /** 請求時間迄 */
    private LocalDateTime requestBefore;

    /** 操作人員 */
    private String operator;

    /** 關鍵字模糊查詢（針對 remark 或 reject_reason） */
    private String keyword;

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
