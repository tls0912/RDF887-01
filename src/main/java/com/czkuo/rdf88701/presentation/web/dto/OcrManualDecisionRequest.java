package com.czkuo.rdf88701.presentation.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

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
