package com.czkuo.rdf88701.infra.service;

import com.czkuo.rdf88701.common.util.PlcDataCodec;
import com.czkuo.rdf88701.config.plc.DeviceArea;
import com.czkuo.rdf88701.config.plc.PlcCraneProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Crane PLC 記憶體布局解析服務。
 *
 * <p>依 PlcCraneProperties 的設備區段設定，從 PLC 大區塊 byte array 中切出單一
 * Crane 的 read/write、B/W 資料區，並提供 bits、words、string 解碼入口。</p>
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Service
@RequiredArgsConstructor
public class CraneMemoryLayoutService {

    private final PlcCraneProperties craneProperties;

    private Map<Integer, PlcCraneProperties.Crane> craneMap;

    /**
     * 初始化時將 cranes list 轉為 id -> Crane 快取表
     */
    public void init() {
        this.craneMap = new HashMap<>();
        for (PlcCraneProperties.Crane crane : craneProperties.getCranes()) {
            craneMap.put(crane.getId(), crane);
        }
    }

    /**
     * 切出指定 Crane、指定區段的原始 byte[]
     */
    public byte[] extractAreaBytes(int craneId, String areaType, String memoryType, byte[] fullData, int fullStart) {
        if (craneMap == null) {
            init();
        }

        PlcCraneProperties.Crane crane = craneMap.get(craneId);
        if (crane == null) {
            throw new IllegalArgumentException("Crane ID not found: " + craneId);
        }

        List<DeviceArea> areas =
                "read".equalsIgnoreCase(areaType) ? crane.getReadAreas() : crane.getWriteAreas();

        for (DeviceArea area : areas) {
            if (area.getType().equalsIgnoreCase(memoryType)) {
                int offset;
                int lengthBytes;

                if ("B".equalsIgnoreCase(memoryType)) {
                    int bitOffset = area.getAddress() - fullStart;
                    offset = bitOffset / 8;
                    lengthBytes = (area.getLength() + 7) / 8;
                } else if ("W".equalsIgnoreCase(memoryType)) {
                    offset = (area.getAddress() - fullStart) * 2;
                    lengthBytes = area.getLength() * 2;
                } else {
                    throw new UnsupportedOperationException("Unknown memory type: " + memoryType);
                }

                if (offset < 0 || offset + lengthBytes > fullData.length) {
                    throw new IndexOutOfBoundsException(
                            String.format("Area out of full data range, craneId=%d, offset=%d, lengthBytes=%d, fullDataLength=%d",
                                    craneId, offset, lengthBytes, fullData.length)
                    );
                }

                byte[] result = new byte[lengthBytes];
                System.arraycopy(fullData, offset, result, 0, lengthBytes);
                return result;
            }
        }

        throw new IllegalArgumentException("Memory type not found in areas: " + memoryType);
    }

    public boolean[] extractBits(int craneId, String areaType, byte[] fullData, int fullStart, int bitCount) {
        byte[] areaData = extractAreaBytes(craneId, areaType, "B", fullData, fullStart);
        return PlcDataCodec.bytesToBits(areaData, bitCount);
    }

    public int[] extractWords(int craneId, String areaType, byte[] fullData, int fullStart) {
        byte[] areaData = extractAreaBytes(craneId, areaType, "W", fullData, fullStart);
        return PlcDataCodec.bytesToWords(areaData);
    }

    public String extractString(int craneId, String areaType, byte[] fullData, int fullStart) {
        byte[] areaData = extractAreaBytes(craneId, areaType, "W", fullData, fullStart);
        return PlcDataCodec.decodeString(areaData, ByteOrder.LITTLE_ENDIAN);
    }
}
