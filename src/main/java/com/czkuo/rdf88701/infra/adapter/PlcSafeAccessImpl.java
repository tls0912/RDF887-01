package com.czkuo.rdf88701.infra.adapter;

import com.czkuo.rdf88701.application.interfaces.PlcSafeAccess;
import com.czkuo.rdf88701.common.exception.plc.*;
import com.czkuo.rdf88701.config.plc.PlcDeviceRegistry;
import com.czkuo.rdf88701.config.plc.PlcProperties;
import com.czkuo.rdf88701.infra.adapter.plc.connection.PlcClientManager;
import com.czkuo.rdf88701.infra.adapter.plc.protocol.PlcProtocolAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlcSafeAccessImpl implements PlcSafeAccess {

    private final PlcClientManager clientManager;
    private final PlcDeviceRegistry deviceRegistry;

    private String formatError(String action, String deviceName, String address, Exception e) {
        PlcProperties.Device device = deviceRegistry.getDevice(deviceName);
        return String.format("%s failed [device=%s, ip=%s, protocol=%s, address=%s] - %s: %s",
                action,
                device.getName(),
                device.getIp(),
                device.getProtocol(),
                address,
                e.getClass().getSimpleName(),
                e.getMessage());
    }

    private <T> T execute(String deviceName, String address, String action, Function<PlcProtocolAdapter, T> fn) {
        try {
            return clientManager.executeIfAllowed(deviceName, fn);
        } catch (Exception e) {
            throw new PlcCommunicationException(formatError(action, deviceName, address, e), e);
        }
    }

    private void executeVoid(String deviceName, String address, String action, Consumer<PlcProtocolAdapter> fn) {
        try {
            clientManager.executeIfAllowed(deviceName, adapter -> {
                fn.accept(adapter);
                return null;
            });
        } catch (Exception e) {
            throw new PlcCommunicationException(formatError(action, deviceName, address, e), e);
        }
    }

    @Override
    public boolean readBoolean(String deviceName, String address) {
        return execute(deviceName, address, "Read boolean", a -> a.readBoolean(address));
    }

    @Override
    public List<Boolean> readBooleanList(String deviceName, String address, int length) {
        throw new UnsupportedOperationException("Boolean list not implemented for PlcProtocolAdapter");
    }

    @Override
    public void writeBoolean(String deviceName, String address, boolean value) {
        executeVoid(deviceName, address, "Write boolean", a -> a.writeBoolean(address, value));
    }

    @Override
    public byte readByte(String deviceName, String address) {
        return execute(deviceName, address, "Read byte", a -> a.readByte(address));
    }

    @Override
    public byte[] readBytes(String deviceName, String address, int length) {
        return execute(deviceName, address, "Read bytes", a -> a.readBytes(address, length));
    }

    @Override
    public void writeByte(String deviceName, String address, byte value) {
        executeVoid(deviceName, address, "Write byte", a -> a.writeByte(address, value));
    }

    @Override
    public void writeBytes(String deviceName, String address, byte[] values) {
        executeVoid(deviceName, address, "Write bytes", a -> a.writeBytes(address, values));
    }

    @Override
    public short readInt16(String deviceName, String address) {
        return execute(deviceName, address, "Read int16", a -> a.readInt16(address));
    }

    @Override
    public void writeInt16(String deviceName, String address, short value) {
        executeVoid(deviceName, address, "Write int16", a -> a.writeInt16(address, value));
    }

    @Override
    public int readUInt16(String deviceName, String address) {
        return execute(deviceName, address, "Read uint16", a -> a.readUInt16(address));
    }

    @Override
    public void writeUInt16(String deviceName, String address, int value) {
        executeVoid(deviceName, address, "Write uint16", a -> a.writeUInt16(address, value));
    }

    @Override
    public List<Integer> readUInt16List(String deviceName, String... addresses) {
        throw new UnsupportedOperationException("UInt16 list not implemented for PlcProtocolAdapter");
    }

    @Override
    public int readInt32(String deviceName, String address) {
        return execute(deviceName, address, "Read int32", a -> a.readInt32(address));
    }

    @Override
    public void writeInt32(String deviceName, String address, int value) {
        executeVoid(deviceName, address, "Write int32", a -> a.writeInt32(address, value));
    }

    @Override
    public List<Integer> readInt32List(String deviceName, String... addresses) {
        throw new UnsupportedOperationException("Int32 list not implemented for PlcProtocolAdapter");
    }

    @Override
    public long readUInt32(String deviceName, String address) {
        return execute(deviceName, address, "Read uint32", a -> a.readUInt32(address));
    }

    @Override
    public void writeUInt32(String deviceName, String address, long value) {
        executeVoid(deviceName, address, "Write uint32", a -> a.writeUInt32(address, value));
    }

    @Override
    public List<Long> readUInt32List(String deviceName, String... addresses) {
        throw new UnsupportedOperationException("UInt32 list not implemented for PlcProtocolAdapter");
    }

    @Override
    public float readFloat32(String deviceName, String address) {
        return execute(deviceName, address, "Read float32", a -> a.readFloat32(address));
    }

    @Override
    public void writeFloat32(String deviceName, String address, float value) {
        executeVoid(deviceName, address, "Write float32", a -> a.writeFloat32(address, value));
    }

    @Override
    public List<Float> readFloat32List(String deviceName, String... addresses) {
        throw new UnsupportedOperationException("Float32 list not implemented for PlcProtocolAdapter");
    }

    @Override
    public double readFloat64(String deviceName, String address) {
        return execute(deviceName, address, "Read float64", a -> a.readFloat64(address));
    }

    @Override
    public void writeFloat64(String deviceName, String address, double value) {
        executeVoid(deviceName, address, "Write float64", a -> a.writeFloat64(address, value));
    }

    @Override
    public String readString(String deviceName, String address, int length) {
        return execute(deviceName, address, "Read string", a -> a.readString(address, length));
    }

    @Override
    public void writeString(String deviceName, String address, String value) {
        executeVoid(deviceName, address, "Write string", a -> a.writeString(address, value));
    }

    @Override
    public Object readMultiAddress(String deviceName) {
        throw new UnsupportedOperationException("Multi address read not implemented");
    }

    @Override
    public void writeMultiAddress(String deviceName) {
        throw new UnsupportedOperationException("Multi address write not implemented");
    }
}
