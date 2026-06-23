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
 * Infrared 任務歷史記錄
 * </p>
 *
 * @author czkuo
 * @since 2025-08-06
 */
@Getter
@Setter
@ToString
@TableName("infrared_task_history")
public class InfraredTaskHistory {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 對應 infrared_task.id
     */
    private Long originId;

    /**
     * 對應 infrared_request.id
     */
    private Long requestId;

    /**
     * 對應 infrared.id
     */
    private Long infraredId;

    /**
     * 對應的容器主檔
     */
    private Long containerMainId;

    /**
     * 任務類型，目前僅 MEASURE
     */
    private String taskType;

    /**
     * 任務狀態
     */
    private String taskStatus;

    /**
     * 任務優先級
     */
    private Integer priorityLevel;

    /**
     * 下派時間
     */
    private LocalDateTime dispatchedTime;

    /**
     * 完成時間
     */
    private LocalDateTime completedTime;

    /**
     * 取消時間
     */
    private LocalDateTime cancelledTime;

    /**
     * 實際結束時間（完成、取消或失敗）
     */
    private LocalDateTime doneTime;

    /**
     * 取消原因
     */
    private String cancelledReason;

    /**
     * 備註
     */
    private String remark;

    /**
     * 任務建立時間
     */
    private LocalDateTime createdTime;

    /**
     * 對應主表 updated_time（最後一次變更時間）
     */
    private LocalDateTime updatedTime;

    /**
     * 異動類型
     */
    private String changeType;

    /**
     * 歸檔時間
     */
    private LocalDateTime archivedTime;
}
