package com.czkuo.rdf88701.config.plc;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * PlcLabelingRegistry
 * - 提供 plc-labeling.yml 中的 Labeling 設備設定
 * - 提供名稱查詢、起始位址解析等功能
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Component
@RequiredArgsConstructor
public class PlcLabelingRegistry {

    private final PlcLabelingProperties labelingProperties;

    /**
     * 取得所有 Labeling 設定
     */
    public List<PlcLabelingProperties.Labeling> getLabelings() {
        return labelingProperties.getDevices();
    }

    /**
     * 取得所有 Labeling 名稱
     */
    public List<String> getAllLabelingNames() {
        return getLabelings().stream()
                .map(PlcLabelingProperties.Labeling::getName)
                .collect(Collectors.toList());
    }

    /**
     * 取得所有 Labeling ID（轉成 Long）
     */
    public List<Long> getAllLabelingIds() {
        return getLabelings().stream()
                .map(l -> (long) l.getId())
                .collect(Collectors.toList());
    }

    /**
     * 根據名稱查詢 Labeling 裝置
     */
    public PlcLabelingProperties.Labeling getLabelingByName(String name) {
        return getLabelings().stream()
                .filter(l -> l.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Labeling not found: " + name));
    }

    /**
     * 根據 ID 查詢 Labeling 裝置
     */
    public PlcLabelingProperties.Labeling getLabelingById(int id) {
        return getLabelings().stream()
                .filter(l -> l.getId() == id)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Labeling not found by id: " + id));
    }

    /**
     * 根據名稱取得 PLC device 名稱
     */
    public String resolvePlcDeviceNameByName(String name) {
        return getLabelingByName(name).getPlcDeviceName();
    }

    /**
     * 根據 ID 取得 PLC device 名稱
     */
    public String resolvePlcDeviceNameById(int id) {
        return getLabelingById(id).getPlcDeviceName();
    }

    /**
     * 根據 ID 查詢名稱
     */
    public String getLabelingNameById(int id) {
        return getLabelingById(id).getName();
    }

    /**
     * 取得 Bit 寫入區起始位址
     */
    public int getWriteBitStartAddress(String name) {
        return getLabelingByName(name).getWriteAreas().stream()
                .filter(a -> "B".equalsIgnoreCase(a.getType()))
                .map(DeviceArea::getAddress)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No B write-area found for: " + name));
    }

    /**
     * 取得 Word 寫入區起始位址
     */
    public int getWriteWordStartAddress(String name) {
        return getLabelingByName(name).getWriteAreas().stream()
                .filter(a -> "W".equalsIgnoreCase(a.getType()))
                .map(DeviceArea::getAddress)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No W write-area found for: " + name));
    }

    /**
     * 取得 Bit 讀取區起始位址
     */
    public int getReadBitStartAddress(String name) {
        return getLabelingByName(name).getReadAreas().stream()
                .filter(a -> "B".equalsIgnoreCase(a.getType()))
                .map(DeviceArea::getAddress)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No B read-area found for: " + name));
    }

    /**
     * 取得 Word 讀取區起始位址
     */
    public int getReadWordStartAddress(String name) {
        return getLabelingByName(name).getReadAreas().stream()
                .filter(a -> "W".equalsIgnoreCase(a.getType()))
                .map(DeviceArea::getAddress)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No W read-area found for: " + name));
    }

    /**
     * 預設 From Word 起始位址
     */
    public int getFromWordStartAddress(String name) {
        return getWriteWordStartAddress(name);
    }

    /**
     * 預設 To Word 起始位址（offset +31）
     */
    public int getToWordStartAddress(String name) {
        return getWriteWordStartAddress(name) + 31;
    }

    /**
     * Bit 傳輸欄位起始索引（通常為 B區起點）
     */
    public int getHandshakeBitStartAddress(String name) {
        return getWriteBitStartAddress(name);
    }
}