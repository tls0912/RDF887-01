package com.czkuo.rdf88701.domain.dto.zip.DispatchOrder;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Data
public class DispatchOrderPrimaryBody {
    @JsonProperty("Magazines") private List<String> magazines;
    @JsonProperty("STK_PORT")  @JsonInclude(JsonInclude.Include.NON_NULL)
    private String stkPort; // FSK7003 需要；FSK7004 只用 LOT_ID 變色可不帶
}
