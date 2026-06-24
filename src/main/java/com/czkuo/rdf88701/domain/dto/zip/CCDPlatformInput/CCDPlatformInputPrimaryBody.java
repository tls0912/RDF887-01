package com.czkuo.rdf88701.domain.dto.zip.CCDPlatformInput;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Data
public class CCDPlatformInputPrimaryBody {
    @JsonProperty("MESSAGE") private CCDPlatformInputPrimaryBody.Message message;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Message {
        @JsonProperty("Name")         private String name;
        @JsonProperty("CARRIED")      private String carried;
        @JsonProperty("Status")       private int status;
    }
}
