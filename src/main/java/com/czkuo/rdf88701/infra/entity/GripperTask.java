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
@TableName("gripper_task")
public class GripperTask {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 對應 gripper_request.id
     */
    private Long requestId;

    /**
     * 對應請求版本
     */
    private Integer requestVersion;

    /**
     * Gripper 裝置 ID（比照 Transfer 為 Long 型別）
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

    /**
     * 實際執行目標高度（建立時固定）
     */
    private BigDecimal targetHeightMm;

    /**
     * 夾取層數（僅 PICK 使用）
     */
    private Integer layerCount;

    /**
     * 任務優先級（越大越優先）
     */
    private Integer priorityLevel;

    /**
     * 任務派發時間
     */
    private LocalDateTime dispatchedTime;

    /**
     * 任務完成時間
     */
    private LocalDateTime completedTime;

    /**
     * 任務取消時間
     */
    private LocalDateTime cancelledTime;

    /**
     * 任務已結束（完成或取消）時間
     */
    private LocalDateTime doneTime;

    private String cancelledReason;

    private String operator;

    private String remark;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
