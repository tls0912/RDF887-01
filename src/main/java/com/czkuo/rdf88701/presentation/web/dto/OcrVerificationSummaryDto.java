package com.czkuo.rdf88701.presentation.web.dto;

import lombok.Data;

import java.time.LocalDateTime;

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
