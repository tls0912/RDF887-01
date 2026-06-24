package com.czkuo.rdf88701.infra.service;

import com.czkuo.rdf88701.common.util.PlcDataCodec;
import com.czkuo.rdf88701.config.plc.DeviceArea;
import com.czkuo.rdf88701.config.plc.PlcWorkingBeamProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Working Beam PLC 記憶體布局解析服務。
 *
 * <p>依 PlcWorkingBeamProperties 的設備區段設定，從 PLC 大區塊 byte array 中切出
 * 單一 Working Beam 的 read/write、B/W 資料區，並提供 bits、words、string 解碼入口。</p>
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Service
@RequiredArgsConstructor
public class WorkingBeamMemoryLayoutService {

    private final PlcWorkingBeamProperties workingBeamProperties;

    private Map<Integer, PlcWorkingBeamProperties.WorkingBeam> beamMap;

    /**
     * 初始化快取，將 id -> WorkingBeam 映射表建構完成
     */
    public void init() {
        this.beamMap = new HashMap<>();
        for (PlcWorkingBeamProperties.WorkingBeam beam : workingBeamProperties.getWorkingBeams()) {
            beamMap.put(beam.getId(), beam);
        }
    }

    /**
     * 從大區段中抽出指定 Working Beam、記憶體類型（B/W）與區段類型（read/write）的資料
     *
     * @param beamId        Working Beam ID
     * @param areaType      區段類型（read / write）
     * @param memoryType    記憶體類型（B / W）
     * @param fullData      完整 PLC 資料
     * @param fullStart     該完整資料的起始地址（bit 或 word 為單位）
     * @return              切割出對應區段的 byte[]
     */
    public byte[] extractAreaBytes(int beamId, String areaType, String memoryType, byte[] fullData, int fullStart) {
        if (beamMap == null) {
            init();
        }

        PlcWorkingBeamProperties.WorkingBeam beam = beamMap.get(beamId);
        if (beam == null) {
            throw new IllegalArgumentException("WorkingBeam ID not found: " + beamId);
        }

        List<DeviceArea> areas =
                "read".equalsIgnoreCase(areaType) ? beam.getReadAreas() : beam.getWriteAreas();

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
                            String.format("Area out of full data range, beamId=%d, offset=%d, lengthBytes=%d, fullDataLength=%d",
                                    beamId, offset, lengthBytes, fullData.length)
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
    public boolean[] extractBits(int beamId, String areaType, byte[] fullData, int fullStart, int bitCount) {
        byte[] areaData = extractAreaBytes(beamId, areaType, "B", fullData, fullStart);
        return PlcDataCodec.bytesToBits(areaData, bitCount);
    }

    /**
     * 從指定 word 區段中切出 word 陣列
     */
    public int[] extractWords(int beamId, String areaType, byte[] fullData, int fullStart) {
        byte[] areaData = extractAreaBytes(beamId, areaType, "W", fullData, fullStart);
        return PlcDataCodec.bytesToWords(areaData);
    }

    /**
     * 從指定 word 區段中轉為文字（通常為產品 ID 類欄位）
     */
    public String extractString(int beamId, String areaType, byte[] fullData, int fullStart) {
        byte[] areaData = extractAreaBytes(beamId, areaType, "W", fullData, fullStart);
        return PlcDataCodec.decodeString(areaData, ByteOrder.LITTLE_ENDIAN);
    }
}
