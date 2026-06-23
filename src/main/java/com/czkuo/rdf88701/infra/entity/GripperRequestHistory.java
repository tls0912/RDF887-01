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
@TableName("gripper_request_history")
public class GripperRequestHistory {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 對應主表 gripper_request.id
     */
    private Long originId;

    /**
     * Gripper 裝置代號
     */
    private String gripperId;

    private String requestKey;

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

    private Long sourceLocationId;

    private String sourceLocationName;

    private Long targetLocationId;

    private String targetLocationName;

    private BigDecimal targetHeightMm;

    private Integer layerCount;

    /**
     * 是否接受請求（Y/N）或 Boolean
     */
    private Boolean accepted;

    private LocalDateTime acceptTime;

    private String rejectReason;

    private String operator;

    private LocalDateTime requestTime;

    private String remark;

    private String rawPayload;

    /**
     * 異動類型（INSERT / UPDATE / DELETE）
     */
    private String changeType;

    private LocalDateTime archivedTime;

    /**
     * 紀錄來源（系統或操作人員）
     */
    private String archivedBy;
}
