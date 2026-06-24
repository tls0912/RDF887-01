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
 * HMI 訊息（中/英）
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
@TableName("hmi_display_task")
public class HmiDisplayTask {

    /**
     * PK
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * S019 的 TID（yyyyMMddHHmmssSSS），冪等用
     */
    private String tid;

    /**
     * 中文訊息（入庫保存用，不寫 PLC）
     */
    private String msgCh;

    /**
     * 英文訊息（實際要寫入 PLC 的內容）
     */
    private String msgEn;

    /**
     * 任務狀態：PENDING=待寫、SENT=已寫入成功、FAILED=寫入失敗
     */
    private String status;

    /**
     * 已嘗試寫入次數（重試用）
     */
    private Integer attempts;

    /**
     * 最後一次錯誤訊息（失敗時紀錄）
     */
    private String lastError;

    /**
     * 建立時間（入列時間）
     */
    private LocalDateTime createdAt;

    /**
     * 最後更新時間
     */
    private LocalDateTime updatedAt;

    /**
     * 實際成功寫入 PLC 的時間（SENT 時填）
     */
    private LocalDateTime sentAt;
}
