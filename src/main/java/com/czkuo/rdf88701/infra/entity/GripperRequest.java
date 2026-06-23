package com.czkuo.rdf88701.infra.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
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
@TableName("gripper_request")
public class GripperRequest {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long gripperId;

    /**
     * 外部請求識別碼
     */
    private String requestKey;

    /**
     * 請求版本控制
     */
    private Integer version;

    /**
     * 請求動作類型（MOVE / PICK / DROP）
     */
    private String taskType;

    /**
     * 來源系統（UI / SYSTEM）
     */
    private String requestSource;

    private Long containerMainId;

    /**
     * 來源位置（如 PICK、MOVE）
     */
    private Long sourceLocationId;

    private String sourceLocationName;

    /**
     * 目標位置（如 PLACE、MOVE）
     */
    private Long targetLocationId;

    private String targetLocationName;

    /**
     * 希望執行的目標高度（參考用）
     */
    private BigDecimal targetHeightMm;

    /**
     * 夾取層數（僅 PICK 使用）
     */
    private Integer layerCount;

    /**
     * 是否接受請求（Y/N）
     */
    private String accepted;

    private LocalDateTime acceptTime;

    private String rejectReason;

    private String operator;

    private LocalDateTime requestTime;

    private String remark;

    /**
     * 原始請求內容 JSON（保留擴充用）
     */
    private String rawPayload;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;

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
