package com.czkuo.rdf88701.domain.dto.zip.StatusQuery;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Data
public class StatusQuerySecondaryBody {
    @JsonProperty("StatusInfos") private List<StatusInfo> statusInfos;

    @Data
    public static class StatusInfo {
        @JsonProperty("Type")   private int type;
        @JsonProperty("Name")   private Object name;   // 文件示例 Name 可能是數字或字串，保留 Object
        @JsonProperty("Status") private int status;
        @JsonProperty("Message")private List<String> message;
    }
}
