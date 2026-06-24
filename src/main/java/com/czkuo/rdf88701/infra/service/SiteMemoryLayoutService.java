package com.czkuo.rdf88701.infra.service;

import com.czkuo.rdf88701.common.util.PlcDataCodec;
import com.czkuo.rdf88701.config.plc.DeviceArea;
import com.czkuo.rdf88701.config.plc.PlcSiteProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Site PLC 記憶體布局解析服務。
 *
 * <p>依 PlcSiteProperties 的設備區段設定，從 PLC 大區塊 byte array 中切出單一
 * Site 的 read/write、B/W 資料區，並提供 bits、words、string 解碼入口。</p>
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Service
@RequiredArgsConstructor
public class SiteMemoryLayoutService {

    private final PlcSiteProperties siteProperties;

    private Map<Integer, PlcSiteProperties.Site> siteMap;

    /**
     * 初始化快取（ID → Site 裝置對應表）
     */
    public void init() {
        this.siteMap = new HashMap<>();
        for (PlcSiteProperties.Site site : siteProperties.getSites()) {
            siteMap.put(site.getId(), site);
        }

        if (siteMap.size() > 42) {
            throw new RuntimeException("<UNK>");
        }
    }

    /**
     * 從 PLC 的整體資料區中，切出單一 Site 的 B/W 區段資料
     *
     * @param siteId     Site 裝置 ID
     * @param areaType   區段類型（read / write）
     * @param memoryType 記憶體類型（B / W）
     * @param fullData   PLC 傳入或寫入的完整 byte[]
     * @param fullStart  該 byte[] 對應的 PLC 起始位址（bit 或 word）
     * @return 切割出來的實際 byte[] 區段
     */
    public byte[] extractAreaBytes(int siteId, String areaType, String memoryType, byte[] fullData, int fullStart) {
        if (siteMap == null) {
            init();
        }

        PlcSiteProperties.Site site = siteMap.get(siteId);
        if (site == null) {
            throw new IllegalArgumentException("Site ID not found: " + siteId);
        }

        List<DeviceArea> areas =
                "read".equalsIgnoreCase(areaType) ? site.getReadAreas() : site.getWriteAreas();

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
                            String.format("Area out of full data range, siteId=%d, offset=%d, lengthBytes=%d, fullDataLength=%d",
                                    siteId, offset, lengthBytes, fullData.length)
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
     * 從 bit 區段切出 bit 陣列（for 裝置狀態）
     */
    public boolean[] extractBits(int siteId, String areaType, byte[] fullData, int fullStart, int bitCount) {
        byte[] areaData = extractAreaBytes(siteId, areaType, "B", fullData, fullStart);
        return PlcDataCodec.bytesToBits(areaData, bitCount);
    }

    /**
     * 從 word 區段切出 word 陣列（for 狀態碼 / 字串）
     */
    public int[] extractWords(int siteId, String areaType, byte[] fullData, int fullStart) {
        byte[] areaData = extractAreaBytes(siteId, areaType, "W", fullData, fullStart);
        return PlcDataCodec.bytesToWords(areaData);
    }

    /**
     * 從 word 區段解碼為文字（如 Product ID 等）
     */
    public String extractString(int siteId, String areaType, byte[] fullData, int fullStart) {
        byte[] areaData = extractAreaBytes(siteId, areaType, "W", fullData, fullStart);
        return PlcDataCodec.decodeString(areaData, ByteOrder.LITTLE_ENDIAN);
    }
}
