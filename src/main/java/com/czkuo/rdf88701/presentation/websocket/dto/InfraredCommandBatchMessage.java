package com.czkuo.rdf88701.presentation.websocket.dto;


import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 紅外線設備指令狀態批次更新推播訊息
 * - 用於批次推播紅外線設備的指令狀態更新
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
@Builder
public class InfraredCommandBatchMessage {

    /** 紅外線設備指令更新訊息列表 */
    private List<InfraredCommandUpdatedMessage> commands;
}
