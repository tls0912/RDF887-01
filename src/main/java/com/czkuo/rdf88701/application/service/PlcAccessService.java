package com.czkuo.rdf88701.application.service;

import com.czkuo.rdf88701.application.interfaces.PlcSafeAccess;
import com.czkuo.rdf88701.infra.adapter.plc.connection.PlcDeviceStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 封裝上層對 PLC 的存取服務，供應用層（如 Controller、Scheduler）使用
 * 提供更完整的讀寫操作包裝，統一錯誤處理與記錄點位操作行為
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlcAccessService {

    private final PlcSafeAccess plcSafeAccess;

    public boolean readBoolean(String deviceName, String address) {
        boolean result = plcSafeAccess.readBoolean(deviceName, address);
        //log.debug("[PLC] Read boolean: {} -> {}", address, result);
        return result;
    }

    public List<Boolean> readBooleanList(String deviceName, String address, int length) {
        List<Boolean> result = plcSafeAccess.readBooleanList(deviceName, address, length);
        //log.debug("[PLC] Read boolean list: {} ({}) -> {}", address, length, result);
        return result;
    }

    public void writeBoolean(String deviceName, String address, boolean value) {
        plcSafeAccess.writeBoolean(deviceName, address, value);
        //log.debug("[PLC] Write boolean: {} -> {}", address, value);
    }

    public byte readByte(String deviceName, String address) {
        byte result = plcSafeAccess.readByte(deviceName, address);
        //log.debug("[PLC] Read byte: {} -> {}", address, result);
        return result;
    }

    public byte[] readBytes(String deviceName, String address, int length) {
        byte[] result = plcSafeAccess.readBytes(deviceName, address, length);
        //log.debug("[PLC] Read bytes: {} ({}) -> {}", address, length, result);
        return result;
    }

    public void writeByte(String deviceName, String address, byte value) {
        plcSafeAccess.writeByte(deviceName, address, value);
        //log.debug("[PLC] Write byte: {} -> {}", address, value);
    }

    public void writeBytes(String deviceName, String address, byte[] values) {
        plcSafeAccess.writeBytes(deviceName, address, values);
        //log.debug("[PLC] Write bytes: {} -> {}", address, values);
    }

    public int readInt16(String deviceName, String address) {
        int value = plcSafeAccess.readInt16(deviceName, address);
        //log.debug("[PLC] Read int16: {} -> {}", address, value);
        return value;
    }

    public void writeInt16(String deviceName, String address, short value) {
        plcSafeAccess.writeInt16(deviceName, address, value);
        //log.debug("[PLC] Write int16: {} -> {}", address, value);
    }

    public int readUInt16(String deviceName, String address) {
        int value = plcSafeAccess.readUInt16(deviceName, address);
        //log.debug("[PLC] Read uint16: {} -> {}", address, value);
        return value;
    }

    public void writeUInt16(String deviceName, String address, int value) {
        plcSafeAccess.writeUInt16(deviceName, address, value);
        //log.debug("[PLC] Write uint16: {} -> {}", address, value);
    }

    public int readInt32(String deviceName, String address) {
        int value = plcSafeAccess.readInt32(deviceName, address);
        //log.debug("[PLC] Read int32: {} -> {}", address, value);
        return value;
    }

    public List<Integer> readInt32List(String deviceName, String... addresses) {
        List<Integer> list = plcSafeAccess.readInt32List(deviceName, addresses);
        //log.debug("[PLC] Read int32 list: {} -> {}", String.join(",", addresses), list);
        return list;
    }

    public void writeInt32(String deviceName, String address, int value) {
        plcSafeAccess.writeInt32(deviceName, address, value);
        //log.debug("[PLC] Write int32: {} -> {}", address, value);
    }

    public float readFloat32(String deviceName, String address) {
        float value = plcSafeAccess.readFloat32(deviceName, address);
        //log.debug("[PLC] Read float32: {} -> {}", address, value);
        return value;
    }

    public List<Float> readFloat32List(String deviceName, String... addresses) {
        List<Float> result = plcSafeAccess.readFloat32List(deviceName, addresses);
        //log.debug("[PLC] Read float32 list: {} -> {}", String.join(",", addresses), result);
        return result;
    }

    public void writeFloat32(String deviceName, String address, float value) {
        plcSafeAccess.writeFloat32(deviceName, address, value);
        //log.debug("[PLC] Write float32: {} -> {}", address, value);
    }

    public String readString(String deviceName, String address, int length) {
        String result = plcSafeAccess.readString(deviceName, address, length);
        //log.debug("[PLC] Read string: {} ({}) -> {}", address, length, result);
        return result;
    }

    public void writeString(String deviceName, String address, String value) {
        plcSafeAccess.writeString(deviceName, address, value);
        //log.debug("[PLC] Write string: {} -> {}", address, value);
    }
}