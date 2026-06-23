package com.czkuo.rdf88701.application.dto.report.ocr;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OcrManualLogRow {
    public OcrManualLogRow(String carrierId,
                           String currOcrText1,
                           String currOcrText2,
                           String refSite,
                           String refCarrierId,
                           String refOcrText1,
                           String refOcrText2,
                           String manualDecision,
                           String manualBy,
                           LocalDateTime manualTime) {
        CarrierId = carrierId;
        CurrOcrText1 = currOcrText1;
        CurrOcrText2 = currOcrText2;
        RefSite = refSite;
        RefCarrierId = refCarrierId;
        RefOcrText1 = refOcrText1;
        RefOcrText2 = refOcrText2;
        ManualDecision = manualDecision;
        ManualBy = manualBy;
        ManualTime = manualTime;
    }

    String CarrierId;
    String CurrOcrText1;
    String CurrOcrText2;
    String RefSite;
    String RefCarrierId;
    String RefOcrText1;
    String RefOcrText2;
    String ManualDecision;
    String ManualBy;
    LocalDateTime ManualTime;
}
