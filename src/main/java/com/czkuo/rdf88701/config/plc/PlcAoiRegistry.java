package com.czkuo.rdf88701.config.plc;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * PlcAoiRegistry
 * - 提供 plc-aoi.yml 中的 AOI 設備設定
 * - 提供名稱查詢、起始位址解析等功能
 */
@Component
@RequiredArgsConstructor
public class PlcAoiRegistry {

    private final PlcAoiProperties aoiProperties;

    /**
     * 取得所有 AOI 裝置設定
     */
    public List<PlcAoiProperties.Aoi> getAoiDevices() {
        return aoiProperties.getDevices();
    }

    /**
     * 取得所有 AOI 裝置名稱
     */
    public List<String> getAllAoiNames() {
        return getAoiDevices().stream()
                .map(PlcAoiProperties.Aoi::getName)
                .collect(Collectors.toList());
    }

    /**
     * 取得所有 AOI 裝置 ID（轉為 Long）
     */
    public List<Long> getAllAoiIds() {
        return getAoiDevices().stream()
                .map(a -> (long) a.getId())
                .collect(Collectors.toList());
    }

    /**
     * 根據名稱查詢 AOI 裝置
     */
    public PlcAoiProperties.Aoi getAoiByName(String name) {
        return getAoiDevices().stream()
                .filter(a -> a.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("AOI not found: " + name));
    }

    /**
     * 根據 ID 查詢 AOI 裝置
     */
    public PlcAoiProperties.Aoi getAoiById(int id) {
        return getAoiDevices().stream()
                .filter(a -> a.getId() == id)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("AOI not found by id: " + id));
    }

    /**
     * 根據名稱取得 PLC device 名稱
     */
    public String resolvePlcDeviceNameByName(String name) {
        return getAoiByName(name).getPlcDeviceName();
    }

    /**
     * 根據 ID 取得 PLC device 名稱
     */
    public String resolvePlcDeviceNameById(int id) {
        return getAoiById(id).getPlcDeviceName();
    }

    /**
     * 根據 ID 查詢名稱
     */
    public String getAoiNameById(int id) {
        return getAoiById(id).getName();
    }

    /**
     * 取得 Bit 寫入區起始位址
     */
    public int getWriteBitStartAddress(String name) {
        return getAoiByName(name).getWriteAreas().stream()
                .filter(a -> "B".equalsIgnoreCase(a.getType()))
                .map(DeviceArea::getAddress)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No B write-area found for: " + name));
    }

    /**
     * 取得 Word 寫入區起始位址
     */
    public int getWriteWordStartAddress(String name) {
        return getAoiByName(name).getWriteAreas().stream()
                .filter(a -> "W".equalsIgnoreCase(a.getType()))
                .map(DeviceArea::getAddress)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No W write-area found for: " + name));
    }

    /**
     * 取得 Bit 讀取區起始位址
     */
    public int getReadBitStartAddress(String name) {
        return getAoiByName(name).getReadAreas().stream()
                .filter(a -> "B".equalsIgnoreCase(a.getType()))
                .map(DeviceArea::getAddress)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No B read-area found for: " + name));
    }

    /**
     * 取得 Word 讀取區起始位址
     */
    public int getReadWordStartAddress(String name) {
        return getAoiByName(name).getReadAreas().stream()
                .filter(a -> "W".equalsIgnoreCase(a.getType()))
                .map(DeviceArea::getAddress)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No W read-area found for: " + name));
    }

    /**
     * 預設 From Word 起始位址（通常為寫入區第一段）
     */
    public int getFromWordStartAddress(String name) {
        return getWriteWordStartAddress(name);
    }

    /**
     * 預設 To Word 起始位址（通常 offset +31）
     */
    public int getToWordStartAddress(String name) {
        return getWriteWordStartAddress(name) + 31;
    }

    /**
     * 交握位起始位址（通常為 B 區起始）
     */
    public int getHandshakeBitStartAddress(String name) {
        return getWriteBitStartAddress(name);
    }
}
