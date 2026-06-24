package com.czkuo.rdf88701.config.plc;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * PlcWorkingBeamRegistry
 * - 專責提供 plc-working-beam.yml 中的 Working Beam 設備設定
 * - 與 PlcDeviceRegistry 分層職責
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Component
@RequiredArgsConstructor
public class PlcWorkingBeamRegistry {

    private final PlcWorkingBeamProperties workingBeamProperties;

    /**
     * 取得所有 Working Beam 設定
     */
    public List<PlcWorkingBeamProperties.WorkingBeam> getWorkingBeams() {
        return workingBeamProperties.getWorkingBeams();
    }

    /**
     * 取得所有 Working Beam 名稱
     */
    public List<String> getAllWorkingBeamNames() {
        return workingBeamProperties.getWorkingBeams().stream()
                .map(PlcWorkingBeamProperties.WorkingBeam::getName)
                .collect(Collectors.toList());
    }

    /**
     * 取得所有 Working Beam ID
     */
    public Set<Long> getAllWorkingBeamIds() {
        return getWorkingBeams().stream()
                .map(beam -> (long) beam.getId())
                .collect(Collectors.toSet());
    }

    /**
     * 根據名稱查詢 Working Beam
     */
    public PlcWorkingBeamProperties.WorkingBeam getWorkingBeamByName(String name) {
        return workingBeamProperties.getWorkingBeams().stream()
                .filter(c -> c.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("WorkingBeam not found: " + name));
    }

    /**
     * 根據 ID 查詢 Working Beam
     */
    public PlcWorkingBeamProperties.WorkingBeam getWorkingBeamById(int id) {
        return workingBeamProperties.getWorkingBeams().stream()
                .filter(c -> c.getId() == id)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("WorkingBeam not found by id: " + id));
    }

    /**
     * 根據 Working Beam 名稱查找對應的 PLC Device Name
     */
    public String resolvePlcDeviceNameByName(String name) {
        return getWorkingBeamByName(name).getPlcDeviceName();
    }

    /**
     * 根據 Working Beam ID 查找對應的 PLC Device Name
     */
    public String resolvePlcDeviceNameById(int id) {
        return getWorkingBeamById(id).getPlcDeviceName();
    }

    /**
     * 根據 Working Beam ID 查找名稱
     */
    public String getWorkingBeamNameById(int id) {
        return getWorkingBeamById(id).getName();
    }

    /**
     * 取得 Bit 寫入區起始位址（第一個 type=B 的 write-area）
     */
    public int getWriteBitStartAddress(String name) {
        return getWorkingBeamByName(name).getWriteAreas().stream()
                .filter(a -> "B".equalsIgnoreCase(a.getType()))
                .map(DeviceArea::getAddress)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No B write-area found for: " + name));
    }

    /**
     * 取得 Word 寫入區起始位址（第一個 type=W 的 write-area）
     */
    public int getWriteWordStartAddress(String name) {
        return getWorkingBeamByName(name).getWriteAreas().stream()
                .filter(a -> "W".equalsIgnoreCase(a.getType()))
                .map(DeviceArea::getAddress)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No W write-area found for: " + name));
    }

    /**
     * 取得 Bit 讀取區起始位址（第一個 type=B 的 read-area）
     */
    public int getReadBitStartAddress(String name) {
        return getWorkingBeamByName(name).getReadAreas().stream()
                .filter(a -> "B".equalsIgnoreCase(a.getType()))
                .map(DeviceArea::getAddress)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No B read-area found for: " + name));
    }

    /**
     * 取得 Word 讀取區起始位址（第一個 type=W 的 read-area）
     */
    public int getReadWordStartAddress(String name) {
        return getWorkingBeamByName(name).getReadAreas().stream()
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
