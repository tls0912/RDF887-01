package com.czkuo.rdf88701.infra.adapter.plc.dto;

import lombok.Data;

/**
 * Working Beam PLC Bit Status
 * - PLC 回傳 PC 狀態專用封裝
 */
@Data
public class PlcWorkingBeamBitStatus {

    /** Working Beam Standby 狀態（B0748） */
    private boolean workingBeamStandby;

    /** Transfer CMD Ack（PLC 接收到 PC 指令）（B074D） */
    private boolean transferCmdAck;

    /** Transfer Completion Request（PLC 任務完成）（B074E） */
    private boolean transferCompReq;

    /** Alarm 狀態位元（B074F） */
    private boolean alarm;
}
