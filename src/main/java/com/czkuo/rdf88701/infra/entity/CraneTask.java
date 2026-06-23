package com.czkuo.rdf88701.infra.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
@TableName("crane_task")
public class CraneTask {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 對應的 crane_request.id */
    private Long requestId;

    /** 執行任務的天車代號（如 CRANE#1 = 1） */
    private String craneId;

    /** 任務類型：INBOUND / OUTBOUND / RELOCATE */
    private String taskType;

    /**
     * 任務狀態：
     * - PENDING：待處理
     * - DISPATCHED：已派發
     * - IN_PROGRESS：執行中
     * - COMPLETED：完成
     * - FAILED：失敗
     * - CANCELLED：已取消
     * - SKIPPED：被略過
     * - RETRY：補償任務
     */
    private String taskStatus;

    /** 任務優先等級，數字越大越高（預設為 0） */
    private Integer priorityLevel;

    /** 對應容器 container_main.id */
    private Long containerMainId;

    /** 任務來源位置（對應 location_point.id） */
    private Long sourceLocationId;

    /** 任務目標位置（對應 location_point.id） */
    private Long targetLocationId;

    /** 任務被系統派發的時間 */
    private LocalDateTime dispatchedTime;

    /** 任務實際完成時間（成功才會填） */
    private LocalDateTime completedTime;

    /** 任務被取消的時間 */
    private LocalDateTime cancelledTime;

    /** 任務已被視為結束（無論成功與否）之時間 */
    private LocalDateTime doneTime;

    /** 若任務被取消，紀錄取消原因 */
    private String cancelledReason;

    /** 任務備註 */
    private String remark;

    /** 任務建立時間（系統建立時自動填入） */
    private LocalDateTime createdTime;

    /** 任務最後更新時間（系統更新時自動維護） */
    private LocalDateTime updatedTime;

    // === 額外欄位（非資料表） ===

    /** 虛擬容器對外識別碼（如 CU24061200001） */
    @TableField(exist = false)
    private String containerAliasCode;

    /** 代表性產品條碼（對應 product_main.product_code） */
    @TableField(exist = false)
    private String representativeProductCode;

    /** 任務來源位置名稱（對應 location_point.name） */
    @TableField(exist = false)
    private String sourceLocationName;

    /** 任務目標位置名稱（對應 location_point.name） */
    @TableField(exist = false)
    private String targetLocationName;
}
