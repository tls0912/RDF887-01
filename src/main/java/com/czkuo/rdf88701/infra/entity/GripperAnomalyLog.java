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
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@Setter
@ToString
@TableName("gripper_anomaly_log")
public class GripperAnomalyLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 裝置代號
     */
    private String gripperId;

    /**
     * 異常類型
     */
    private String anomalyType;

    /**
     * 詳細異常說明
     */
    private String description;

    /**
     * 若異常與任務有關，記錄任務 ID
     */
    private Long relatedTaskId;

    /**
     * 異常發生時間
     */
    private LocalDateTime occurredTime;
}
