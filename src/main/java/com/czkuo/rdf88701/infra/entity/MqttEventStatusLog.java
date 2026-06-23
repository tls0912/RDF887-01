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
 * MQTT事件狀態變更歷程記錄表
 * </p>
 *
 * @author czkuo
 * @since 2025-08-05
 */
@Getter
@Setter
@ToString
@TableName("mqtt_event_status_log")
public class MqttEventStatusLog {

    /**
     * 主鍵，自動遞增
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 對應 mqtt_event_log.id（或TID）
     */
    private Long eventId;

    /**
     * 變更前狀態（第一次建立時可為NULL）
     */
    private String fromStatus;

    /**
     * 變更後狀態
     */
    private String toStatus;

    /**
     * 異動者（如 system, user_admin, schedule 等）
     */
    private String changedBy;

    /**
     * 異動原因或備註（如逾時自動補償、人工處理等）
     */
    private String changeReason;

    /**
     * 異動時間
     */
    private LocalDateTime changeTime;
}
