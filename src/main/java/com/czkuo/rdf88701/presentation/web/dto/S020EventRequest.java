package com.czkuo.rdf88701.presentation.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * S020 事件上報 API 的請求 DTO（v2）
 *
 * 設計：
 * - receiver：接收端系統（ase / seec ...）
 * - 其他欄位全部可選；只序列化非空值，避免傳一堆 null。
 * - 1D_BARCODE 的鍵名以數字開頭，需用 @JsonProperty 顯式標註。
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class S020EventRequest {

    /** 目標系統（例：ase） */
    @NotBlank
    private String receiver;

    /** 事件代碼（例：2003）。有些舊格式可能留空，這裡不強制必填。 */
    private String ceid;

    private String ceidDescEn;
    private String ceidDescCh;

    /** Port/設備狀態（Idle/Run/Executing...） */
    private String status;

    /** 批號（LOT_ID："11YT11V001"） */
    @JsonProperty("LOT_ID")
    private String lotId;

    /** 載具（CARRIERID："TY00021VM"） */
    @JsonProperty("CARRIERID")
    private String carrierId;

    /** 類型（TYPE："STK"/"OUT"...） */
    @JsonProperty("TYPE")
    private String type;

    /** WIP 名稱（"1025"...） */
    @JsonProperty("WIPNAME")
    private String wipname;

    /** 數量（"20"...） */
    @JsonProperty("NUM")
    private String num;

    /** 一維條碼（鍵名以數字開頭） */
    @JsonProperty("1D_BARCODE")
    private String oneDBarcode;
}
