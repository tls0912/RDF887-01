package com.czkuo.rdf88701.config.plc;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 裝置資訊查詢器，用來管理與查詢 PLC 裝置設定資訊。
 * 此類別封裝了名稱查詢、啟用過濾、外部控制過濾等常用邏輯。
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Component
@RequiredArgsConstructor
public class PlcDeviceRegistry {

    private final PlcProperties plcProperties;

    /** 快取：裝置名稱 → 設定資訊，用於加速查詢 */
    private volatile Map<String, PlcProperties.Device> nameToDeviceMap;

    /**
     * 取得所有啟用中的 PLC 裝置（enabled = true）。
     * 僅回傳目前系統實際會啟動與維護連線的裝置。
     */
    public List<PlcProperties.Device> getEnabledDevices() {
        return plcProperties.getDevices().stream()
                .filter(PlcProperties.Device::isEnabled)
                .toList();
    }

    /**
     * 取得所有已載入的 PLC 裝置（包含未啟用的設定）。
     * 通常用於系統管理介面或除錯時顯示完整設定。
     */
    public List<PlcProperties.Device> getAllDevices() {
        return plcProperties.getDevices();
    }

    /**
     * 取得所有「允許外部控制」且啟用中的裝置。
     * 僅這些裝置可以透過外部系統（API、UI）變更狀態。
     */
    public List<PlcProperties.Device> getExternalControlAllowedDevices() {
        return plcProperties.getDevices().stream()
                .filter(PlcProperties.Device::isEnabled)
                .filter(PlcProperties.Device::isExternalControlAllowed)
                .toList();
    }

    /**
     * 根據名稱查詢裝置資訊，若名稱不存在則拋出例外。
     * 使用名稱為 key 的快取，避免每次都線性搜尋。
     *
     * @param name 裝置名稱（唯一識別）
     * @return 對應的 PLC 裝置設定
     */
    public PlcProperties.Device getDevice(String name) {
        Map<String, PlcProperties.Device> map = nameToDeviceMap;

        // 雙重檢查鎖定（DCL）模式初始化快取
        if (map == null) {
            synchronized (this) {
                map = nameToDeviceMap;
                if (map == null) {
                    List<PlcProperties.Device> devices = plcProperties.getDevices();
                    if (devices == null) {
                        throw new IllegalStateException("PLC device list is not configured.");
                    }
                    map = new ConcurrentHashMap<>(
                            devices.stream()
                                    .collect(Collectors.toMap(PlcProperties.Device::getName, d -> d))
                    );
                    nameToDeviceMap = map;
                }
            }
        }

        return Optional.ofNullable(map.get(name))
                .orElseThrow(() -> new IllegalArgumentException("Unknown PLC device name: " + name));
    }

    /**
     * 判斷指定裝置是否允許外部控制。
     */
    public boolean isExternalControlAllowed(String name) {
        return getDevice(name).isExternalControlAllowed();
    }

    /**
     * 查詢指定裝置所使用的通訊協議（例如 "mc", "modbus"）。
     */
    public String getProtocol(String name) {
        return getDevice(name).getProtocol();
    }

    /**
     * 取得指定裝置所配置的協議選項（key-value 結構，交由協議解析器處理）。
     */
    public Map<String, Object> getOptions(String name) {
        return getDevice(name).getOptions();
    }

    /**
     * 從 options 中取得整數值（可指定預設值）
     */
    public int getOptionInt(String name, String key, int defaultValue) {
        Object value = getOptions(name).get(key);
        if (value instanceof Number num) {
            return num.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 從 options 中取得長整數值（可指定預設值）
     * 若 options 未包含該 key，或格式不正確，則回傳預設值。
     *
     * @param name 裝置名稱（唯一識別）
     * @param key  欲查詢的參數 key（通常為 YAML 中 options.*）
     * @param defaultValue 若無值或格式錯誤時的預設值
     * @return 對應 key 的 long 值，或預設值
     */
    public long getOptionLong(String name, String key, long defaultValue) {
        Object value = getOptions(name).get(key);
        if (value instanceof Number num) {
            return num.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 從 options 中取得布林值（可指定預設值）
     */
    public boolean getOptionBoolean(String name, String key, boolean defaultValue) {
        Object value = getOptions(name).get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String str) {
            return Boolean.parseBoolean(str);
        }
        return defaultValue;
    }
}
