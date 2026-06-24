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
 * @since 2025-07-25
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@Setter
@ToString
@TableName("mqtt_message_log")
public class MqttMessageLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String tid;

    private String cmdId;

    private String messageType;

    private String idDesc;

    private String topic;

    private String sender;

    private String receiver;

    private LocalDateTime timestamp;

    private String result;

    private String resultMessage;

    private String payload;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
