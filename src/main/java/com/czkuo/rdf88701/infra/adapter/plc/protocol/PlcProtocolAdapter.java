package com.czkuo.rdf88701.infra.adapter.plc.protocol;

import java.nio.ByteOrder;
import java.util.Map;

/**
 * PLC 協議抽象介面。
 *
 * <p>定義上層讀寫 PLC 所需的統一方法，包含 boolean、byte、整數、浮點數與字串。
 * 不同協議實作需包裝各自底層 client，讓 application/monitor 不直接依賴廠牌協議。</p>
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public interface PlcProtocolAdapter {

    /**
     * 回傳底層連線物件（例如 McPLC、OpcUaClient 等）
     * @return 協議對應的底層 client 實例
     */
    Object getClient();

    // ----------------------- Boolean -----------------------

    /**
     * 讀取單點布林值（例如 M100）
     */
    boolean readBoolean(String address);

    /**
     * 寫入單點布林值
     */
    void writeBoolean(String address, boolean value);

    // ------------------------ Byte -------------------------

    /**
     * 讀取單個位元組（Byte）
     */
    byte readByte(String address);

    /**
     * 寫入單個位元組（Byte）
     */
    void writeByte(String address, byte value);

    /**
     * 讀取多個位元組
     */
    byte[] readBytes(String address, int length);

    /**
     * 寫入多個位元組
     */
    void writeBytes(String address, byte[] values);

    // ---------------------- Int16 / UInt16 ----------------------

    /**
     * 讀取單點 Int16（有號）
     */
    short readInt16(String address);

    /**
     * 寫入單點 Int16
     */
    void writeInt16(String address, short value);

    /**
     * 讀取單點 UInt16（無號）
     */
    int readUInt16(String address);

    /**
     * 寫入單點 UInt16
     */
    void writeUInt16(String address, int value);

    /**
     * 讀取多點 UInt16，並對應地址 → 數值
     */
    Map<String, Integer> readUInt16(String... addresses);

    // ---------------------- Int32 / UInt32 ----------------------

    /**
     * 讀取單點 Int32（有號）
     */
    int readInt32(String address);

    /**
     * 寫入單點 Int32
     */
    void writeInt32(String address, int value);

    /**
     * 讀取多點 Int32
     */
    Map<String, Integer> readInt32(String... addresses);

    /**
     * 讀取單點 UInt32（無號）
     */
    long readUInt32(String address);

    /**
     * 寫入單點 UInt32
     */
    void writeUInt32(String address, long value);

    /**
     * 讀取多點 UInt32
     */
    Map<String, Long> readUInt32(String... addresses);

    // ----------------------- Float -------------------------

    /**
     * 讀取單點 Float32
     */
    float readFloat32(String address);

    /**
     * 寫入單點 Float32
     */
    void writeFloat32(String address, float value);

    /**
     * 讀取多點 Float32
     */
    Map<String, Float> readFloat32(String... addresses);

    /**
     * 讀取單點 Float64
     */
    double readFloat64(String address);

    /**
     * 寫入單點 Float64
     */
    void writeFloat64(String address, double value);

    // ----------------------- String -------------------------

    /**
     * 讀取字串（需指定長度）
     */
    String readString(String address, int length);

    /**
     * 寫入字串
     */
    void writeString(String address, String value);

    /**
     * 讀取字串（指定端序）
     */
    String readString(String address, int length, ByteOrder order);

    /**
     * 寫入字串（指定端序）
     */
    void writeString(String address, String value, ByteOrder order);
}
