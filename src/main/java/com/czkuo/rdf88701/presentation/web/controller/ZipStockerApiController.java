package com.czkuo.rdf88701.presentation.web.controller;

import com.czkuo.rdf88701.application.service.zip.ZipStockerEventService;
import com.czkuo.rdf88701.domain.dto.zip.CCDPlatformInput.CCDPlatformInputPrimaryBody;
import com.czkuo.rdf88701.domain.dto.zip.CCDPlatformInput.CCDPlatformInputSecondaryBody;
import com.czkuo.rdf88701.domain.dto.zip.CardReader.CardReaderPrimaryBody;
import com.czkuo.rdf88701.domain.dto.zip.CardReader.CardReaderSecondaryBody;
import com.czkuo.rdf88701.domain.dto.zip.CarrierFlip.CarrierFlipPrimaryBody;
import com.czkuo.rdf88701.domain.dto.zip.CarrierFlip.CarrierFlipSecondaryBody;
import com.czkuo.rdf88701.domain.dto.zip.StatusReport.StatusReportPrimaryBody;
import com.czkuo.rdf88701.domain.dto.zip.StatusReport.StatusReportSecondaryBody;
import com.czkuo.rdf88701.domain.dto.zip.StockerInput.StockerInputPrimaryBody;
import com.czkuo.rdf88701.domain.dto.zip.StockerInput.StockerInputSecondaryBody;
import com.czkuo.rdf88701.domain.dto.zip.StockerOutput.StockerOutputPrimaryBody;
import com.czkuo.rdf88701.domain.dto.zip.StockerOutput.StockerOutputSecondaryBody;
import com.czkuo.rdf88701.domain.dto.zip.common.Header;
import com.czkuo.rdf88701.domain.dto.zip.common.Root;
import com.czkuo.rdf88701.infra.zip.ZipHeaders;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ZIP → MCS WebAPI（MCS 端）
 * - 僅接收 ZIP 發來的 Primary，回覆 Secondary（回原生 JSON，不能包 ResponseResult）
 * - 本版本增強：將「請求/回覆」以 JSON 形式完整記錄到日誌，方便稽核追蹤
 */
@Slf4j
@RestController
@RequestMapping("/api/stocker2mcs")
@RequiredArgsConstructor
@Validated
public class ZipStockerApiController {

    private static final String SENDER = "MCS";

    /** 單筆 log 最多輸出字元數，避免過長 payload 撐爆日誌（可視需要調整或抽到設定檔） */
    private static final int MAX_LOG_LEN = 8000;

    private final ZipStockerEventService zipStockerEventService;

    /** 用於將 Header/Body 序列化為 JSON，避免 toString() 不可讀 */
    private final ObjectMapper objectMapper;

    // =============================== Endpoints ===============================

    /** ZIP -> MCS：入庫上報 */
    @PostMapping("/StockerInput")
    public Root<StockerInputSecondaryBody> stockerInput(@RequestBody @Valid Root<StockerInputPrimaryBody> req) {
        final long t0 = System.currentTimeMillis();
        validateHeader(req.getHeader(), "StockerInput");

        // 收到 Primary：完整記錄 header/body
        log.info("[ZIP] StockerInput Primary 收到：header={}, body={}",
                clip(json(req.getHeader())), clip(json(req.getBody())));

        // 業務處理
        StockerInputSecondaryBody body = zipStockerEventService.onStockerInput(req.getHeader(), req.getBody());

        // 包 Secondary，並記錄回覆
        Root<StockerInputSecondaryBody> resp = wrap("StockerInput", body);
        log.info("[ZIP] StockerInput Secondary 回覆：header={}, body={}, tookMs={}",
                clip(json(resp.getHeader())), clip(json(resp.getBody())), (System.currentTimeMillis() - t0));

        return resp;
    }

    /** ZIP -> MCS：出庫上報 */
    @PostMapping("/StockerOutput")
    public Root<StockerOutputSecondaryBody> stockerOutput(@RequestBody @Valid Root<StockerOutputPrimaryBody> req) {
        final long t0 = System.currentTimeMillis();
        validateHeader(req.getHeader(), "StockerOutput");

        log.info("[ZIP] StockerOutput Primary 收到：header={}, body={}",
                clip(json(req.getHeader())), clip(json(req.getBody())));

        StockerOutputSecondaryBody body = zipStockerEventService.onStockerOutput(req.getBody());

        Root<StockerOutputSecondaryBody> resp = wrap("StockerOutput", body);
        log.info("[ZIP] StockerOutput Secondary 回覆：header={}, body={}, tookMs={}",
                clip(json(resp.getHeader())), clip(json(resp.getBody())), (System.currentTimeMillis() - t0));

        return resp;
    }

    /** ZIP -> MCS：狀態變更上報 */
    @PostMapping("/StatusReport")
    public Root<StatusReportSecondaryBody> statusReport(@RequestBody @Valid Root<StatusReportPrimaryBody> req) {
        final long t0 = System.currentTimeMillis();
        validateHeader(req.getHeader(), "StatusReport");

        // 之前你只 log header，這裡補上 body
        log.info("[ZIP] StatusReport Primary 收到：header={}, body={}",
                clip(json(req.getHeader())), clip(json(req.getBody())));

        StatusReportSecondaryBody body = zipStockerEventService.onStatusReport(req.getHeader(), req.getBody());

        Root<StatusReportSecondaryBody> resp = wrap("StatusReport", body);
        log.info("[ZIP] StatusReport Secondary 回覆：header={}, body={}, tookMs={}",
                clip(json(resp.getHeader())), clip(json(resp.getBody())), (System.currentTimeMillis() - t0));

        return resp;
    }

    /** ZIP -> MCS：讀卡事件 */
    @PostMapping("/CardReader")
    public Root<CardReaderSecondaryBody> cardReader(@RequestBody @Valid Root<CardReaderPrimaryBody> req) {
        final long t0 = System.currentTimeMillis();
        validateHeader(req.getHeader(), "CardReader");

        log.info("[ZIP] CardReader Primary 收到：header={}, body={}",
                clip(json(req.getHeader())), clip(json(req.getBody())));

        CardReaderSecondaryBody body = zipStockerEventService.onCardReader(req.getHeader(), req.getBody());

        Root<CardReaderSecondaryBody> resp = wrap("CardReader", body);
        log.info("[ZIP] CardReader Secondary 回覆：header={}, body={}, tookMs={}",
                clip(json(resp.getHeader())), clip(json(resp.getBody())), (System.currentTimeMillis() - t0));

        return resp;
    }

    /** ZIP -> MCS：翻轉事件 */
    @PostMapping("/CarrierFlip")
    public Root<CarrierFlipSecondaryBody> carrierFlip(@RequestBody @Valid Root<CarrierFlipPrimaryBody> req) {
        final long t0 = System.currentTimeMillis();
        validateHeader(req.getHeader(), "CarrierFlip");

        log.info("[ZIP] CarrierFlip Primary 收到：header={}, body={}",
                clip(json(req.getHeader())), clip(json(req.getBody())));

        CarrierFlipSecondaryBody body = zipStockerEventService.onCarrierFlip(req.getHeader(), req.getBody());

        Root<CarrierFlipSecondaryBody> resp = wrap("CarrierFlip", body);
        log.info("[ZIP] CarrierFlip Secondary 回覆：header={}, body={}, tookMs={}",
                clip(json(resp.getHeader())), clip(json(resp.getBody())), (System.currentTimeMillis() - t0));

        return resp;
    }

    /** ZIP -> MCS：間隙檢事件 */
    @PostMapping("/CCDPlatformInput")
    public Root<CCDPlatformInputSecondaryBody> ccdPlatformInput(@RequestBody @Valid Root<CCDPlatformInputPrimaryBody> req) {
        final long t0 = System.currentTimeMillis();
        validateHeader(req.getHeader(), "CCDPlatformInput");

        log.info("[ZIP] CCDPlatformInput Primary 收到：header={}, body={}",
                clip(json(req.getHeader())), clip(json(req.getBody())));

        CCDPlatformInputSecondaryBody body = zipStockerEventService.onCCDPlatformInput(req.getHeader(), req.getBody());

        Root<CCDPlatformInputSecondaryBody> resp = wrap("CCDPlatformInput", body);
        log.info("[ZIP] CCDPlatformInput Secondary 回覆：header={}, body={}, tookMs={}",
                clip(json(resp.getHeader())), clip(json(resp.getBody())), (System.currentTimeMillis() - t0));

        return resp;
    }

    // =============================== Helpers ===============================

    /** 包 Secondary Header（Direction=Secondary, Sender=MCS） */
    private <T> Root<T> wrap(String eventName, T body) {
        Root<T> resp = new Root<>();
        Header h = ZipHeaders.of(eventName, "Secondary", SENDER);
        resp.setHeader(h);
        resp.setBody(body);
        return resp;
    }

    /** 輕量 Header 檢查（事件名需匹配 endpoint；方向需為 Primary） */
    private void validateHeader(Header h, String expectedEvent) {
        if (h == null) {
            log.warn("[ZIP] Header 為 null（放行處理）");
            return;
        }
        if (!expectedEvent.equals(h.getEventName())) {
            log.warn("[ZIP] EventName 不符：expected={}, actual={}", expectedEvent, h.getEventName());
        }
        if (!"Primary".equalsIgnoreCase(h.getDirection())) {
            log.warn("[ZIP] Direction 非 Primary：{}", h.getDirection());
        }
        // 可視需求再檢查 Sender 是否 "ZIP"
    }

    /** 物件轉 JSON 字串；失敗時退回 toString()，避免日誌中斷 */
    private String json(Object obj) {
        if (obj == null) return "null";
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            // 轉換失敗時仍給出可讀資訊
            return String.valueOf(obj);
        }
    }

    /** 截斷超長字串，避免爆 log；附上原始長度提示 */
    private String clip(String s) {
        if (s == null) return "null";
        if (s.length() <= MAX_LOG_LEN) return s;
        return s.substring(0, MAX_LOG_LEN) + "...(truncated," + s.length() + " chars)";
    }
}
