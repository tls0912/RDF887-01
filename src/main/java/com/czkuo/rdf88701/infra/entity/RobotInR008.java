package com.czkuo.rdf88701.infra.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * <p>
 * 入站 R008 MESSAGE 明細（EQP→WIP；一筆對應一個 mqtt_message_log.id）
 * </p>
 *
 * @author czkuo
 * @since 2025-10-18
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@Setter
@ToString
@TableName("robot_in_r008")
public class RobotInR008 {

    /**
     * 對應 mqtt_message_log.id（入站 COMMAND）
     */
    @TableId("log_id")
    private Long logId;

    private String lotId;

    /**
     * CARRIERID
     */
    private String carrierId;

    /**
     * 目標 WIP/STK（R008 為目的地儲位，可為 NULL）
     */
    private String wipName;

    /**
     * 來源機台名稱（EQP → WIP 的 EQP）
     */
    private String destLoc;

    /**
     * 來源機台 Port
     */
    private String eqpPort;

    /**
     * AMR 名稱（允許空）
     */
    private String deviceName;

    /**
     * SAA→SEEC 才會有；ASE→廠商禁止
     */
    private String stkPort;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
