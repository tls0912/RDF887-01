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
@TableName("gripper_task_history")
public class GripperTaskHistory {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 對應主表 gripper_task.id
     */
    private Long originId;

    /**
     * Gripper 裝置 ID
     */
    private Long gripperId;

    /**
     * 任務動作類型（MOVE / PICK / DROP）
     */
    private String taskType;

    /**
     * 任務狀態（PENDING / DISPATCHED / IN_PROGRESS / COMPLETED / FAILED / CANCELLED / SKIPPED / RETRY）
     */
    private String taskStatus;

    private Long containerMainId;

    /**
     * 來源位置（僅 PICK、MOVE 使用）
     */
    private Long fromLocationId;

    /**
     * 目標位置（僅 PLACE、MOVE 使用）
     */
    private Long toLocationId;

    private BigDecimal targetHeightMm;

    private Integer layerCount;

    /**
     * 任務優先級（越大越優先）
     */
    private Integer priorityLevel;

    private LocalDateTime dispatchedTime;

    private LocalDateTime completedTime;

    private LocalDateTime cancelledTime;

    /**
     * 任務已結束（完成或取消）時間
     */
    private LocalDateTime doneTime;

    private String cancelledReason;

    private String operator;

    private String remark;

    private String changeType;

    private LocalDateTime archivedTime;

    /**
     * 紀錄來源（系統或操作人員）
     */
    private String archivedBy;
}
