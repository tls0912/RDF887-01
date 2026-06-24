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
 * MQTT事件可靠推送/補償事件記錄表
 * </p>
 *
 * @author czkuo
 * @since 2025-08-05
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@Setter
@ToString
@TableName("mqtt_event_log")
public class MqttEventLog {

    /** 主鍵，自動遞增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 事件類型（如 ALARM, TASK_DONE, STATUS 等） */
    private String eventType;

    /** 事件追蹤碼，每筆唯一，對應原始訊息 TID */
    private String tid;

    /** MQTT 發送 Topic 名稱 */
    private String topic;

    /** 目標系統（如 SEEC、ASE） */
    private String targetSystem;

    /** 是否需要等待ACK（1=需，0=不需） */
    private Boolean requireAck;

    /** 狀態（PENDING, SENT, TIMEOUT, RETRYING, ACKED, FAILED） */
    private String status;

    /** 事件發生時間（如：設備異常發生時間） */
    private LocalDateTime eventTime;

    /** 實際發送MQTT的時間 */
    private LocalDateTime sendTime;

    /** 收到ACK的時間（有回覆才寫） */
    private LocalDateTime ackTime;

    /** 重發次數（每補償重送一次+1） */
    private Integer retryCount;

    /** 完整MQTT事件內容（原始JSON） */
    private String payload;

    /** 補充說明（如失敗原因、ACK內容等） */
    private String resultMessage;

    /** 下一次嘗試時間（排程取用） */
    private LocalDateTime nextAttemptTime;

    /** 建立時間 */
    private LocalDateTime createdTime;

    /** 最後更新時間 */
    private LocalDateTime updatedTime;
}
