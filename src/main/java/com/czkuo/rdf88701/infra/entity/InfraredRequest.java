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
 * Infrared 任務請求
 * </p>
 *
 * @author czkuo
 * @since 2025-08-06
 */
@Getter
@Setter
@ToString
@TableName("infrared_request")
public class InfraredRequest {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 外部識別用唯一鍵 */
    private String requestKey;

    /** 版本控制（遞增） */
    private Integer version;

    /** 請求來源 */
    private String requestSource;

    /** 指定 Infrared 裝置 */
    private Long infraredId;

    /** 對應的容器主檔 */
    private Long containerMainId;

    /** 任務類型，目前僅 MEASURE */
    private String taskType;

    /** 是否接受請求（Y/N） */
    private String accepted;

    private LocalDateTime acceptTime;

    private String rejectReason;

    private String operator;

    /** 請求時間 */
    private LocalDateTime requestTime;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;

    private String remark;

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
