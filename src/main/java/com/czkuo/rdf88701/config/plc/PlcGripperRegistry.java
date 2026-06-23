package com.czkuo.rdf88701.config.plc;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * PlcGripperRegistry
 * - 提供 plc-gripper.yml 中的 Gripper 設備設定
 * - 提供名稱查詢、起始位址解析等功能
 */
@Component
@RequiredArgsConstructor
public class PlcGripperRegistry {

    private final PlcGripperProperties gripperProperties;

    /**
     * 取得所有 Gripper 設定
     */
    public List<PlcGripperProperties.Gripper> getGrippers() {
        return gripperProperties.getGrippers();
    }

    /**
     * 取得所有 Gripper 名稱
     */
    public List<String> getAllGripperNames() {
        return gripperProperties.getGrippers().stream()
                .map(PlcGripperProperties.Gripper::getName)
                .collect(Collectors.toList());
    }

    /**
     * 取得所有 Gripper ID（轉成 Long 方便統一處理）
     */
    public List<Long> getAllGripperIds() {
        return gripperProperties.getGrippers().stream()
                .map(g -> (long) g.getId())
                .collect(Collectors.toList());
    }

    /**
     * 根據名稱查詢 Gripper
     */
    public PlcGripperProperties.Gripper getGripperByName(String name) {
        return gripperProperties.getGrippers().stream()
                .filter(g -> g.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Gripper not found: " + name));
    }

    /**
     * 根據 ID 查詢 Gripper
     */
    public PlcGripperProperties.Gripper getGripperById(int id) {
        return gripperProperties.getGrippers().stream()
                .filter(g -> g.getId() == id)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Gripper not found by id: " + id));
    }

    /**
     * 根據 Gripper 名稱查找對應的 PLC Device Name
     */
    public String resolvePlcDeviceNameByName(String name) {
        return getGripperByName(name).getPlcDeviceName();
    }

    /**
     * 根據 Gripper ID 查找對應的 PLC Device Name
     */
    public String resolvePlcDeviceNameById(int id) {
        return getGripperById(id).getPlcDeviceName();
    }

    /**
     * 根據 Gripper ID 查找名稱
     */
    public String getGripperNameById(int id) {
        return getGripperById(id).getName();
    }

    /**
     * 取得 Bit 寫入區起始位址（第一個 type=B 的 write-area）
     */
    public int getWriteBitStartAddress(String name) {
        return getGripperByName(name).getWriteAreas().stream()
                .filter(a -> "B".equalsIgnoreCase(a.getType()))
                .map(DeviceArea::getAddress)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No B write-area found for: " + name));
    }

    /**
     * 取得 Word 寫入區起始位址（第一個 type=W 的 write-area）
     */
    public int getWriteWordStartAddress(String name) {
        return getGripperByName(name).getWriteAreas().stream()
                .filter(a -> "W".equalsIgnoreCase(a.getType()))
                .map(DeviceArea::getAddress)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No W write-area found for: " + name));
    }

    /**
     * 取得 Bit 讀取區起始位址（第一個 type=B 的 read-area）
     */
    public int getReadBitStartAddress(String name) {
        return getGripperByName(name).getReadAreas().stream()
                .filter(a -> "B".equalsIgnoreCase(a.getType()))
                .map(DeviceArea::getAddress)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No B read-area found for: " + name));
    }

    /**
     * 取得 Word 讀取區起始位址（第一個 type=W 的 read-area）
     */
    public int getReadWordStartAddress(String name) {
        return getGripperByName(name).getReadAreas().stream()
                .filter(a -> "W".equalsIgnoreCase(a.getType()))
                .map(DeviceArea::getAddress)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No W read-area found for: " + name));
    }

    /**
     * 預設 From Word 起始地址（如無特殊配置則為 W 起始位）
     */
    public int getFromWordStartAddress(String name) {
        return getWriteWordStartAddress(name);
    }

    /**
     * 預設 To Word 起始地址（如需可調整 offset）
     */
    public int getToWordStartAddress(String name) {
        return getWriteWordStartAddress(name) + 31;
    }

    /**
     * Bit 傳輸欄位起始索引（交握 bit 通常從這個位置往後配置）
     */
    public int getHandshakeBitStartAddress(String name) {
        return getWriteBitStartAddress(name);
    }
}
