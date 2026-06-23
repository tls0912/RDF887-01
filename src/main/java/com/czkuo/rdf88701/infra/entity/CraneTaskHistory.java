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
@TableName("crane_task_history")
public class CraneTaskHistory {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 對應主表 crane_task.id
     */
    private Long originId;

    /**
     * 對應 crane_request.id（若有）
     */
    private Long requestId;

    /**
     * 執行任務的天車代碼
     */
    private String craneId;

    /**
     * 任務類型（INBOUND / OUTBOUND / RELOCATE）
     */
    private String taskType;

    /**
     * 任務狀態（PENDING / DISPATCHED / COMPLETED / ...）
     */
    private String taskStatus;

    /**
     * 任務優先順序（數字越小優先級越高）
     */
    private Integer priorityLevel;

    /**
     * 操作的容器主體 ID
     */
    private Long containerMainId;

    /**
     * 搬運起始位置 ID
     */
    private Long fromLocationId;

    /**
     * 搬運目標位置 ID
     */
    private Long toLocationId;

    /**
     * 任務被派工的時間
     */
    private LocalDateTime dispatchedTime;

    /**
     * 任務完成的時間（實際完成）
     */
    private LocalDateTime completedTime;

    /**
     * 任務取消時間
     */
    private LocalDateTime cancelledTime;

    /**
     * 任務 Done 時間（可為 COMPLETED、CANCELLED、FAILED 等任務最終狀態時間）
     */
    private LocalDateTime doneTime;

    /**
     * 任務取消原因（如有）
     */
    private String cancelledReason;

    /**
     * 備註欄
     */
    private String remark;

    /**
     * 任務建立時間（對應原始任務建立時刻）
     */
    private LocalDateTime createdTime;

    /**
     * 任務最後變更時間（對應主表 updated_time）
     */
    private LocalDateTime updatedTime;

    /**
     * 歷史紀錄類型（INSERT / UPDATE / DELETE）
     */
    private String changeType;

    /**
     * 歷史備份時間（本筆歷史資料寫入時間）
     */
    private LocalDateTime archivedTime;
}
