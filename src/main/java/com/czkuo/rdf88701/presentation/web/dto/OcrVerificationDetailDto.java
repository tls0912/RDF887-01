package com.czkuo.rdf88701.presentation.web.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Data
public class OcrVerificationDetailDto {

    private Long id;
    private Long containerMainId;

    private String carrierId;
    private String lotId;
    private String trayType;

    private String refSite;
    private Long refContainerId;

    // OCR 文字
    private String currOcrText1;  // back
    private String currOcrText2;  // front
    private String refOcrText1;
    private String refOcrText2;

    // 自判 flags
    private boolean localPass;
    private boolean badOcr;
    private boolean partMatch;
    private boolean ocr1Match;
    private boolean ocr2Match;

    // S073 / 人判
    private String s073Tid;
    private String s073Status;
    private String s073ResultCode;
    private String manualDecision;
    private String manualBy;
    private LocalDateTime manualTime;
    private String finalResult;

    // 圖片（Tray 本體 / 上蓋）—— 這裡用 dataURL / base64 string 就可以
    private List<String> trayImages;   // TRAY_* 對應的那時候 4 張 (或少於 4)
    private List<String> coverImages;  // UPPER_COVER_* 那時候 4 張

    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
