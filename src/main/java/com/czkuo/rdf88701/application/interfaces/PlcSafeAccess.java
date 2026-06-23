package com.czkuo.rdf88701.application.interfaces;

import com.czkuo.rdf88701.infra.adapter.plc.connection.PlcDeviceStatus;

import java.util.List;

public interface PlcSafeAccess {

    // Boolean
    boolean readBoolean(String deviceName, String address);
    void writeBoolean(String deviceName, String address, boolean value);
    List<Boolean> readBooleanList(String deviceName, String address, int length);

    // Byte
    byte readByte(String deviceName, String address);
    byte[] readBytes(String deviceName, String address, int length);
    void writeByte(String deviceName, String address, byte value);
    void writeBytes(String deviceName, String address, byte[] values);

    // Int16
    short readInt16(String deviceName, String address);
    void writeInt16(String deviceName, String address, short value);

    // UInt16
    int readUInt16(String deviceName, String address);
    void writeUInt16(String deviceName, String address, int value);
    List<Integer> readUInt16List(String deviceName, String... addresses);

    // Int32
    int readInt32(String deviceName, String address);
    void writeInt32(String deviceName, String address, int value);
    List<Integer> readInt32List(String deviceName, String... addresses);

    // UInt32
    long readUInt32(String deviceName, String address);
    void writeUInt32(String deviceName, String address, long value);
    List<Long> readUInt32List(String deviceName, String... addresses);

    // Float32
    float readFloat32(String deviceName, String address);
    void writeFloat32(String deviceName, String address, float value);
    List<Float> readFloat32List(String deviceName, String... addresses);

    // Float64
    double readFloat64(String deviceName, String address);
    void writeFloat64(String deviceName, String address, double value);

    // String
    String readString(String deviceName, String address, int length);
    void writeString(String deviceName, String address, String value);

    // Multi Address（進階）
    Object readMultiAddress(String deviceName); // 可定義回傳結構體
    void writeMultiAddress(String deviceName); // 可定義寫入邏輯
}
