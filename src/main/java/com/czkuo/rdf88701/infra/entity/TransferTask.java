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
 * Transfer 任務執行表
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
@TableName("transfer_task")
public class TransferTask {

    /**
     * 主鍵 ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 對應的請求 ID
     */
    private Long requestId;

    /**
     * Transfer 裝置 ID
     */
    private Long transferId;

    /**
     * 任務類型
     */
    private String taskType;

    /**
     * 關聯容器（可選）
     */
    private Long containerMainId;

    /**
     * 來源位置 ID
     */
    private Long fromLocationId;

    /**
     * 目標位置 ID
     */
    private Long toLocationId;

    /**
     * 任務狀態
     */
    private String taskStatus;

    /**
     * 任務優先級
     */
    private Integer priorityLevel;

    /**
     * 下發時間
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
     * 任務實際結束時間（完成/取消皆可能）
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
     * 建立時間
     */
    private LocalDateTime createdTime;

    /**
     * 更新時間
     */
    private LocalDateTime updatedTime;
}
