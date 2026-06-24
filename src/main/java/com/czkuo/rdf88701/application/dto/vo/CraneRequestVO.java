package com.czkuo.rdf88701.application.dto.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * CraneRequest 對外呈現用 VO
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
public class CraneRequestVO {

    private Long id;

    /** 外部識別用唯一鍵 */
    private String requestKey;

    /** 請求版本（用於追蹤多次更新） */
    private Integer version;

    /** 請求類型（INBOUND, OUTBOUND, RELOCATE） */
    private String requestType;

    /** 請求來源（UI, ASE, SYSTEM） */
    private String requestSource;

    /** 原始來源系統的請求參考編號 */
    private String sourceRequestRef;

    /** 虛擬容器 ID（container_main） */
    private Long containerMainId;

    /** 來源儲位 ID（可為 null） */
    private Long sourceLocationId;

    /** 目標儲位 ID（可為 null） */
    private Long targetLocationId;

    /** 是否接受請求（Y/N） */
    private String accepted;

    /** 接受時間 */
    private LocalDateTime acceptTime;

    /** 拒絕原因（如未接受時） */
    private String rejectReason;

    /** 請求操作人員（或系統） */
    private String operator;

    /** 請求建立時間 */
    private LocalDateTime requestTime;

    /** 請求備註 */
    private String remark;

    /** 原始 Payload（JSON 格式） */
    private String rawPayload;

    /** 最後更新時間 */
    private LocalDateTime lastUpdatedTime;
}
