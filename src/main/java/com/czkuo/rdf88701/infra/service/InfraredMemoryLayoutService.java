package com.czkuo.rdf88701.infra.service;

import com.czkuo.rdf88701.common.util.PlcDataCodec;
import com.czkuo.rdf88701.config.plc.DeviceArea;
import com.czkuo.rdf88701.config.plc.PlcInfraredProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Infrared PLC 記憶體布局解析服務。
 *
 * <p>依 PlcInfraredProperties 的設備區段設定，從 PLC 大區塊 byte array 中切出單一
 * Infrared 的 read/write、B/W 資料區，並提供 bits、words、string 解碼入口。</p>
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Service
@RequiredArgsConstructor
public class InfraredMemoryLayoutService {

    private final PlcInfraredProperties infraredProperties;

    private Map<Integer, PlcInfraredProperties.Infrared> infraredMap;

    /**
     * 初始化 ID → 紅外線設備 映射表
     */
    public void init() {
        this.infraredMap = new HashMap<>();
        for (PlcInfraredProperties.Infrared unit : infraredProperties.getInfrareds()) {
            infraredMap.put(unit.getId(), unit);
        }
    }

    /**
     * 從完整 PLC 區段中擷取單一紅外線裝置的區塊資料
     *
     * @param infraredId   紅外線裝置 ID
     * @param areaType     區段類型（read / write）
     * @param memoryType   記憶體型別（B / W）
     * @param fullData     原始 PLC 資料
     * @param fullStart    原始資料的起始位址
     * @return             該設備區段對應的 byte[]
     */
    public byte[] extractAreaBytes(int infraredId, String areaType, String memoryType, byte[] fullData, int fullStart) {
        if (infraredMap == null) {
            init();
        }

        PlcInfraredProperties.Infrared unit = infraredMap.get(infraredId);
        if (unit == null) {
            throw new IllegalArgumentException("Infrared ID not found: " + infraredId);
        }

        List<DeviceArea> areas =
                "read".equalsIgnoreCase(areaType) ? unit.getReadAreas() : unit.getWriteAreas();

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
                            String.format("Area out of full data range, infraredId=%d, offset=%d, lengthBytes=%d, fullDataLength=%d",
                                    infraredId, offset, lengthBytes, fullData.length)
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
     * 從 Bit 區段中轉出 boolean[]（例如紅外線狀態旗標）
     */
    public boolean[] extractBits(int infraredId, String areaType, byte[] fullData, int fullStart, int bitCount) {
        byte[] areaData = extractAreaBytes(infraredId, areaType, "B", fullData, fullStart);
        return PlcDataCodec.bytesToBits(areaData, bitCount);
    }

    /**
     * 從 Word 區段中轉出 int[]
     */
    public int[] extractWords(int infraredId, String areaType, byte[] fullData, int fullStart) {
        byte[] areaData = extractAreaBytes(infraredId, areaType, "W", fullData, fullStart);
        return PlcDataCodec.bytesToWords(areaData);
    }

    /**
     * 從 Word 區段中轉為字串（若未來有 ProductId 等資訊）
     */
    public String extractString(int infraredId, String areaType, byte[] fullData, int fullStart) {
        byte[] areaData = extractAreaBytes(infraredId, areaType, "W", fullData, fullStart);
        return PlcDataCodec.decodeString(areaData, ByteOrder.LITTLE_ENDIAN);
    }
}
