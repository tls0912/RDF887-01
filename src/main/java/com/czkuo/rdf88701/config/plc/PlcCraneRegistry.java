package com.czkuo.rdf88701.config.plc;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * PlcCraneRegistry
 * - 專責提供 plc-crane.yml 中的 Crane 設備設定
 * - 與 PlcDeviceRegistry 分層職責
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Component
@RequiredArgsConstructor
public class PlcCraneRegistry {

    private final PlcCraneProperties craneProperties;

    /**
     * 取得所有 Crane 設定
     */
    public List<PlcCraneProperties.Crane> getCranes() {

        return craneProperties.getCranes();
    }

    /**
     * 取得所有 Crane 名稱
     */
    public List<String> getAllCraneNames() {
        return craneProperties.getCranes().stream()
                .map(PlcCraneProperties.Crane::getName)
                .collect(Collectors.toList());
    }

    /**
     * 取得所有 Crane ID
     */
    public Set<Long> getAllCraneIds() {
        return getCranes().stream()
                .map(beam -> (long) beam.getId())
                .collect(Collectors.toSet());
    }

    /**
     * 根據名稱查詢 Crane
     */
    public PlcCraneProperties.Crane getCraneByName(String name) {
        return craneProperties.getCranes().stream()
                .filter(c -> c.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Crane not found: " + name));
    }

    /**
     * 根據 ID 查詢 Crane
     */
    public PlcCraneProperties.Crane getCraneById(int id) {
        return craneProperties.getCranes().stream()
                .filter(c -> c.getId() == id)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Crane not found by id: " + id));
    }

    /**
     * 根據 Crane 名稱查找對應的 PLC Device Name
     */
    public String resolvePlcDeviceNameByCraneName(String craneName) {
        return getCraneByName(craneName).getPlcDeviceName();
    }

    /**
     * 取得 Word 寫入區起始位址（第一個 type=W 的 write-area）
     */
    public int getWriteWordStartAddress(String craneName) {
        return getCraneByName(craneName).getWriteAreas().stream()
                .filter(a -> "W".equalsIgnoreCase(a.getType()))
                .map(DeviceArea::getAddress)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No W write-area found for: " + craneName));
    }

    /**
     * 根據 Crane ID 取得名稱
     */
    public String getCraneNameById(int craneId) {
        return getCraneById(craneId).getName();
    }

    /**
     * 根據 Crane ID 取得對應 PLC Device Name
     */
    public String resolvePlcDeviceNameByCraneId(int craneId) {
        return getCraneById(craneId).getPlcDeviceName();
    }

    /**
     * 取得 Bit 寫入區起始位址（第一個 type=B 的 write-area）
     */
    public int getWriteBitStartAddress(String craneName) {
        return getCraneByName(craneName).getWriteAreas().stream()
                .filter(a -> "B".equalsIgnoreCase(a.getType()))
                .map(DeviceArea::getAddress)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No B write-area found for: " + craneName));
    }

    /**
     * From Transfer Word 區起始地址（預設即為 write W 起點）
     */
    public int getFromWordStartAddress(String craneName) {
        return getWriteWordStartAddress(craneName);
    }

    /**
     * To Transfer Word 區起始地址（預設為 From 起點 + 31）
     */
    public int getToWordStartAddress(String craneName) {
        return getWriteWordStartAddress(craneName) + 31;
    }

    /**
     * Bit 傳輸欄位起始索引（交握 bit 通常從這個位置往後配置）
     */
    public int getHandshakeBitStartAddress(String craneName) {
        return getWriteBitStartAddress(craneName);
    }
}
