package com.czkuo.rdf88701.infra.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * R029 任務主表：單一流道；同流道同時僅一筆 RUNNING
 * </p>
 *
 * @author czkuo
 * @since 2025-10-18
 */
@Getter
@Setter
@ToString
@TableName("robot_r029_task")
public class RobotR029Task {

    /**
     * PK
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 對應 mqtt_message_log.id（入站 R029）
     */
    private Long logId;

    /**
     * 對應 mqtt_inbox.id（可為 NULL）
     */
    private Long inboxId;

    /**
     * R029.TID（例：yyyyMMddHHmmssSSS）
     */
    private String tid;

    /**
     * COUNT（每顆要拆幾片）
     */
    private Integer piecePerLot;

    private String trayType;

    private String trayDesc;

    /**
     * CRANE_SPEED（STK 內枒杈速度）
     */
    private BigDecimal craneSpeed;

    /**
     * FORK_SPEED（STK 內枒杈速度）
     */
    private BigDecimal forkSpeed;

    private Integer priority;

    /**
     * Walker 決策的流道（整張單一致）
     */
    private String lane;

    /**
     * 內部簡化狀態
     */
    private String internalState;

    /**
     * 對外最後結果（四態）
     */
    private String externalLastResult;

    /**
     * 對外最後結果時間
     */
    private LocalDateTime externalLastTime;

    /**
     * NG 原因（可選）
     */
    private String failReason;

    /**
     * 整筆 R029（或彙整後）快照
     */
    private String rawMessageJson;

    private String activeLane;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
