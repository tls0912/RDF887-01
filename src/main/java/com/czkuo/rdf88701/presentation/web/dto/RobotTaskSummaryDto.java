package com.czkuo.rdf88701.presentation.web.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RobotTaskSummaryDto {

    /** PK: robot_r007_task.id / robot_r008_task.id / ... */
    private Long id;

    /** CMD: "R007" / "R008" / "R029" / "R031" */
    private String cmd;

    /** TID (yyyyMMddHHmmssSSS) */
    private String tid;

    /** LOT_ID（有些任務沒有，就給空字串） */
    private String lotId;

    /** CARRIERID（有些沒有就空字串） */
    private String carrierId;

    /** 來源位置（WIP/STK/EQP），UI 顯示 From */
    private String fromLoc;

    /** 目的位置（EQP/WIP/Manual/ZIP），UI 顯示 To */
    private String toLoc;

    /** 機台 Port（若有） */
    private String eqpPort;

    /** TrayType 或其他關鍵描述（可選） */
    private String trayType;

    /** 內部狀態字串（internal_state） */
    private String internalState;

    /** 對外最後結果（external_last_result） */
    private String externalLastResult;

    /** 對外最後結果時間（external_last_time） */
    private LocalDateTime externalLastTime;

    /** 建立時間（created_time） */
    private LocalDateTime createdTime;

    /** 失敗原因（FAIL） */
    private String failReason;

    /** 取消原因（CANCEL） */
    private String cancelReason;
}
