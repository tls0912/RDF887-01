package com.czkuo.rdf88701.infra.adapter.plc.writer;

import com.czkuo.rdf88701.application.service.PlcAccessService;
import com.czkuo.rdf88701.common.util.PlcAddressUtils;
import com.czkuo.rdf88701.config.plc.PlcGripperRegistry;
import com.czkuo.rdf88701.domain.plc.state.gripper.GripperBitSignal;
import com.czkuo.rdf88701.domain.plc.state.site.SiteBitSignal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * PlcGripperBitWriter
 * - 專責寫入 Gripper 指定 Bit（交握用）
 * - 支援以 Gripper ID 控制 Bit 寫入
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlcGripperBitWriter {

    private final PlcAccessService plcAccessService;
    private final PlcGripperRegistry plcGripperRegistry;

    /**
     * 寫入指定 Gripper Bit（offset 版）
     *
     * @param gripperId Gripper 裝置 ID
     * @param offset Bit 偏移位置
     * @param value 寫入值（true/false）
     */
    public void writeBit(int gripperId, int offset, boolean value) {
        String gripperName = plcGripperRegistry.getGripperNameById(gripperId);
        String deviceName = plcGripperRegistry.resolvePlcDeviceNameById(gripperId);
        int baseAddress = plcGripperRegistry.getHandshakeBitStartAddress(gripperName);
        int finalAddress = baseAddress + offset;
        String address = "B" + PlcAddressUtils.formatAddressHexWithout0x(finalAddress);

        plcAccessService.writeBoolean(deviceName, address, value);
        //log.debug("[PLC] [{}] Write bit: {} -> {}", gripperName, address, value);
    }

    /**
     * 使用 enum 操作具名 bit（推薦用法）
     */
    public void writeBit(int gripperId, GripperBitSignal signal, boolean value) {
        writeBit(gripperId, signal.getBitIndex(), value);
    }

    // ===========================
    // 語義化常用交握操作（可擴充）
    // ===========================

    public void writeGripperReady(int gripperId, boolean value) {
        writeBit(gripperId, GripperBitSignal.GRIPPER_READY, value);
    }

    public void writeGripperCmdReq(int gripperId, boolean value) {
        writeBit(gripperId, GripperBitSignal.GRIPPER_CMD_REQ, value);
    }
    public void readGripperCmdReq(int gripperId, boolean value) {
        writeBit(gripperId, GripperBitSignal.GRIPPER_CMD_REQ, value);
    }
    public void writeGripperCompAck(int gripperId, boolean value) {
        writeBit(gripperId, GripperBitSignal.GRIPPER_COMP_ACK, value);
    }

    public void writeRemoveAccountAck(int gripperId, boolean value) {
        writeBit(gripperId, GripperBitSignal.REMOVE_ACCOUNT_ACK, value);
    }
}
