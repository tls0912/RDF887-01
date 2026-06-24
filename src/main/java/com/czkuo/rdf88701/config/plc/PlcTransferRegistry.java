package com.czkuo.rdf88701.config.plc;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * PlcTransferRegistry
 * - 專責提供 plc-transfer.yml 中的 Transfer 設備設定
 * - 與 PlcDeviceRegistry 分層職責
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Component
@RequiredArgsConstructor
public class PlcTransferRegistry {

    private final PlcTransferProperties transferProperties;

    /**
     * 取得所有 Transfer 設定
     */
    public List<PlcTransferProperties.Transfer> getTransfers() {
        return transferProperties.getTransfers();
    }

    /**
     * 取得所有 Transfer 名稱
     */
    public List<String> getAllTransferNames() {
        return transferProperties.getTransfers().stream()
                .map(PlcTransferProperties.Transfer::getName)
                .collect(Collectors.toList());
    }

    /**
     * 取得所有 Transfer 裝置 ID
     */
    public List<Long> getAllTransferIds() {
        return transferProperties.getTransfers().stream()
                .map(t -> (long) t.getId())
                .collect(Collectors.toList());
    }

    /**
     * 根據名稱查詢 Transfer
     */
    public PlcTransferProperties.Transfer getTransferByName(String name) {
        return transferProperties.getTransfers().stream()
                .filter(t -> t.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found: " + name));
    }

    /**
     * 根據 ID 查詢 Transfer
     */
    public PlcTransferProperties.Transfer getTransferById(int id) {
        return transferProperties.getTransfers().stream()
                .filter(t -> t.getId() == id)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found by id: " + id));
    }

    /**
     * 根據 Transfer 名稱查找對應的 PLC Device Name
     */
    public String resolvePlcDeviceNameByName(String name) {
        return getTransferByName(name).getPlcDeviceName();
    }

    /**
     * 根據 Transfer ID 查找對應的 PLC Device Name
     */
    public String resolvePlcDeviceNameById(int id) {
        return getTransferById(id).getPlcDeviceName();
    }

    /**
     * 根據 Transfer ID 查找名稱
     */
    public String getTransferNameById(int id) {
        return getTransferById(id).getName();
    }

    /**
     * 取得 Bit 寫入區起始位址（第一個 type=B 的 write-area）
     */
    public int getWriteBitStartAddress(String name) {
        return getTransferByName(name).getWriteAreas().stream()
                .filter(a -> "B".equalsIgnoreCase(a.getType()))
                .map(DeviceArea::getAddress)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No B write-area found for: " + name));
    }

    /**
     * 取得 Word 寫入區起始位址（第一個 type=W 的 write-area）
     */
    public int getWriteWordStartAddress(String name) {
        return getTransferByName(name).getWriteAreas().stream()
                .filter(a -> "W".equalsIgnoreCase(a.getType()))
                .map(DeviceArea::getAddress)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No W write-area found for: " + name));
    }

    /**
     * 取得 Bit 讀取區起始位址（第一個 type=B 的 read-area）
     */
    public int getReadBitStartAddress(String name) {
        return getTransferByName(name).getReadAreas().stream()
                .filter(a -> "B".equalsIgnoreCase(a.getType()))
                .map(DeviceArea::getAddress)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No B read-area found for: " + name));
    }

    /**
     * 取得 Word 讀取區起始位址（第一個 type=W 的 read-area）
     */
    public int getReadWordStartAddress(String name) {
        return getTransferByName(name).getReadAreas().stream()
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
