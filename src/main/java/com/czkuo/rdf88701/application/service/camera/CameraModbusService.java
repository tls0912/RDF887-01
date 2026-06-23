package com.czkuo.rdf88701.application.service.camera;

import com.czkuo.rdf88701.common.dto.camera.CameraReadback;
import com.czkuo.rdf88701.common.dto.camera.TwoCamerasSnapshot;
import com.czkuo.rdf88701.common.enums.camera.CameraErrorCode;
import com.czkuo.rdf88701.common.enums.camera.CameraState;
import com.czkuo.rdf88701.config.modbus.CameraModbusProperties;
import com.github.xingshuangs.iot.protocol.modbus.service.ModbusTcp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class CameraModbusService {

    private final CameraModbusProperties props;
    private final ModbusTcp plc;

    /** 轉方法地址：對應教程「存在 1 位地址偏移」。*/
    private int addr(int ref400xx) {
        int a = ref400xx - props.getReferenceBase();
        if (a < 0) throw new IllegalStateException(
                "address < 0，請檢查 referenceBase 是否應為 40000 或 40001");
        return a;
    }

    // ====== 讀取（使用 readUInt16 / 連續區塊建議看下方備註） ======
    public int rU16(int ref) {
        // 固定 unitId 模式（1 對 1）
        return plc.readUInt16(addr(ref));
        // 若要帶 unitId（多從站），可改： plc.readUInt16(props.getUnitId(), addr(ref), false);
        // 這是教程的任意 unitId 方法；布林參數為簽號/端序選項，16-bit 不涉 32/64 位端序問題。:contentReference[oaicite:4]{index=4}
    }

    public TwoCamerasSnapshot readSnapshot() {
        var r1 = props.getRegisters().getCam1();
        var r2 = props.getRegisters().getCam2();

        CameraReadback c1 = new CameraReadback(
                CameraState.from(rU16(r1.getState())),
                CameraErrorCode.from(rU16(r1.getError())),
                rU16(r1.getFirstCount()),
                rU16(r1.getSecondCount()),
                rU16(r1.getTotal()),
                rU16(r1.getTimes())
        );
        CameraReadback c2 = new CameraReadback(
                CameraState.from(rU16(r2.getState())),
                CameraErrorCode.from(rU16(r2.getError())),
                rU16(r2.getFirstCount()),
                rU16(r2.getSecondCount()),
                rU16(r2.getTotal()),
                rU16(r2.getTimes())
        );
        return new TwoCamerasSnapshot(c1, c2, Instant.now().toEpochMilli());
    }

    // ====== 寫入 / 脈衝 ======
    public void writeU16(int ref, int value) {
        plc.writeUInt16(addr(ref), value);
    }

    public void pulse(int ref, int pulseMs) {
        plc.writeUInt16(addr(ref), 1);
        try { Thread.sleep(Math.max(40, pulseMs)); } catch (InterruptedException ignored) {}
        plc.writeUInt16(addr(ref), 0);
    }
    public void pulse(int ref) { pulse(ref, props.getTriggerPulseMs()); }

    // 便捷方法
    public void triggerCam1First()  { pulse(props.getRegisters().getCmd().getC1First()); }
    public void triggerCam1Second() { pulse(props.getRegisters().getCmd().getC1Second()); }
    public void triggerCam2First()  { pulse(props.getRegisters().getCmd().getC2First()); }
    public void triggerCam2Second() { pulse(props.getRegisters().getCmd().getC2Second()); }
    public void resetAll()          { pulse(props.getRegisters().getCmd().getResetAll()); }
}
