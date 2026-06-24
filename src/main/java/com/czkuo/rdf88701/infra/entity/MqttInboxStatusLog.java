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
 * 入站 COMMAND 處理狀態歷程
 * </p>
 *
 * @author czkuo
 * @since 2025-08-27
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@Setter
@ToString
@TableName("mqtt_inbox_status_log")
public class MqttInboxStatusLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 對應 mqtt_inbox.id
     */
    private Long inboxId;

    private String fromState;

    private String toState;

    private String changedBy;

    private String changeReason;

    private LocalDateTime changeTime;
}
