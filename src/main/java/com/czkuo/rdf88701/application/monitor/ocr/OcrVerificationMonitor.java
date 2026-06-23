package com.czkuo.rdf88701.application.monitor.ocr;

import com.czkuo.rdf88701.application.service.PlcAccessService;
import com.czkuo.rdf88701.domain.repository.OcrVerificationRepository;
import com.czkuo.rdf88701.infra.entity.OcrVerification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OcrVerificationMonitor {

    private final PlcAccessService plc;
    private final OcrVerificationRepository ocrVerificationRepository;

    private static final String PLC_DEVICE   = "PLC-Main";
    private static final String WORD_ALARM   = "W0009";

    @Scheduled(fixedDelay = 600)
    public void monitor() {
        try {
            List<OcrVerification> list = ocrVerificationRepository
                    .findPendingManualDecisions(100);

            if (!list.isEmpty()) {
                int cur = plc.readUInt16(PLC_DEVICE, WORD_ALARM);
                int next = cur | (1 << 1);
                if (next != cur) {
                    plc.writeUInt16(PLC_DEVICE, WORD_ALARM, next);
                    log.info("[OcrVerification] PC->{} set bit1 = 1", WORD_ALARM);
                } else {
                    //log.debug("[OcrVerification] PC->{} bit1 已是 1，略過寫入", WORD_ALARM);
                }
            } else {
                int cur = plc.readUInt16(PLC_DEVICE, WORD_ALARM);
                int next = cur & ~(1 << 1);
                if (next != cur) {
                    plc.writeUInt16(PLC_DEVICE, WORD_ALARM, next);
                    log.info("[OcrVerification] PC->{} set bit1 = 0", WORD_ALARM);
                } else {
                    //log.debug("[OcrVerification] PC->{} bit1 已是 0，略過寫入", WORD_ALARM);
                }
            }
        } catch (Exception e) {
            log.error("[OcrVerification] 置位失敗：word={}, bit={}, err={}", WORD_ALARM, 1, e.getMessage(), e);
        }
    }
}
