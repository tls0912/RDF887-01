package com.czkuo.rdf88701.domain.dto.zip.CancelDispatchOrder;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class CancelDispatchOrderPrimaryBody {
    @JsonProperty("Magazines") private List<String> magazines;
}
