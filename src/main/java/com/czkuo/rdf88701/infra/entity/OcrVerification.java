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
 * 
 * </p>
 *
 * @author czkuo
 * @since 2025-11-18
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@Setter
@ToString
@TableName("ocr_verification")
public class OcrVerification {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 這顆 tray 的 container_main.id */
    private Long containerMainId;

    /** ACTIVE / CLOSED */
    private String state;

    /** 取自 container_main.alias_code */
    private String carrierId;

    /** 取自 container_main.lot_no */
    private String lotId;

    /** 取自 container_main.part_no */
    private String trayType;

    /** 上蓋站點（Site#12 / Site#14） */
    private String refSite;

    /** 上蓋的 container_main.id（若有） */
    private Long refContainerId;

    /** 本體 OCR_TEXT1（back） */
    private String currOcrText1;

    /** 本體 OCR_TEXT2（front） */
    private String currOcrText2;

    /** 上蓋 OCR_TEXT1（back） */
    private String refOcrText1;

    /** 上蓋 OCR_TEXT2（front） */
    private String refOcrText2;

    /** 自判結果：Y/N（part + ocr1 + ocr2 全一致且 OCR 合格） */
    private String localPass;

    /** OCR 是否不適合自動放行：Y/N（任一欄空或含 fail） */
    private String badOcr;

    /** 料號是否有 match 上蓋：Y/N */
    private String partMatch;

    /** OCR_TEXT1 是否 match：Y/N */
    private String ocr1Match;

    /** OCR_TEXT2 是否 match：Y/N */
    private String ocr2Match;

    /** 該次 S073 的 TID（若有送） */
    private String s073Tid;

    /** S073 狀態：NOT_SENT / SENT / PASS / FAIL / ERROR */
    private String s073Status;

    /** S073 結果碼（設備回傳的 result / error code） */
    private String s073ResultCode;

    /** S073 第一次送出的時間（第一次 SENT 時寫入；重送不覆蓋此欄位） */
    private LocalDateTime s073SentTime;

    /** S073 已重送次數（0=尚未重送；每次 timeout 重送 +1） */
    private Integer s073RetryCount;

    /** S073 最後一次重送時間（每次 retry 時寫入） */
    private LocalDateTime s073LastRetryTime;

    /** S073 下次允許重送時間（sent 或 retry 時寫入：now + retryInterval） */
    private LocalDateTime s073NextRetryTime;

    /** 人工判定：N_A / PENDING / ALLOW / BLOCK */
    private String manualDecision;

    /** 人工判定者 */
    private String manualBy;

    /** 人工判定時間 */
    private LocalDateTime manualTime;

    /** 最終結果：PASS / BLOCK / CANCEL 等 */
    private String finalResult;

    /** 建立時間 */
    private LocalDateTime createdTime;

    /** 更新時間 */
    private LocalDateTime updatedTime;

    /** 本體：後一燈 */
    private String currBackOneLightPath;

    /** 本體：後三燈 */
    private String currBackThreeLightPath;

    /** 本體：前一燈 */
    private String currFrontOneLightPath;

    /** 本體：前三燈 */
    private String currFrontThreeLightPath;

    /** 上蓋：後一燈 */
    private String refBackOneLightPath;

    /** 上蓋：後三燈 */
    private String refBackThreeLightPath;

    /** 上蓋：前一燈 */
    private String refFrontOneLightPath;

    /** 上蓋：前三燈 */
    private String refFrontThreeLightPath;
}
