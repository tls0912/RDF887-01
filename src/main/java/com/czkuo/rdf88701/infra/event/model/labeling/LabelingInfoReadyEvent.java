package com.czkuo.rdf88701.infra.event.model.labeling;

import com.czkuo.rdf88701.infra.entity.LabelingInfo;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 貼標資訊就緒事件（READY）
 *
 * 用途：
 * - 當系統已接收/入庫一筆可列印的貼標資訊（S065/S066）或
 *   在站點水位線（watermark）之後出現了新資料時，對外通知。
 *
 * 欄位：
 * - siteCode：站點（例：Site#30 / Site#37）
 * - containerMainId：容器（可為 null）
 * - labelNo：建議使用的標籤號（預設 1；可為 null）
 * - info：對應的 LabelingInfo 物件（可為 null；如果你只想透過 site/container 取用就留空）
 *
 * 發送建議：
 *   publisher.publishEvent(new LabelingInfoReadyEvent(this, siteCode, containerMainId, 1));
 *   // 或：publisher.publishEvent(new LabelingInfoReadyEvent(this, labelingInfo));
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
public class LabelingInfoReadyEvent extends ApplicationEvent {

    private final String siteCode;
    private final Long containerMainId;
    private final Integer labelNo;
    private final LabelingInfo info; // 可為 null

    /** 以站點/容器/標籤號為主體的事件（常用） */
    public LabelingInfoReadyEvent(Object source, String siteCode, Long containerMainId, Integer labelNo) {
        super(source);
        this.siteCode = siteCode;
        this.containerMainId = containerMainId;
        this.labelNo = labelNo;
        this.info = null;
    }

    /** 直接包一個 LabelingInfo 的事件（若當下就有那筆資料可一併傳遞） */
    public LabelingInfoReadyEvent(Object source, LabelingInfo info) {
        super(source);
        this.info = info;
        this.siteCode = info != null ? info.getSiteCode() : null;
        this.containerMainId = info != null ? info.getContainerMainId() : null;
        this.labelNo = info != null ? info.getLabelNo() : null;
    }
}
