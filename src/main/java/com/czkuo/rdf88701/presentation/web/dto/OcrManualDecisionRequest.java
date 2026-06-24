package com.czkuo.rdf88701.presentation.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Data
public class OcrManualDecisionRequest {

    /** ALLOW / BLOCK */
    @JsonProperty("Decision")
    private String decision;

    /** 操作者帳號 / 姓名 */
    @JsonProperty("User")
    private String user;

    /** 備註 */
    @JsonProperty("Note")
    private String note;
}
