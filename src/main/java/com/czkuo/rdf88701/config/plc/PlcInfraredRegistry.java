package com.czkuo.rdf88701.config.plc;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * PlcInfraredRegistry
 * - 提供 plc-infrared-distance.yml 中的紅外線測距感測器配置
 * - 支援名稱、ID、PLC Device、讀寫區資訊查詢
 */
@Component
@RequiredArgsConstructor
public class PlcInfraredRegistry {

    private final PlcInfraredProperties infraredProperties;

    /**
     * 取得所有紅外線感測器設定
     */
    public List<PlcInfraredProperties.Infrared> getInfrareds() {
        return infraredProperties.getInfrareds();
    }

    /**
     * 取得所有紅外線感測器名稱清單
     */
    public List<String> getAllInfraredNames() {
        return getInfrareds().stream()
                .map(PlcInfraredProperties.Infrared::getName)
                .collect(Collectors.toList());
    }

    /**
     * 取得所有紅外線感測器 ID 清單
     */
    public Set<Long> getAllInfraredIds() {
        return getInfrareds().stream()
                .map(sensor -> (long) sensor.getId())
                .collect(Collectors.toSet());
    }

    /**
     * 根據名稱查詢紅外線感測器
     */
    public PlcInfraredProperties.Infrared getInfraredByName(String name) {
        return getInfrareds().stream()
                .filter(s -> s.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Infrared sensor not found: " + name));
    }

    /**
     * 根據 ID 查詢紅外線感測器
     */
    public PlcInfraredProperties.Infrared getInfraredById(int id) {
        return getInfrareds().stream()
                .filter(s -> s.getId() == id)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Infrared sensor not found by id: " + id));
    }

    /**
     * 根據名稱查找對應的 PLC Device Name
     */
    public String resolvePlcDeviceNameByName(String name) {
        return getInfraredByName(name).getPlcDeviceName();
    }

    /**
     * 根據 ID 查找對應的 PLC Device Name
     */
    public String resolvePlcDeviceNameById(int id) {
        return getInfraredById(id).getPlcDeviceName();
    }

    /**
     * 根據 ID 查找紅外線設備名稱
     */
    public String getInfraredNameById(int id) {
        return getInfraredById(id).getName();
    }

    /**
     * 根據名稱查找紅外線設備 ID
     */
    public int getInfraredIdByName(String name) {
        return getInfraredByName(name).getId();
    }

    /**
     * 取得 Bit 寫入區起始位址（第一個 type=B 的 write-area）
     */
    public int getWriteBitStartAddress(String name) {
        return getInfraredByName(name).getWriteAreas().stream()
                .filter(a -> "B".equalsIgnoreCase(a.getType()))
                .map(DeviceArea::getAddress)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No B write-area found for: " + name));
    }

    /**
     * 取得 Word 寫入區起始位址（第一個 type=W 的 write-area）
     */
    public int getWriteWordStartAddress(String name) {
        return getInfraredByName(name).getWriteAreas().stream()
                .filter(a -> "W".equalsIgnoreCase(a.getType()))
                .map(DeviceArea::getAddress)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No W write-area found for: " + name));
    }

    /**
     * 取得 Bit 讀取區起始位址（第一個 type=B 的 read-area）
     */
    public int getReadBitStartAddress(String name) {
        return getInfraredByName(name).getReadAreas().stream()
                .filter(a -> "B".equalsIgnoreCase(a.getType()))
                .map(DeviceArea::getAddress)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No B read-area found for: " + name));
    }

    /**
     * 取得 Word 讀取區起始位址（第一個 type=W 的 read-area）
     */
    public int getReadWordStartAddress(String name) {
        return getInfraredByName(name).getReadAreas().stream()
                .filter(a -> "W".equalsIgnoreCase(a.getType()))
                .map(DeviceArea::getAddress)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No W read-area found for: " + name));
    }

    /**
     * Bit 傳輸欄位起始索引（交握 bit 通常從這個位置往後配置）
     */
    public int getHandshakeBitStartAddress(String name) {
        return getWriteBitStartAddress(name);
    }
}
