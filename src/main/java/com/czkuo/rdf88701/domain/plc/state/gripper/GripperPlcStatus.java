package com.czkuo.rdf88701.domain.plc.state.gripper;

import com.czkuo.rdf88701.domain.plc.state.common.RunningSubStatus;

/**
 * 外部需要提供的 PLC 即時狀態快照（例如 Polling解析後）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public interface GripperPlcStatus {

    /** 是否 Ready (Ready to receive command) */
    boolean isReady();

    /** 是否收到 Command Ack (PLC已經接收命令) */
    boolean isCmdAck();

    /** 是否 Completion Request (Gripper 要求 Completion Ack) */
    boolean isCompReq();

    /** 是否 Alarm 異常 (警報) */
    boolean isAlarm();

    /** 當前執行中的子狀態（例如移動中、抓取中、放置中） */
    RunningSubStatus getRunningSubStatus();

    /** 對應原始解析出來的 GripperDeviceStatus (可供外部查詢快照) */
    GripperDeviceStatus getRawDeviceStatus();
}
