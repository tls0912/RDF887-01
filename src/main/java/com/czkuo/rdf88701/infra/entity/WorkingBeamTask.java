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
 * WorkingBeam 任務執行
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
@TableName("working_beam_task")
public class WorkingBeamTask {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 對應的請求
     */
    private Long requestId;

    /**
     * WorkingBeam 裝置 ID
     */
    private String workingBeamId;

    /**
     * 移動方向（IN=向內，OUT=向外）
     */
    private String direction;

    private String taskStatus;

    /**
     * 任務優先級，數值越高優先權越高
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
     * 最後更新時間
     */
    private LocalDateTime updatedTime;
}
