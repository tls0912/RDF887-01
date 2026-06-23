package com.czkuo.rdf88701.domain.dto.zip.PortLockUnlock;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class PortLockUnlockPrimaryBody {
    @JsonProperty("CmdInfos") private List<CmdInfo> cmdInfos;

    @Data
    public static class CmdInfo {
        @JsonProperty("Name") private String name; // STK01...
        @JsonProperty("Cmd")  private int cmd;     // 1=Lock,2=Unlock
    }
}
