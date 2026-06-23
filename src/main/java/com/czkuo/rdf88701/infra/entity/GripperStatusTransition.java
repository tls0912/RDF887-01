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
 */
@Getter
@Setter
@ToString
@TableName("gripper_status_transition")
public class GripperStatusTransition {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 裝置代號
     */
    private String gripperId;

    /**
     * 來源狀態（如 IDLE、RUNNING）
     */
    private String fromStatus;

    /**
     * 目標狀態（如 RUNNING、DONE）
     */
    private String toStatus;

    /**
     * RUNNING 狀態細分類（子行為）
     */
    private String subStatus;

    /**
     * 若為任務觸發，紀錄來源任務 ID
     */
    private Long triggeredByTaskId;

    /**
     * 對應 PLC snapshot 時間
     */
    private LocalDateTime snapshotTime;

    /**
     * 來源狀態持續時間（毫秒）
     */
    private Long durationMs;

    /**
     * 狀態變更時間
     */
    private LocalDateTime changedTime;
}
