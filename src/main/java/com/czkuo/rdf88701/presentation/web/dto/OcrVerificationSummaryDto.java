package com.czkuo.rdf88701.presentation.web.dto;

import lombok.Data;

import java.time.LocalDateTime;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Data
public class OcrVerificationSummaryDto {

    private Long id;
    private Long containerMainId;

    private String carrierId;
    private String lotId;
    private String trayType;

    // 自判 + S073 + 人判狀態
    private boolean localPass;
    private boolean badOcr;
    private boolean partMatch;
    private boolean ocr1Match;
    private boolean ocr2Match;

    private String s073Status;      // NOT_SENT / SENT / PASS / FAIL / ERROR
    private String manualDecision;  // N_A / PENDING / ALLOW / BLOCK
    private String finalResult;     // PASS / BLOCK / CANCEL / null

    private LocalDateTime createdTime;
}
