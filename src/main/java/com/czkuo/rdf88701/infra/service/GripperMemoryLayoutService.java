package com.czkuo.rdf88701.infra.service;

import com.czkuo.rdf88701.common.util.PlcDataCodec;
import com.czkuo.rdf88701.config.plc.DeviceArea;
import com.czkuo.rdf88701.config.plc.PlcGripperProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gripper PLC 記憶體布局解析服務。
 *
 * <p>依 PlcGripperProperties 的設備區段設定，從 PLC 大區塊 byte array 中切出單一
 * Gripper 的 read/write、B/W 資料區，並提供 bits、words、string 解碼入口。</p>
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Service
@RequiredArgsConstructor
public class GripperMemoryLayoutService {

    private final PlcGripperProperties gripperProperties;

    private Map<Integer, PlcGripperProperties.Gripper> gripperMap;

    /**
     * 初始化時將 grippers list 轉為 id -> Gripper 快取表
     */
    public void init() {
        this.gripperMap = new HashMap<>();
        for (PlcGripperProperties.Gripper g : gripperProperties.getGrippers()) {
            gripperMap.put(g.getId(), g);
        }
    }

    /**
     * 切出指定 Gripper、指定區段的原始 byte[]
     * 注意：
     * - fullData 是 PLC 回來的原始 byte array
     * - fullStart 是區段開始的元件位址
     */
    public byte[] extractAreaBytes(int gripperId, String areaType, String memoryType, byte[] fullData, int fullStart) {
        if (gripperMap == null) {
            init();
        }

        PlcGripperProperties.Gripper gripper = gripperMap.get(gripperId);
        if (gripper == null) {
            throw new IllegalArgumentException("Gripper ID not found: " + gripperId);
        }

        List<DeviceArea> areas =
                "read".equalsIgnoreCase(areaType) ? gripper.getReadAreas() : gripper.getWriteAreas();

        for (DeviceArea area : areas) {
            if (area.getType().equalsIgnoreCase(memoryType)) {
                int offset;
                int lengthBytes;

                if ("B".equalsIgnoreCase(memoryType)) {
                    // Bit區計算修正
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
                            String.format("Area out of full data range, gripperId=%d, offset=%d, lengthBytes=%d, fullDataLength=%d",
                                    gripperId, offset, lengthBytes, fullData.length)
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
     * 切出並解碼指定 Gripper 的 B 區 bit 資料
     */
    public boolean[] extractBits(int gripperId, String areaType, byte[] fullData, int fullStart, int bitCount) {
        byte[] areaData = extractAreaBytes(gripperId, areaType, "B", fullData, fullStart);
        return PlcDataCodec.bytesToBits(areaData, bitCount);
    }

    /**
     * 切出並解碼指定 Gripper 的 W 區 word 資料
     */
    public int[] extractWords(int gripperId, String areaType, byte[] fullData, int fullStart) {
        byte[] areaData = extractAreaBytes(gripperId, areaType, "W", fullData, fullStart);
        return PlcDataCodec.bytesToWords(areaData);
    }

    /**
     * 切出並解碼指定 Gripper 的 W 區 String 資料
     */
    public String extractString(int gripperId, String areaType, byte[] fullData, int fullStart) {
        byte[] areaData = extractAreaBytes(gripperId, areaType, "W", fullData, fullStart);
        return PlcDataCodec.decodeString(areaData, ByteOrder.LITTLE_ENDIAN);
    }
}
