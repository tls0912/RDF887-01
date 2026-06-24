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
 * 安全門開/關檢核資訊
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
@TableName("door_access_info")
public class DoorAccessInfo {

    /**
     * PK
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 對應 S011/S012 的 TID（yyyyMMddHHmmssSSS），冪等鍵
     */
    private String tid;

    /**
     * 門號（1..N）
     */
    private Integer doorNo;

    /**
     * 請求值：1=OPEN(開門), 2=CLOSE(關門)
     */
    private Byte reqValue;

    /**
     * 請求狀態：PENDING=已送出待回覆；ACK_OK/ACK_NG=已回；TIMEOUT=逾時；CANCELLED=取消
     */
    private String status;

    /**
     * ACK 結果：OK/NG（回覆後填）
     */
    private String ackResult;

    /**
     * ACK 結果說明（如 NG 的原因）
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
     * 已重送次數（如需）
     */
    private Integer retries;

    /**
     * 最後一次錯誤訊息（如逾時標記或其他）
     */
    private String lastError;

    /**
     * 寫 PLC 狀態：WAITING=等寫、WRITTEN=已寫、FAILED=寫失敗
     */
    private String writebackStatus;

    /**
     * 寫 PLC 嘗試次數
     */
    private Integer writebackAttempts;

    /**
     * 寫 PLC 失敗最後錯誤
     */
    private String writebackError;

    /**
     * 實際寫入 PLC 的時間
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
