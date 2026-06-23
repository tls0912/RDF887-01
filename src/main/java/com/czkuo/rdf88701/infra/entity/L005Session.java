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
 * L005 會話：對方檢核結果與我方進度分欄；只註記失效
 * </p>
 *
 * @author czkuo
 * @since 2025-10-18
 */
@Getter
@Setter
@ToString
@TableName("l005_session")
public class L005Session {

    /** 流水號 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 條碼（trim 後） */
    private String barcode;

    /** L005 會話唯一識別（TID） */
    private String tid;

    // =====================================================
    // 內部狀態機（簡化）
    // =====================================================

    /**
     * 內部狀態（INIT → SENT → ACKED → COMPLETED / FAILED）
     */
    private String internalState;

    /**
     * 等待 ACK 的截止時間（逾時轉 FAILED）
     */
    private LocalDateTime ackDeadlineAt;

    // =====================================================
    // 對外結果（對應入庫任務語意）
    // =====================================================

    /** 對外最後結果（OK / START / END / FAIL / CANCEL） */
    private String externalLastResult;

    /** 對外最後結果時間 */
    private LocalDateTime externalLastTime;

    /** 失敗原因（FAIL / FAILED / TIMEOUT 等情境） */
    private String failReason;

    // =====================================================
    // 對方 ACK 資料
    // =====================================================

    /** 對方條碼檢核結果（PASS / FAIL） */
    private String peerResult;

    /** 對方結果訊息 */
    private String peerResultMsg;

    private String peerCarrierId;
    private String peerLotId;
    private String peerTrayHigh;
    private String peerTrayType;
    private String peerMsgType;

    /** 對方 ACK 時間 */
    private LocalDateTime peerAckAt;

    /** 對方 ACK 原始 JSON Payload */
    private String peerAckPayloadJson;

    // =====================================================
    // 管理欄位
    // =====================================================

    /** 是否現役（1=有效, 0=失效） */
    private Boolean isValid;

    /** 被哪個新 TID 取代（僅被取代時填） */
    private String invalidByTid;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
