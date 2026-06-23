package com.czkuo.rdf88701.domain.dto.zip.StockerOutput;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class StockerOutputPrimaryBody {
    @JsonProperty("MESSAGE") private Message message;

    @Data
    public static class Message {
        @JsonProperty("BARCODE") private String barcode; // 或
        @JsonProperty("CARRIED") private String carried;
    }
}
