package com.czkuo.rdf88701.config.plc;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * PlcSafetyRegistry
 * - 提供 plc-safety.yml 的設備與點位查詢
 * - 名稱/ID/PLC 裝置名解析、讀區起始位址、addr 解析 (Wxxxx.b) 等工具
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Component
@RequiredArgsConstructor
public class PlcSafetyRegistry {

    private final PlcSafetyProperties safetyProperties;

    /* ========================= 基本取用 ========================= */

    /** 取得所有安全設備（Bank） */
    public List<PlcSafetyProperties.Device> getDevices() {
        return safetyProperties.getDevices();
    }

    /** 取得所有設備名稱 */
    public List<String> getAllDeviceNames() {
        return safetyProperties.getDevices().stream()
                .map(PlcSafetyProperties.Device::getName)
                .collect(Collectors.toList());
    }

    /** 取得所有設備 ID（轉成 Long 以便與其他元件一致） */
    public List<Long> getAllDeviceIds() {
        return safetyProperties.getDevices().stream()
                .map(d -> (long) d.getId())
                .collect(Collectors.toList());
    }

    /** 依名稱取設備 */
    public PlcSafetyProperties.Device getDeviceByName(String name) {
        return safetyProperties.getDevices().stream()
                .filter(d -> d.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Safety device not found: " + name));
    }

    /** 依 ID 取設備 */
    public PlcSafetyProperties.Device getDeviceById(int id) {
        return safetyProperties.getDevices().stream()
                .filter(d -> d.getId() == id)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Safety device not found by id: " + id));
    }

    /** 依設備名稱取得對應 PLC adapter 的 deviceName */
    public String resolvePlcDeviceNameByName(String deviceName) {
        return getDeviceByName(deviceName).getPlcDeviceName();
    }

    /** 依設備 ID 取得對應 PLC adapter 的 deviceName */
    public String resolvePlcDeviceNameById(int id) {
        return getDeviceById(id).getPlcDeviceName();
    }

    /* ========================= 區段位址解析 ========================= */

    /** 取得「第一個 W 型別」讀取區的起始位址（word 起始，例：0x1040 轉 int） */
    public int getReadWordStartAddress(String deviceName) {
        return getDeviceByName(deviceName).getReadAreas().stream()
                .filter(a -> "W".equalsIgnoreCase(a.getType()))
                .map(DeviceArea::getAddress)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No W read-area found for: " + deviceName));
    }

    /** 取得「第一個 B 型別」讀取區的起始位址（bit 起始） */
    public int getReadBitStartAddress(String deviceName) {
        return getDeviceByName(deviceName).getReadAreas().stream()
                .filter(a -> "B".equalsIgnoreCase(a.getType()))
                .map(DeviceArea::getAddress)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No B read-area found for: " + deviceName));
    }

    /** 取得所有 W 讀取區（方便需要整段遍歷的人用） */
    public List<DeviceArea> getReadWordAreas(String deviceName) {
        return getDeviceByName(deviceName).getReadAreas().stream()
                .filter(a -> "W".equalsIgnoreCase(a.getType()))
                .collect(Collectors.toList());
    }

    /* ========================= 點位清單 / 索引 ========================= */

    /** 取得指定設備的所有點位（含 enabled=false） */
    public List<PlcSafetyProperties.Point> getAllPoints(String deviceName) {
        return new ArrayList<>(getDeviceByName(deviceName).getPoints());
    }

    /** 取得指定設備「啟用中」的點位 */
    public List<PlcSafetyProperties.Point> getEnabledPoints(String deviceName) {
        return getDeviceByName(deviceName).getPoints().stream()
                .filter(PlcSafetyProperties.Point::isEnabled)
                .collect(Collectors.toList());
    }

    /** 取得指定設備的所有位址字串 */
    public List<String> getAllAddrs(String deviceName) {
        return getDeviceByName(deviceName).getPoints().stream()
                .map(PlcSafetyProperties.Point::getAddr)
                .collect(Collectors.toList());
    }

    /** 依 addr 取得點位（例：W1042.A） */
    public PlcSafetyProperties.Point getPointByAddr(String deviceName, String addr) {
        return getDeviceByName(deviceName).getPoints().stream()
                .filter(p -> p.getAddr().equalsIgnoreCase(addr))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Safety point not found: " + addr));
    }

    /** 將點位建索引：addr -> Point */
    public Map<String, PlcSafetyProperties.Point> indexByAddr(String deviceName) {
        return getDeviceByName(deviceName).getPoints().stream()
                .collect(Collectors.toMap(
                        p -> p.getAddr().toUpperCase(Locale.ROOT),
                        p -> p,
                        (a, b) -> a,
                        LinkedHashMap::new));
    }

    /** 依 Word 取點位（例：word = "W1040"） */
    public List<PlcSafetyProperties.Point> getPointsByWord(String deviceName, String word) {
        String upper = word.toUpperCase(Locale.ROOT);
        return getDeviceByName(deviceName).getPoints().stream()
                .filter(p -> toWord(p.getAddr()).equals(upper))
                .sorted(Comparator.comparingInt(p -> toBitIndex(p.getAddr())))
                .collect(Collectors.toList());
    }

    /** 依型別（DOOR/LIGHT_CURTAIN/EMO/OTHER）取啟用點位 */
    public List<PlcSafetyProperties.Point> getEnabledPointsByType(String deviceName, String type) {
        return getDeviceByName(deviceName).getPoints().stream()
                .filter(PlcSafetyProperties.Point::isEnabled)
                .filter(p -> type.equalsIgnoreCase(p.getType()))
                .collect(Collectors.toList());
    }

    /* ========================= 位址工具 ========================= */

    private static final Pattern ADDR_PATTERN = Pattern.compile("^W[0-9A-Fa-f]{4}\\.[0-9A-Fa-f]$");

    /** 從 addr 取出 Word（例：W1042.A -> W1042） */
    public String toWord(String addr) {
        int dot = addr.indexOf('.');
        if (dot <= 0) throw new IllegalArgumentException("Invalid addr: " + addr);
        return addr.substring(0, dot).toUpperCase(Locale.ROOT);
    }

    /** 將 addr 的 bit 轉成 0~15 的整數（十六進位字元） */
    public int toBitIndex(String addr) {
        int dot = addr.indexOf('.');
        if (dot <= 0 || dot == addr.length() - 1)
            throw new IllegalArgumentException("Invalid addr: " + addr);
        char c = addr.charAt(dot + 1);
        int v = Character.digit(c, 16);
        if (v < 0) throw new IllegalArgumentException("Invalid bit in addr: " + addr);
        return v;
    }

    /** 將 Word 轉十六進位 int 值（例："W1040" -> 0x1040） */
    public int wordHexValue(String word) {
        if (!word.toUpperCase(Locale.ROOT).startsWith("W"))
            throw new IllegalArgumentException("Invalid word: " + word);
        return Integer.parseInt(word.substring(1), 16);
    }

    /* ========================= 防呆檢查（可選） ========================= */

    /**
     * 基本校驗：addr 格式、重複位址、是否落在 read-areas 範圍（W 區）
     * 檢查失敗會丟 IllegalArgumentException；你也可以改成回傳 List<String> warnings。
     */
    public void validateDevice(String deviceName) {
        PlcSafetyProperties.Device d = getDeviceByName(deviceName);

        // 1) addr 格式
        for (PlcSafetyProperties.Point p : d.getPoints()) {
            String addr = p.getAddr();
            if (addr == null || !ADDR_PATTERN.matcher(addr).matches()) {
                throw new IllegalArgumentException("Bad addr format: " + addr + " (" + p.getName() + ")");
            }
        }

        // 2) 重複位址
        Map<String, Long> dup = d.getPoints().stream()
                .collect(Collectors.groupingBy(p -> p.getAddr().toUpperCase(Locale.ROOT), Collectors.counting()));
        dup.entrySet().stream().filter(e -> e.getValue() > 1).findFirst()
                .ifPresent(e -> { throw new IllegalArgumentException("Duplicate addr: " + e.getKey()); });

        // 3) 是否在 W 讀區範圍（若有配置 W 區）
        List<DeviceArea> wAreas = d.getReadAreas().stream()
                .filter(a -> "W".equalsIgnoreCase(a.getType()))
                .toList();

        if (!wAreas.isEmpty()) {
            // 將 W 區組成一組連續 word 範圍集合
            List<int[]> ranges = wAreas.stream()
                    .map(a -> new int[]{ a.getAddress(), a.getAddress() + a.getLength() - 1 })
                    .toList();

            for (PlcSafetyProperties.Point p : d.getPoints()) {
                int wordHex = wordHexValue(toWord(p.getAddr()));
                boolean covered = ranges.stream().anyMatch(r -> wordHex >= r[0] && wordHex <= r[1]);
                if (!covered) {
                    throw new IllegalArgumentException("Addr out of read W-range: " + p.getAddr());
                }
            }
        }
    }

    /** 一次驗證所有設備 */
    public void validateAll() {
        for (PlcSafetyProperties.Device d : getDevices()) {
            validateDevice(d.getName());
        }
    }
}
