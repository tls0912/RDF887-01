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
 * R007 任務主表（Worker 決策 STK_PORT；簡化內部狀態 + 對外結果快取）
 * </p>
 *
 * @author czkuo
 * @since 2025-10-20
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@Setter
@ToString
@TableName("robot_r007_task")
public class RobotR007Task {

    /**
     * PK
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 對應 mqtt_message_log.id（入站 R007）
     */
    private Long logId;

    /**
     * 對應 mqtt_inbox.id（入佇列那筆，可為 NULL）
     */
    private Long inboxId;

    /**
     * R007.TID（例：yyyyMMddHHmmssSSS）
     */
    private String tid;

    /**
     * LOT_ID
     */
    private String lotId;

    /**
     * CARRIERID（修正命名）
     */
    private String carrierId;

    /**
     * WIPNAME（來源儲格/站點）
     */
    private String wipName;

    /**
     * DEST_LOC（目的設備）
     */
    private String destLoc;

    /**
     * EQP_PORT（目的 Port）
     */
    private String eqpPort;

    /**
     * DEVICE_NAME（允許空字串或 NULL）
     */
    private String deviceName;

    /**
     * ZIP 出料 Port（由 Worker 決策後寫入）
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
     * TRAY_NUM
     */
    private Integer trayNum;

    /**
     * MOVE_PRIORITY
     */
    private Integer movePriority;

    /**
     * MISSION_TRIP
     */
    private String missionTrip;

    /**
     * ODO
     */
    private BigDecimal odo;

    /**
     * AMR_SPEED
     */
    private BigDecimal amrSpeed;

    /**
     * AMR_ROBOT_SPEED
     */
    private BigDecimal amrRobotSpeed;

    /**
     * PPKG_BODY_SIZE
     */
    private String ppkgBodySize;

    /**
     * FLIP（翻轉 Y/N）
     */
    private String flip;

    /**
     * 是否需要 ZIP 派單
     */
    private Boolean zipRequired;

    /**
     * 是否需要 AMR 轉發
     */
    private Boolean amrRequired;

    /**
     * ZIP 派單狀態
     */
    private String zipState;

    /**
     * ZIP 派單嘗試次數
     */
    private Integer zipAttempts;

    /**
     * 上次派單時間
     */
    private LocalDateTime zipLastAttemptTime;

    /**
     * ZIP 接單（Result=0）時間
     */
    private LocalDateTime zipAcceptTime;

    /**
     * ZIP 回傳 Result/錯誤碼
     */
    private String zipResultCode;

    /**
     * ZIP 回傳訊息
     */
    private String zipResultMessage;

    /**
     * 最後一次派單 Request JSON
     */
    private String zipRequestJson;

    /**
     * 最後一次派單 Response JSON
     */
    private String zipResponseJson;

    /**
     * 轉發給 AMR 的 TID（通常沿用原 R007.TID；若策略不同可另編號）
     */
    private String amrTid;

    /**
     * AMR 轉發狀態
     */
    private String amrState;

    /**
     * AMR 轉發嘗試次數
     */
    private Integer amrAttempts;

    /**
     * 上次轉發時間
     */
    private LocalDateTime amrLastAttemptTime;

    /**
     * 最後一次 ACK 時間
     */
    private LocalDateTime amrLastAckTime;

    /**
     * 最後一次 ACK 的補充訊息
     */
    private String amrResultMessage;

    /**
     * 對應 mqtt_message_log.id（我方發出的 R007 COMMAND）
     */
    private Long amrForwardLogId;

    /**
     * 收到 START ACK 的 mqtt_message_log.id
     */
    private Long amrAckStartLogId;

    /**
     * 收到 END ACK 的 mqtt_message_log.id
     */
    private Long amrAckEndLogId;

    /**
     * 最後一次轉發給 AMR 的 R007（MESSAGE 部分）
     */
    private String amrRequestJson;

    /**
     * 最後一次收到的 ACK JSON
     */
    private String amrLastAckJson;

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
     * R007.MESSAGE 原樣序列化
     */
    private String rawMessageJson;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
