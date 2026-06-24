package com.czkuo.rdf88701.infra.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * <p>
 * 入站 R007 MESSAGE 明細（WIP→EQP；一筆對應一個 mqtt_message_log.id）
 * </p>
 *
 * @author czkuo
 * @since 2025-10-15
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@Setter
@ToString
@TableName("robot_in_r007")
public class RobotInR007 {

    /**
     * 對應 mqtt_message_log.id（入站 COMMAND）
     */
    @TableId("log_id")
    private Long logId;

    private String lotId;

    /**
     * CARRIERID（修正命名）
     */
    private String carrierId;

    /**
     * 來源 WIP/STK
     */
    private String wipName;

    /**
     * 目的設備
     */
    private String destLoc;

    private String eqpPort;

    /**
     * AGV/AMR 名稱（允許空）
     */
    private String deviceName;

    /**
     * ASE→廠商禁止；SAA→SEEC 才會有
     */
    private String stkPort;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
