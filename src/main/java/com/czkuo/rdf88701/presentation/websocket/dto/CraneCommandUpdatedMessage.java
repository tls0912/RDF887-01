package com.czkuo.rdf88701.presentation.websocket.dto;

import com.czkuo.rdf88701.domain.plc.valueobject.FromCraneCommandType;
import com.czkuo.rdf88701.domain.plc.valueobject.ToCraneCommandType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Crane 指令狀態更新推播訊息
 * <p>
 * 用於 WebSocket 推送單筆 PLC 指令狀態更新（PC -> PLC）
 */
@Data
@Builder
public class CraneCommandUpdatedMessage {

    private Instant timestamp;                      // 快照時間
    private int craneId;                            // 天車 ID

    private boolean transferReady;                  // 是否準備完成（Transfer Ready）
    private boolean fromTransferCmdReq;             // 是否有 From Transfer CMD 請求
    private boolean fromTransferCompAck;            // 是否 From Transfer 完成確認
    private boolean toTransferCmdReq;               // 是否有 To Transfer CMD 請求
    private boolean toTransferCompAck;              // 是否 To Transfer 完成確認
    private boolean homeReturnRequest;              // 是否有原點回歸請求
    private boolean removeAccountAck;               // Remove Account 確認

    private String fromCstId;                       // From 端 CST ID
    private int fromTransferNo;                     // From 任務編號
    private int fromLocationType;                   // From 位置類型
    private int fromLocationBank;                   // From Bank
    private int fromLocationBay;                    // From Bay
    private int fromLocationLv;                     // From Level
    private FromCraneCommandType fromCraneCommandType;      // From 任務類型

    private String toCstId;                         // To 端 CST ID
    private int toTransferNo;                       // To 任務編號
    private int toLocationType;                     // To 位置類型
    private int toLocationBank;                     // To Bank
    private int toLocationBay;                      // To Bay
    private int toLocationLv;                       // To Level
    private ToCraneCommandType toTransferType;          // To 任務類型

    private boolean stale;                          // 是否過期（超過指定秒數未更新）
}
