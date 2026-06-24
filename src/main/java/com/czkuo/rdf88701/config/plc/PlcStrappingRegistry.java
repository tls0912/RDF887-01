package com.czkuo.rdf88701.config.plc;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * PlcStrappingRegistry
 * - 提供 plc-strapping.yml 中的 Strapping 設備設定
 * - 提供名稱查詢、起始位址解析等功能
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Component
@RequiredArgsConstructor
public class PlcStrappingRegistry {

    private final PlcStrappingProperties strappingProperties;

    /**
     * 取得所有 Strapping 設定
     */
    public List<PlcStrappingProperties.Strapping> getStrappings() {
        return strappingProperties.getStrappings();
    }

    /**
     * 取得所有 Strapping 名稱
     */
    public List<String> getAllStrappingNames() {
        return getStrappings().stream()
                .map(PlcStrappingProperties.Strapping::getName)
                .collect(Collectors.toList());
    }

    /**
     * 取得所有 Strapping ID（轉成 Long）
     */
    public List<Long> getAllStrappingIds() {
        return getStrappings().stream()
                .map(s -> (long) s.getId())
                .collect(Collectors.toList());
    }

    /**
     * 根據名稱查詢 Strapping
     */
    public PlcStrappingProperties.Strapping getStrappingByName(String name) {
        return getStrappings().stream()
                .filter(s -> s.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Strapping not found: " + name));
    }

    /**
     * 根據 ID 查詢 Strapping
     */
    public PlcStrappingProperties.Strapping getStrappingById(int id) {
        return getStrappings().stream()
                .filter(s -> s.getId() == id)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Strapping not found by id: " + id));
    }

    /**
     * 根據 Strapping 名稱查找對應的 PLC Device Name
     */
    public String resolvePlcDeviceNameByName(String name) {
        return getStrappingByName(name).getPlcDeviceName();
    }

    /**
     * 根據 Strapping ID 查找對應的 PLC Device Name
     */
    public String resolvePlcDeviceNameById(int id) {
        return getStrappingById(id).getPlcDeviceName();
    }

    /**
     * 根據 Strapping ID 查找名稱
     */
    public String getStrappingNameById(int id) {
        return getStrappingById(id).getName();
    }

    /**
     * 取得 Bit 寫入區起始位址（第一個 type=B 的 write-area）
     */
    public int getWriteBitStartAddress(String name) {
        return getStrappingByName(name).getWriteAreas().stream()
                .filter(a -> "B".equalsIgnoreCase(a.getType()))
                .map(DeviceArea::getAddress)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No B write-area found for: " + name));
    }

    /**
     * 取得 Word 寫入區起始位址（第一個 type=W 的 write-area）
     */
    public int getWriteWordStartAddress(String name) {
        return getStrappingByName(name).getWriteAreas().stream()
                .filter(a -> "W".equalsIgnoreCase(a.getType()))
                .map(DeviceArea::getAddress)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No W write-area found for: " + name));
    }

    /**
     * 取得 Bit 讀取區起始位址（第一個 type=B 的 read-area）
     */
    public int getReadBitStartAddress(String name) {
        return getStrappingByName(name).getReadAreas().stream()
                .filter(a -> "B".equalsIgnoreCase(a.getType()))
                .map(DeviceArea::getAddress)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No B read-area found for: " + name));
    }

    /**
     * 取得 Word 讀取區起始位址（第一個 type=W 的 read-area）
     */
    public int getReadWordStartAddress(String name) {
        return getStrappingByName(name).getReadAreas().stream()
                .filter(a -> "W".equalsIgnoreCase(a.getType()))
                .map(DeviceArea::getAddress)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No W read-area found for: " + name));
    }

    /**
     * 預設 From Word 起始地址（通常為 W 起始位）
     */
    public int getFromWordStartAddress(String name) {
        return getWriteWordStartAddress(name);
    }

    /**
     * 預設 To Word 起始地址（通常位於 From 後 offset，例如 31）
     */
    public int getToWordStartAddress(String name) {
        return getWriteWordStartAddress(name) + 31;
    }

    /**
     * Bit 傳輸欄位起始索引（交握 bit 通常從這個位置開始）
     */
    public int getHandshakeBitStartAddress(String name) {
        return getWriteBitStartAddress(name);
    }
}
