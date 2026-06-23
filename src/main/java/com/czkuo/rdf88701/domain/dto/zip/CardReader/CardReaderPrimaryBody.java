package com.czkuo.rdf88701.domain.dto.zip.CardReader;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CardReaderPrimaryBody {
    @JsonProperty("CardID") private String cardID;
}
