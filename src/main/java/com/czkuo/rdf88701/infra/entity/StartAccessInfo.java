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
 * RESET/START 驗證資訊
 * </p>
 *
 * @author czkuo
 * @since 2025-08-25
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@Setter
@ToString
@TableName("start_access_info")
public class StartAccessInfo {

    /**
     * PK
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 對應 S013 的 TID（yyyyMMddHHmmssSSS），冪等鍵
     */
    private String tid;

    /**
     * 啟動對象：WIP / ZIPA / ZIPB / FSK6001A
     */
    private String targetCode;

    /**
     * 請求值：1=START, 256=RESET
     */
    private Short reqValue;

    /**
     * 狀態：送出待回覆/已回/逾時/取消
     */
    private String status;

    /**
     * ACK 結果：OK/NG
     */
    private String ackResult;

    /**
     * ACK 結果說明（如 NG 原因）
     */
    private String ackMessage;

    /**
     * 通過驗證的人員工號清單（JSON array）
     */
    private String staffList;

    /**
     * 收到 ACK 的時間
     */
    private LocalDateTime ackAt;

    /**
     * 重送次數（若有）
     */
    private Integer retries;

    /**
     * 最後一筆錯誤資訊
     */
    private String lastError;

    /**
     * 寫 PLC 狀態
     */
    private String writebackStatus;

    /**
     * 寫 PLC 嘗試次數
     */
    private Integer writebackAttempts;

    /**
     * 最後一次寫 PLC 的錯誤
     */
    private String writebackError;

    /**
     * 成功寫 PLC 的時間
     */
    private LocalDateTime writtenAt;

    /**
     * 建立時間
     */
    private LocalDateTime createdAt;

    /**
     * 最後更新時間
     */
    private LocalDateTime updatedAt;
}
