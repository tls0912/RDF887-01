package com.czkuo.rdf88701.infra.adapter.plc.dto;

import lombok.Data;

/**
 * Transfer PLC Bit Status
 * - PLC 回傳 PC 狀態專用封裝
 */
@Data
public class PlcTransferBitStatus {

    /** Transfer Standby 狀態（B0708） */
    private boolean transferStandby;

    /** Transfer CMD Ack（PLC 接收到 PC 指令）（B070D） */
    private boolean transferCmdAck;

    /** Transfer Completion Request（PLC 任務完成）（B070E） */
    private boolean transferCompReq;

    /** Alarm 狀態位元（B070F） */
    private boolean alarm;
}
