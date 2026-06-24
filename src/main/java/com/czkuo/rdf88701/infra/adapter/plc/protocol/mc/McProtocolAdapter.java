package com.czkuo.rdf88701.infra.adapter.plc.protocol.mc;

import com.czkuo.rdf88701.common.exception.plc.PlcConnectionException;
import com.czkuo.rdf88701.common.util.EndianStringHelper;
import com.czkuo.rdf88701.infra.adapter.plc.protocol.support.ConnectablePlcProtocolAdapter;
import com.github.xingshuangs.iot.common.buff.ByteWriteBuff;
import com.github.xingshuangs.iot.protocol.melsec.service.McPLC;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mitsubishi MC protocol adapter。
 *
 * <p>包裝 xingshuangs `McPLC`，實作專案統一的 PlcProtocolAdapter 讀寫介面，
 * 並提供 connect、disconnect、isConnected 供 PlcClientManager 控制連線。</p>
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
public class McProtocolAdapter implements ConnectablePlcProtocolAdapter {

    private final McPLC client;

    public McProtocolAdapter(McPLC client) {
        this.client = client;
    }

    /**
     * 建立連線
     */
    @Override
    public boolean connect() {
        try {
            client.connect();
            return client.checkConnected();
        } catch (Exception e) {
            log.error("[PLC][MC] connect failed", e);
            throw new PlcConnectionException("MC 協議連線失敗", e);
        }
    }

    /**
     * 中斷連線
     */
    @Override
    public void disconnect() {
        try {
            client.close();
        } catch (Exception e) {
            log.warn("[PLC][MC] disconnect error", e);
        }
    }

    /**
     * 查詢是否已連線
     */
    @Override
    public boolean isConnected() {
        return client.checkConnected();
    }

    @Override
    public McPLC getClient() {
        return this.client;
    }

    // ---------------- Boolean ----------------

    @Override
    public boolean readBoolean(String address) {
        return client.readBoolean(address);
    }

    @Override
    public void writeBoolean(String address, boolean value) {
        client.writeBoolean(address, value);
    }

    // ---------------- Byte ----------------

    @Override
    public byte readByte(String address) {
        return client.readByte(address);
    }

    @Override
    public void writeByte(String address, byte value) {
        client.writeByte(address, value);
    }

    @Override
    public byte[] readBytes(String address, int length) {
        return client.readBytes(address, length);
    }

    @Override
    public void writeBytes(String address, byte[] values) {
        client.writeBytes(address, values);
    }

    // ---------------- Int16 / UInt16 ----------------

    @Override
    public short readInt16(String address) {
        return client.readInt16(address);
    }

    @Override
    public void writeInt16(String address, short value) {
        client.writeInt16(address, value);
    }

    @Override
    public int readUInt16(String address) {
        return client.readUInt16(address);
    }

    @Override
    public void writeUInt16(String address, int value) {
        client.writeUInt16(address, value);
    }

    @Override
    public Map<String, Integer> readUInt16(String... addresses) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String addr : addresses) {
            result.put(addr, client.readUInt16(addr));
        }
        return result;
    }

    // ---------------- Int32 / UInt32 ----------------

    @Override
    public int readInt32(String address) {
        return client.readInt32(address);
    }

    @Override
    public void writeInt32(String address, int value) {
        client.writeInt32(address, value);
    }

    @Override
    public Map<String, Integer> readInt32(String... addresses) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String addr : addresses) {
            result.put(addr, client.readInt32(addr));
        }
        return result;
    }

    @Override
    public long readUInt32(String address) {
        return client.readUInt32(address);
    }

    @Override
    public void writeUInt32(String address, long value) {
        client.writeUInt32(address, value);
    }

    @Override
    public Map<String, Long> readUInt32(String... addresses) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (String addr : addresses) {
            result.put(addr, client.readUInt32(addr));
        }
        return result;
    }

    // ---------------- Float ----------------

    @Override
    public float readFloat32(String address) {
        return client.readFloat32(address);
    }

    @Override
    public void writeFloat32(String address, float value) {
        client.writeFloat32(address, value);
    }

    @Override
    public Map<String, Float> readFloat32(String... addresses) {
        Map<String, Float> result = new LinkedHashMap<>();
        for (String addr : addresses) {
            result.put(addr, client.readFloat32(addr));
        }
        return result;
    }

    @Override
    public double readFloat64(String address) {
        return client.readFloat64(address);
    }

    @Override
    public void writeFloat64(String address, double value) {
        client.writeFloat64(address, value);
    }

    // ---------------- String ----------------

    @Override
    public String readString(String address, int length) {
        return client.readString(address, length);
    }

    @Override
    public void writeString(String address, String value) {
        //byte[] bytes = ByteWriteBuff.newInstance(50, true).putString(value, StandardCharsets.UTF_16).getData();
        byte[] bytes = EndianStringHelper.encodeString(value, value.length(), ByteOrder.LITTLE_ENDIAN);
        client.writeBytes(address, bytes);
        //client.writeString(address, value);
    }

    @Override
    public String readString(String address, int length, ByteOrder order) {
        if (order == ByteOrder.LITTLE_ENDIAN) {
            return client.readString(address, length);
        } else {
            byte[] raw = client.readBytes(address, length * 2);
            return EndianStringHelper.decodeString(raw, order);
        }
    }

    @Override
    public void writeString(String address, String value, ByteOrder order) {
        if (order == ByteOrder.LITTLE_ENDIAN) {
            //byte[] bytes = ByteWriteBuff.newInstance(value.length(), true).putString(value, StandardCharsets.UTF_8).getData();
            byte[] bytes = EndianStringHelper.encodeString(value, value.length(), ByteOrder.LITTLE_ENDIAN);
            client.writeBytes(address, bytes);
            //client.writeString(address, value);
        } else {
            byte[] encoded = EndianStringHelper.encodeString(value, value.length(), order);
            client.writeBytes(address, encoded);
        }
    }
}
