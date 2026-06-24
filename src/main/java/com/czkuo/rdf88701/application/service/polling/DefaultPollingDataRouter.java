package com.czkuo.rdf88701.application.service.polling;

import com.czkuo.rdf88701.application.service.polling.handler.*;
import com.czkuo.rdf88701.config.plc.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * DefaultPollingDataRouter
 * - 根據 PLC 回傳資料來源（設備名稱 + 區段 + 起始位址）判斷該資料屬於哪一個設備模組
 * - 支援單一 PLC 共用 Bit / Word 資料區段，多設備配置不同起始位址與長度
 * - 正確切割區段後，分派至對應的設備 Handler（如：Crane、Gripper 等）做進一步解析與處理
 * - 同時支援讀取區（Read）與寫入區（Write）的比對
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultPollingDataRouter implements PollingDataRouter {

    // 各設備對應的處理器（Handler）
    private final CranePollingHandler craneHandler;
    private final GripperPollingHandler gripperHandler;
    private final WorkingBeamPollingHandler workingBeamHandler;
    private final TransferPollingHandler transferHandler;
    private final StrappingPollingHandler strappingHandler;
    private final SitePollingHandler siteHandler;
    private final InfraredPollingHandler infraredHandler;
    private final SafetyPollingHandler safetyHandler;

    // 各設備的 PLC 設定資訊（包含對應的區段範圍）
    private final PlcCraneProperties craneProperties;
    private final PlcGripperProperties gripperProperties;
    private final PlcWorkingBeamProperties workingBeamProperties;
    private final PlcTransferProperties transferProperties;
    private final PlcStrappingProperties strappingProperties;
    private final PlcSiteProperties siteProperties;
    private final PlcInfraredProperties infraredProperties;
    private final PlcSafetyProperties safetyProperties;
    private final Map<String, List<DeviceRoute>> deviceRouteMap = new ConcurrentHashMap<>();

    /**
     * PLC 資料輪詢進來時進行分派處理
     *
     * @param deviceName   資料來源的 PLC 名稱
     * @param tag          標示區域
     * @param areaType     區段型別（B：Bit 區，W：Word 區）
     * @param startAddress 該筆資料的起始位址（PLC 位址）
     * @param data         PLC 回傳的資料內容（byte[]）
     * @param snapshotTime 此筆資料的快照時間
     */
    @PostConstruct
    public void initRouteMap() {

        // Crane
        for (PlcCraneProperties.Crane crane : craneProperties.getCranes()) {
            addRoute(crane.getPlcDeviceName(),
                    new DeviceRoute(crane.getId(), crane.getReadAreas(), crane.getWriteAreas(), craneHandler));
        }

        // Gripper
        for (PlcGripperProperties.Gripper g : gripperProperties.getGrippers()) {
            addRoute(g.getPlcDeviceName(),
                    new DeviceRoute(g.getId(), g.getReadAreas(), g.getWriteAreas(), gripperHandler));
        }

        // WorkingBeam
        for (PlcWorkingBeamProperties.WorkingBeam b : workingBeamProperties.getWorkingBeams()) {
            addRoute(b.getPlcDeviceName(),
                    new DeviceRoute(b.getId(), b.getReadAreas(), b.getWriteAreas(), workingBeamHandler));
        }

        // Transfer
        for (PlcTransferProperties.Transfer t : transferProperties.getTransfers()) {
            addRoute(t.getPlcDeviceName(),
                    new DeviceRoute(t.getId(), t.getReadAreas(), t.getWriteAreas(), transferHandler));
        }

        // Strapping
        for (PlcStrappingProperties.Strapping s : strappingProperties.getStrappings()) {
            addRoute(s.getPlcDeviceName(),
                    new DeviceRoute(s.getId(), s.getReadAreas(), s.getWriteAreas(), strappingHandler));
        }

        // Infrared
        for (PlcInfraredProperties.Infrared i : infraredProperties.getInfrareds()) {
            addRoute(i.getPlcDeviceName(),
                    new DeviceRoute(i.getId(), i.getReadAreas(), i.getWriteAreas(), infraredHandler));
        }

        // Site
        for (PlcSiteProperties.Site s : siteProperties.getSites()) {
            addRoute(s.getPlcDeviceName(),
                    new DeviceRoute(s.getId(), s.getReadAreas(), s.getWriteAreas(), siteHandler));
        }

        // Safety（只有 READ）
        for (PlcSafetyProperties.Device d : safetyProperties.getDevices()) {
            addRoute(d.getPlcDeviceName(),
                    new DeviceRoute(d.getId(), d.getReadAreas(), null, safetyHandler));
        }
    }

    private void addRoute(String deviceName, DeviceRoute route) {
        deviceRouteMap
                .computeIfAbsent(deviceName.toLowerCase(), k -> new ArrayList<>())
                .add(route);
    }

    @Override
    public void route(String deviceName, String tag, String areaType, int startAddress, byte[] data, Instant snapshotTime) {
        boolean matched = false;
        try {

            List<DeviceRoute> routes = deviceRouteMap.get(deviceName.toLowerCase());
            if (routes == null) {
                log.warn("[ROUTER] 無對應裝置：{}@{} 起始位址 {}", deviceName, areaType, startAddress);
                return;
            }

            for (DeviceRoute route : routes) {

                List<DeviceArea> areas = getAreas(tag, route.getReadAreas(), route.getWriteAreas());
                if (areas == null)
                    continue;
                matched |= handleAreas(route.id, areaType, startAddress, data, snapshotTime, areas, route.handler);
            }

            if (!matched)
                log.warn("[ROUTER] 無匹配範圍：{}@{} 起始 {}", deviceName, areaType, startAddress);


        } catch (Exception ex) {
            log.error("[ROUTER] route 錯誤：{}@{} 起始 {}：{}",
                    deviceName, areaType, startAddress, ex.getMessage(), ex);
        }
    }

    private List<DeviceArea> getAreas(String tag,
                                                      List<DeviceArea> readAreas,
                                                      List<DeviceArea> writeAreas) {
        if ("READ".equals(tag)) return readAreas;
        if ("WRITE".equals(tag)) return writeAreas;
        return null;
    }


    /**
     * 通用設備區段匹配與資料派發處理邏輯
     *
     * @param deviceId     設備 ID（如 craneId、gripperId）
     * @param areaType     資料區段型別（B 或 W）
     * @param startAddress 本次 PLC 回傳資料的起始位址
     * @param data         原始資料（byte[]）
     * @param time         快照時間
     * @param areas        該設備註冊的區段（read 或 write）
     * @param handler      對應設備的 Handler 實作
     * @return 若有至少一筆資料成功配對則回傳 true
     */
    private <T extends PlcArea> boolean handleAreas(int deviceId, String areaType, int startAddress,
                                                    byte[] data, Instant time, Iterable<T> areas,
                                                    PollingHandler handler) {

        for (T area : areas) {
            if (matches(areaType, startAddress, data.length, area)) {
                dispatchToHandler(handler, deviceId, areaType, data, startAddress, time);
                return true;
            }
        }
        return false;
    }

    /**
     * 根據資料區段型別分派資料至對應的處理方法（Bit 或 Word）
     */
    private void dispatchToHandler(PollingHandler handler, int deviceId, String areaType,
                                   byte[] data, int startAddress, Instant time) {
        if ("B".equalsIgnoreCase(areaType)) {
            handler.handleBitData(deviceId, data, startAddress, time);
        } else if ("W".equalsIgnoreCase(areaType)) {
            handler.handleWordData(deviceId, data, startAddress, time);
        }
    }

    /**
     * 判斷目前 PLC 回傳資料是否落在設備定義的區段範圍內
     *
     * @param areaType     B 或 W
     * @param startAddress 回傳資料起始地址
     * @param byteLength   回傳資料長度（byte 為單位）
     * @param area         該設備設定的區段範圍
     */
    private boolean matches(String areaType, int startAddress, int byteLength, PlcArea area) {
        if (!area.getType().equalsIgnoreCase(areaType)) return false;
        int begin = area.getAddress();
        int end = begin + area.getLength() - 1;
        int requestedEnd = startAddress + getComponentCount(areaType, byteLength) - 1;
        return startAddress <= end && requestedEnd >= begin;
    }

    /**
     * 根據區段型別推估元件數量：
     * - B（Bit）: 1 byte = 8 bits
     * - W（Word）: 1 word = 2 bytes → byteLength / 2
     */
    private int getComponentCount(String areaType, int byteLength) {
        if ("B".equalsIgnoreCase(areaType)) return byteLength * 8;
        if ("W".equalsIgnoreCase(areaType)) return byteLength / 2;
        return byteLength;
    }

    @Data
    private static class DeviceRoute {
        int id;
        List<DeviceArea> readAreas;
        List<DeviceArea> writeAreas;
        PollingHandler handler;

        DeviceRoute(int id,
                    List<DeviceArea> readAreas,
                    List<DeviceArea> writeAreas,
                    PollingHandler handler) {
            this.id = id;
            this.readAreas = readAreas;
            this.writeAreas = writeAreas;
            this.handler = handler;
        }
    }
}
