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
 * Transfer 任務執行歷史紀錄表
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
@TableName("transfer_task_history")
public class TransferTaskHistory {

    /**
     * 歷史主鍵
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 對應原始 transfer_task.id
     */
    private Long originId;

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

    private LocalDateTime dispatchedTime;

    private LocalDateTime completedTime;

    private LocalDateTime cancelledTime;

    private LocalDateTime doneTime;

    private String cancelledReason;

    private String remark;

    /**
     * 異動類型
     */
    private String changeType;

    /**
     * 歸檔時間
     */
    private LocalDateTime archivedTime;

    /**
     * 操作者（系統或人員帳號）
     */
    private String archivedBy;

    /**
     * 歸檔備註
     */
    private String archivedRemark;
}
