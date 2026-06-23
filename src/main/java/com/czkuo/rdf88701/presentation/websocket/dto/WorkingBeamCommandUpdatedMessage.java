package com.czkuo.rdf88701.presentation.websocket.dto;

import com.czkuo.rdf88701.domain.plc.valueobject.WorkingBeamCommandMeta;
import com.czkuo.rdf88701.domain.plc.valueobject.WorkingBeamCommandType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * WorkingBeam 指令狀態更新推播訊息
 * <p>
 * 用於 WebSocket 推送單筆 Working Beam 指令狀態（PC → PLC）
 * 對應 PLC Word W0220 ~ W0225 寫入區資料。
 */
@Data
@Builder
public class WorkingBeamCommandUpdatedMessage {

    private Instant timestamp;                        // 快照時間
    private int workingBeamId;                        // Working Beam 裝置 ID

    private boolean transferReady;                    // 表示 PC 已準備好
    private boolean transferCmdReq;                   // 是否觸發 Transfer Cmd 請求
    private boolean transferCompAck;                  // 是否完成回應確認（Ack）

    private int transferNo;                           // 指令編號（W0220）
    private WorkingBeamCommandType commandType;       // 指令類型（W0221）
    private WorkingBeamCommandMeta commandMeta;       // 動作資訊（W0222）

    private boolean stale;                            // 是否過期（未更新超過閾值）
}
