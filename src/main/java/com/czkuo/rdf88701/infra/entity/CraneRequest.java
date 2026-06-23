package com.czkuo.rdf88701.infra.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author czkuo
 * @since 2025-05-06
 */
@Getter
@Setter
@ToString
@TableName("crane_request")
public class CraneRequest {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 外部識別用唯一鍵 */
    private String requestKey;

    /** 版本控制（遞增） */
    private Integer version;

    /** 請求類型（INBOUND/OUTBOUND/RELOCATE） */
    private String requestType;

    /** 請求來源（UI/ASE/SYSTEM） */
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

    /** 操作者 */
    private String operator;

    /** 請求時間（業務傳入或系統建立） */
    private LocalDateTime requestTime;

    /** 建立時間 */
    private LocalDateTime createdTime;

    /** 最後更新時間 */
    private LocalDateTime updatedTime;

    /** 備註 */
    private String remark;

    /** 原始請求內容（JSON 格式） */
    private String rawPayload;

    // === Domain Methods ===

    /** 標記為已接受 */
    public void markAsAccepted() {
        this.accepted = "Y";
        this.acceptTime = LocalDateTime.now();
        this.updatedTime = LocalDateTime.now();
    }

    /** 檢查是否已被接受 */
    public boolean isRequestAccepted() {
        return "Y".equalsIgnoreCase(this.accepted);
    }

    /** 檢查是否被拒絕 */
    public boolean isRejected() {
        return this.rejectReason != null && !this.rejectReason.isBlank();
    }

    /** 判斷是否已被鎖定 */
    public boolean isLocked() {
        return isRequestAccepted();
    }
}
