package com.czkuo.rdf88701.domain.dto.zip.PortLockUnlock;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PortLockUnlockSecondaryBody {
    @JsonProperty("ResultInfos") private java.util.List<ResultInfo> resultInfos;

    @Data
    public static class ResultInfo {
        @JsonProperty("Result")        private int result;        // 0,51,52,>100
        @JsonProperty("ResultMessage") private String resultMessage; // 成功且上面有物要填ID；錯誤填原因
    }
}
