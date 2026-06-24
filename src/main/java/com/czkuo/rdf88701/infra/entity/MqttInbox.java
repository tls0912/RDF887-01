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
 * 入站 COMMAND 處理佇列表（獨立於 mqtt_message_log）
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
@TableName("mqtt_inbox")
public class MqttInbox {

    /**
     * PK
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 對應 mqtt_message_log.id（入站 COMMAND）
     */
    private Long logId;

    /**
     * 沿用來源 TID
     */
    private String tid;

    /**
     * R007/R008/R029/R031
     */
    private String cmdId;

    /**
     * 來源（例 ase）
     */
    private String sender;

    /**
     * 接收方（例 saa）
     */
    private String receiver;

    /**
     * 接收的 topic
     */
    private String topic;

    /**
     * 收到時間（來自 mqtt_message_log.timestamp）
     */
    private LocalDateTime recvTime;

    /**
     * 內部處理狀態
     */
    private String processState;

    /**
     * 錯誤訊息（驗證/解析/業務拒收）
     */
    private String processErrors;

    /**
     * 結案時間（DONE/REJECTED/CANCELLED）
     */
    private LocalDateTime processedTime;

    /**
     * 鎖持有者（節點/執行緒）
     */
    private String lockOwner;

    /**
     * 鎖到期（避免卡死）
     */
    private LocalDateTime lockUntil;

    /**
     * 優先權（1高→9低）
     */
    private Byte priority;

    /**
     * 處理嘗試次數
     */
    private Integer attempts;

    /**
     * 下次嘗試時間（退避）
     */
    private LocalDateTime nextAttemptTime;

    /**
     * 對應內部任務類型（如 TRANSFER/DISMANTLE）
     */
    private String mappedTaskType;

    /**
     * 對應內部任務 id
     */
    private Long mappedTaskId;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
