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
 * @author matt
 * @since 2026-2-11
 */
@Getter
@Setter
@ToString
@TableName("ocr_manual_log")
public class OcrManualLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 這顆 tray 的 container_main.id */
    private Long containerMainId;

    /** 本體 OCR_TEXT1（back） */
    private String currOcrText1;

    /** 本體 OCR_TEXT2（front） */
    private String currOcrText2;

    /** 上蓋站點（Site#12 / Site#14） */
    private String refSite;

    /** 上蓋的 container_main.id（若有） */
    private Long refContainerId;

    /** 上蓋 OCR_TEXT1（back） */
    private String refOcrText1;

    /** 上蓋 OCR_TEXT2（front） */
    private String refOcrText2;

    /** 人工判定：N_A / PENDING / ALLOW / BLOCK */
    private String manualDecision;

    /** 人工判定者 */
    private String manualBy;

    /** 人工判定時間 */
    private LocalDateTime manualTime;
}
