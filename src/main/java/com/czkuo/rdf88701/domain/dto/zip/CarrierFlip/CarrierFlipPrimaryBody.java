package com.czkuo.rdf88701.domain.dto.zip.CarrierFlip;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Data
public class CarrierFlipPrimaryBody {
    @JsonProperty("MESSAGE") private CarrierFlipPrimaryBody.Message message;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Message {
        @JsonProperty("Name")         private String name;
        @JsonProperty("CARRIED")      private String carried;
    }
}
