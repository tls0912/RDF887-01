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
 * R008 任務主表（可計算欄位 + 狀態機 + 對外結果快取）
 * </p>
 *
 * @author czkuo
 * @since 2025-10-18
 */
@Getter
@Setter
@ToString
@TableName("robot_r008_task")
public class RobotR008Task {

    /**
     * PK
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 對應 mqtt_message_log.id（入站 R008）
     */
    private Long logId;

    /**
     * 對應 mqtt_inbox.id（入佇列那筆，可為 NULL）
     */
    private Long inboxId;

    /**
     * R008.TID（例：yyyyMMddHHmmssSSS）
     */
    private String tid;

    /**
     * LOT_ID
     */
    private String lotId;

    /**
     * CARRIERID
     */
    private String carrierId;

    /**
     * WIPNAME（目標儲位，可為 NULL）
     */
    private String wipName;

    /**
     * DEST_LOC（來源機台）
     */
    private String destLoc;

    /**
     * EQP_PORT（來源機台 Port）
     */
    private String eqpPort;

    /**
     * DEVICE_NAME（允許空/NULL）
     */
    private String deviceName;

    /**
     * 內部 SAA→SEEC 才會有；ASE→廠商禁止
     */
    private String stkPort;

    /**
     * TRAY_HIGH
     */
    private BigDecimal trayHigh;

    /**
     * TRAY_TYPE（料號）
     */
    private String trayType;

    /**
     * BIN_TYPE（G=GOOD, B=BAD, E=EMPTY）
     */
    private String binType;

    /**
     * TRAY_NUM
     */
    private Integer trayNum;

    /**
     * MOVE_PRIORITY（越大越高）
     */
    private Integer movePriority;

    /**
     * MISSION_TRIP（本任務里程）
     */
    private String missionTrip;

    /**
     * ODO（累積里程）
     */
    private BigDecimal odo;

    /**
     * AMR_SPEED（底車速度）
     */
    private BigDecimal amrSpeed;

    /**
     * AMR_ROBOT_SPEED（機械手臂速度）
     */
    private BigDecimal amrRobotSpeed;

    /**
     * PPKG_BODY_SIZE
     */
    private String ppkgBodySize;

    /**
     * 內部狀態機（簡化）
     */
    private String internalState;

    /**
     * 對外最後結果
     */
    private String externalLastResult;

    /**
     * 對外最後結果時間
     */
    private LocalDateTime externalLastTime;

    /**
     * 失敗原因（FAIL 時必填）
     */
    private String failReason;

    /**
     * 取消原因（CANCEL 時可填）
     */
    private String cancelReason;

    /**
     * R008.MESSAGE 原樣序列化
     */
    private String rawMessageJson;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
