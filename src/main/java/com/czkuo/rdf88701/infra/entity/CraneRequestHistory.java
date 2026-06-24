package com.czkuo.rdf88701.infra.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author czkuo
 * @since 2025-05-06
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@Setter
@ToString
@TableName("crane_request_history")
public class CraneRequestHistory {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 對應 crane_request.id */
    private Long originId;

    /** 外部識別用唯一鍵 */
    private String requestKey;

    /** 版本控制（遞增） */
    private Integer version;

    /** 請求類型（INBOUND / OUTBOUND / RELOCATE） */
    private String requestType;

    /** 請求來源（UI / ASE / SYSTEM） */
    private String requestSource;

    /** 來源系統傳入之請求參考編號 */
    private String sourceRequestRef;

    /** 對應容器 ID */
    private Long containerMainId;

    /** 來源位置 ID */
    private Long sourceLocationId;

    /** 目標位置 ID */
    private Long targetLocationId;

    /** 外部傳入的 Source Location Name */
    private String sourceLocationName;

    /** 外部傳入的 Target Location Name */
    private String targetLocationName;

    /** 是否接受請求（Y/N） */
    private String accepted;

    /** 接受時間 */
    private LocalDateTime acceptTime;

    /** 拒絕原因 */
    private String rejectReason;

    /** 操作者帳號 */
    private String operator;

    /** 外部傳入的請求時間（業務時間） */
    private LocalDateTime requestTime;

    /** 主表建立時間（快照備份） */
    private LocalDateTime createdTime;

    /** 主表最後更新時間（快照備份） */
    private LocalDateTime updatedTime;

    /** 備註 */
    private String remark;

    /** 原始請求內容（JSON 格式） */
    private String rawPayload;

    /** 異動類型（INSERT / UPDATE / DELETE） */
    private String changeType;

    /** 快照實際寫入歷史表的時間 */
    private LocalDateTime archivedTime;
}
