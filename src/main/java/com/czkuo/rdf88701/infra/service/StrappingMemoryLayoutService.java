package com.czkuo.rdf88701.infra.service;

import com.czkuo.rdf88701.common.util.PlcDataCodec;
import com.czkuo.rdf88701.config.plc.DeviceArea;
import com.czkuo.rdf88701.config.plc.PlcStrappingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Strapping 記憶體布局解析服務
 * - 支援從整體 PLC 資料區段中解析出單一 Strapping 的位元區或字區資料
 */
@Service
@RequiredArgsConstructor
public class StrappingMemoryLayoutService {

    private final PlcStrappingProperties strappingProperties;

    private Map<Integer, PlcStrappingProperties.Strapping> strappingMap;

    /**
     * 初始化快取，將 id -> Strapping 映射表建構完成
     */
    public void init() {
        this.strappingMap = new HashMap<>();
        for (PlcStrappingProperties.Strapping s : strappingProperties.getStrappings()) {
            strappingMap.put(s.getId(), s);
        }
    }

    private void ensureInitialized() {
        if (strappingMap == null) {
            synchronized (this) {
                if (strappingMap == null) {
                    init();
                }
            }
        }
    }

    /**
     * 從大區段中抽出指定 Strapping、記憶體類型（B/W）與區段類型（read/write）的資料
     */
    public byte[] extractAreaBytes(int strappingId, String areaType, String memoryType, byte[] fullData, int fullStart) {
        ensureInitialized();

        PlcStrappingProperties.Strapping s = strappingMap.get(strappingId);
        if (s == null) {
            throw new IllegalArgumentException("Strapping ID not found: " + strappingId);
        }

        List<DeviceArea> areas =
                "read".equalsIgnoreCase(areaType) ? s.getReadAreas() : s.getWriteAreas();

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
                            String.format("Area out of full data range, strappingId=%d, offset=%d, lengthBytes=%d, fullDataLength=%d",
                                    strappingId, offset, lengthBytes, fullData.length)
                    );
                }

                byte[] result = new byte[lengthBytes];
                System.arraycopy(fullData, offset, result, 0, lengthBytes);
                return result;
            }
        }

        throw new IllegalArgumentException("Memory type not found in areas: " + memoryType);
    }

    /**
     * 從指定 bit 區段中切出 bit 陣列
     */
    public boolean[] extractBits(int strappingId, String areaType, byte[] fullData, int fullStart, int bitCount) {
        byte[] areaData = extractAreaBytes(strappingId, areaType, "B", fullData, fullStart);
        return PlcDataCodec.bytesToBits(areaData, bitCount);
    }

    /**
     * 從指定 word 區段中切出 word 陣列
     */
    public int[] extractWords(int strappingId, String areaType, byte[] fullData, int fullStart) {
        byte[] areaData = extractAreaBytes(strappingId, areaType, "W", fullData, fullStart);
        return PlcDataCodec.bytesToWords(areaData);
    }

    /**
     * 從指定 word 區段中轉為文字（通常為產品 ID 類欄位）
     */
    public String extractString(int strappingId, String areaType, byte[] fullData, int fullStart) {
        byte[] areaData = extractAreaBytes(strappingId, areaType, "W", fullData, fullStart);
        return PlcDataCodec.decodeString(areaData, ByteOrder.LITTLE_ENDIAN);
    }
}
