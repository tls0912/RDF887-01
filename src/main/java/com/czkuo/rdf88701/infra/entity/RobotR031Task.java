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
 * R031 任務單（WIP/STK → Manual Port 任務追蹤）
 * </p>
 *
 * @author czkuo
 * @since 2025-10-18
 */
@Getter
@Setter
@ToString
@TableName("robot_r031_task")
public class RobotR031Task {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 對應 mqtt_message_log.id（入站 COMMAND）
     */
    private Long logId;

    /**
     * 對應 mqtt_inbox.id（入佇列後回填）
     */
    private Long inboxId;

    /**
     * 任務識別碼（yyyyMMddHHmmssfff）
     */
    private String tid;

    /**
     * 批號
     */
    private String lotId;

    /**
     * 載具/容器編號
     */
    private String carrierId;

    /**
     * 來源儲格（WIP/STK slot）
     */
    private String wipName;

    /**
     * 來源區（ZIP/WIP）
     */
    private String sourceZone;

    /**
     * 實際放置 Manual Port 名稱（END 時回報）
     */
    private String manualPort;

    /**
     * 原始 MESSAGE JSON（完整保存入站內容）
     */
    private String rawMessageJson;

    /**
     * 內部流程狀態
     */
    private String internalState;

    /**
     * 對外回覆狀態（MQTT RESULT 值）
     */
    private String externalLastResult;

    /**
     * 最後一次回覆時間
     */
    private LocalDateTime externalLastTime;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
