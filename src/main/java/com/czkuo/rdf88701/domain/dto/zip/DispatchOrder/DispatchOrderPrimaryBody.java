package com.czkuo.rdf88701.domain.dto.zip.DispatchOrder;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class DispatchOrderPrimaryBody {
    @JsonProperty("Magazines") private List<String> magazines;
    @JsonProperty("STK_PORT")  @JsonInclude(JsonInclude.Include.NON_NULL)
    private String stkPort; // FSK7003 需要；FSK7004 只用 LOT_ID 變色可不帶
}
