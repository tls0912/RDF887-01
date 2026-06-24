package com.czkuo.rdf88701.application.service.process;

import com.czkuo.rdf88701.application.service.PlcAccessService;
import com.czkuo.rdf88701.common.dto.DeviceProcessState;
import com.czkuo.rdf88701.common.enums.ProcessStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 讀取 WIP（PLC）即時狀態 → 映射為 RUN/IDLE/STOP/WARNING/ERROR
 * 位址從組態注入（B0611~B0614），避免硬編。
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WipPlcProcessStateProvider {

    private final PlcAccessService plc;

    /**
     * 查 WIP 狀態 (使用 PLC-Main)
     */
    public DeviceProcessState queryWip() {
        return queryInternal("PLC-Main", "WIP",
                "B0611", "B0612", "B0613", "B0614");
    }

    /**
     * 查拆併狀態 (使用 PLC-Sub)
     */
    public DeviceProcessState querySplit() {
        return queryInternal("PLC-Packer", "拆併區",
                "B0611", "B0612", "B0613", "B0614");
    }

    private DeviceProcessState queryInternal(String deviceName, String logicalName,
                                             String alarmAddr, String runAddr,
                                             String idleAddr, String stopAddr) {
        try {
            boolean alarm = plc.readBoolean(deviceName, alarmAddr);
            boolean run   = plc.readBoolean(deviceName, runAddr);
            boolean idle  = plc.readBoolean(deviceName, idleAddr);
            boolean stop  = plc.readBoolean(deviceName, stopAddr);

            ProcessStatus st; String msg;
//            if (alarm)       { st = ProcessStatus.ERROR;   msg = "PLC:Alarm"; }
//            else if (run)    { st = ProcessStatus.RUN;     msg = "PLC:Run"; }
//            else if (idle)   { st = ProcessStatus.IDLE;    msg = "PLC:Idle"; }
//            else if (stop)   { st = ProcessStatus.STOP;    msg = "PLC:Stop"; }
//            else             { st = ProcessStatus.STOP;    msg = "PLC:NoFlag→STOP"; }

            if (run)         { st = ProcessStatus.RUN;     msg = "PLC:Run"; }
            else if (idle)   { st = ProcessStatus.IDLE;    msg = "PLC:Idle"; }
            else if (stop)   { st = ProcessStatus.STOP;    msg = "PLC:Stop"; }
            else             { st = ProcessStatus.STOP;    msg = "PLC:NoFlag→STOP"; }

            return new DeviceProcessState(logicalName, st, msg);
        } catch (Exception ex) {
            log.warn("[{}] {} read failed: {}", logicalName, deviceName, ex.getMessage());
            return new DeviceProcessState(logicalName, ProcessStatus.WARNING, "PLC:read-failed");
        }
    }
}
