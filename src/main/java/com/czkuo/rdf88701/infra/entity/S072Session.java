package com.czkuo.rdf88701.infra.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * <p>
 * S072 拍照與檢查會話表（支援單/雙拍照模式）
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
@TableName("s072_session")
public class S072Session {

    /**
     * 主鍵 ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 工作站名稱，例如 STK01/STK02
     */
    private String stationName;

    /**
     * 相機 IP，例如 192.168.3.250
     */
    private String cameraIp;

    /**
     * 條碼（如有）
     */
    private String barcode;

    /**
     * 載具 ID（主要識別用）
     */
    private String carrierId;

    /**
     * 批次號（可選）
     */
    private String lotId;

    /**
     * Tray 類型（可選）
     */
    private String trayType;

    /**
     * 拍照圖像檔案路徑 #1（左或第一次）
     */
    @TableField("image_path_1")
    private String imagePath1;

    /**
     * 拍照圖像檔案路徑 #2（右或第二次）
     */
    @TableField("image_path_2")
    private String imagePath2;

    /**
     * 拍照時間 #1
     */
    @TableField("captured_at_1")
    private LocalDateTime capturedAt1;

    /**
     * 拍照時間 #2
     */
    @TableField("captured_at_2")
    private LocalDateTime capturedAt2;

    /**
     * 拍照模式：DUAL=分兩次拍，SINGLE=一次同時拍
     */
    private String captureMode;

    /** S072 會話唯一識別（TID） */
    private String tid;

    /**
     * ASE 回覆結果（OK=PASS, NG=FAIL）
     */
    private String result;

    /**
     * ASE 回覆訊息
     */
    private String resultMessage;

    /**
     * 拍照或發送異常說明
     */
    private String errorMessage;

    /**
     * 流程狀態
     */
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
