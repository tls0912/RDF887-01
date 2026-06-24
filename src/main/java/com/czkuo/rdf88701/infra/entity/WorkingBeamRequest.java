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
 * WorkingBeam 任務請求
 * </p>
 *
 * @author czkuo
 * @since 2025-06-21
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@Setter
@ToString
@TableName("working_beam_request")
public class WorkingBeamRequest {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 外部識別用唯一鍵
     */
    private String requestKey;

    /**
     * 版本控制（遞增）
     */
    private Integer version;

    /**
     * 請求來源
     */
    private String requestSource;

    /**
     * 指定 WorkingBeam 裝置
     */
    private Long workingBeamId;

    /**
     * 移動方向（IN=向內，OUT=向外）
     */
    private String direction;

    /**
     * 是否接受請求（Y/N）
     */
    private String accepted;

    private LocalDateTime acceptTime;

    private String rejectReason;

    private String operator;

    /**
     * 請求時間
     */
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
