package com.czkuo.rdf88701.presentation.websocket.dto;


import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 紅外線設備指令狀態批次更新推播訊息
 * - 用於批次推播紅外線設備的指令狀態更新
 */
@Data
@Builder
public class InfraredCommandBatchMessage {

    /** 紅外線設備指令更新訊息列表 */
    private List<InfraredCommandUpdatedMessage> commands;
}
