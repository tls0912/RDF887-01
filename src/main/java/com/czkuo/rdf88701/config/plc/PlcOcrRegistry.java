package com.czkuo.rdf88701.config.plc;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * PlcOcrRegistry
 * - 提供 plc-ocr.yml 中的 OCR 設備設定
 * - 提供名稱查詢、起始位址解析等功能
 */
@Component
@RequiredArgsConstructor
public class PlcOcrRegistry {

    private final PlcOcrProperties ocrProperties;

    /**
     * 取得所有 OCR 設定
     */
    public List<PlcOcrProperties.Ocr> getOcrDevices() {
        return ocrProperties.getDevices();
    }

    /**
     * 取得所有 OCR 名稱
     */
    public List<String> getAllOcrNames() {
        return getOcrDevices().stream()
                .map(PlcOcrProperties.Ocr::getName)
                .collect(Collectors.toList());
    }

    /**
     * 取得所有 OCR ID（轉為 Long）
     */
    public List<Long> getAllOcrIds() {
        return getOcrDevices().stream()
                .map(ocr -> (long) ocr.getId())
                .collect(Collectors.toList());
    }

    /**
     * 根據名稱查詢 OCR 裝置
     */
    public PlcOcrProperties.Ocr getOcrByName(String name) {
        return getOcrDevices().stream()
                .filter(o -> o.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("OCR not found: " + name));
    }

    /**
     * 根據 ID 查詢 OCR 裝置
     */
    public PlcOcrProperties.Ocr getOcrById(int id) {
        return getOcrDevices().stream()
                .filter(o -> o.getId() == id)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("OCR not found by id: " + id));
    }

    /**
     * 根據名稱取得 PLC device 名稱
     */
    public String resolvePlcDeviceNameByName(String name) {
        return getOcrByName(name).getPlcDeviceName();
    }

    /**
     * 根據 ID 取得 PLC device 名稱
     */
    public String resolvePlcDeviceNameById(int id) {
        return getOcrById(id).getPlcDeviceName();
    }

    /**
     * 根據 ID 查詢名稱
     */
    public String getOcrNameById(int id) {
        return getOcrById(id).getName();
    }

    /**
     * 取得 Bit 寫入區起始位址
     */
    public int getWriteBitStartAddress(String name) {
        return getOcrByName(name).getWriteAreas().stream()
                .filter(a -> "B".equalsIgnoreCase(a.getType()))
                .map(DeviceArea::getAddress)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No B write-area found for: " + name));
    }

    /**
     * 取得 Word 寫入區起始位址
     */
    public int getWriteWordStartAddress(String name) {
        return getOcrByName(name).getWriteAreas().stream()
                .filter(a -> "W".equalsIgnoreCase(a.getType()))
                .map(DeviceArea::getAddress)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No W write-area found for: " + name));
    }

    /**
     * 取得 Bit 讀取區起始位址
     */
    public int getReadBitStartAddress(String name) {
        return getOcrByName(name).getReadAreas().stream()
                .filter(a -> "B".equalsIgnoreCase(a.getType()))
                .map(DeviceArea::getAddress)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No B read-area found for: " + name));
    }

    /**
     * 取得 Word 讀取區起始位址
     */
    public int getReadWordStartAddress(String name) {
        return getOcrByName(name).getReadAreas().stream()
                .filter(a -> "W".equalsIgnoreCase(a.getType()))
                .map(DeviceArea::getAddress)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No W read-area found for: " + name));
    }

    /**
     * 預設 From Word 起始地址
     */
    public int getFromWordStartAddress(String name) {
        return getWriteWordStartAddress(name);
    }

    /**
     * 預設 To Word 起始地址（一般在 from 區塊之後）
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
