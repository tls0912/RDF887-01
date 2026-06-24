package com.czkuo.rdf88701.application.service.location;

import com.czkuo.rdf88701.common.enums.EntryType;
import com.czkuo.rdf88701.common.enums.ExitType;
import com.czkuo.rdf88701.domain.repository.LocationFlowRepository;
import com.czkuo.rdf88701.domain.repository.LocationPointRepository;
import com.czkuo.rdf88701.domain.repository.LocationReservationRecordRepository;
import com.czkuo.rdf88701.domain.repository.LocationTrackingRepository;
import com.czkuo.rdf88701.infra.entity.LocationFlow;
import com.czkuo.rdf88701.infra.entity.LocationPoint;
import com.czkuo.rdf88701.infra.entity.LocationReservationRecord;
import com.czkuo.rdf88701.infra.entity.LocationTracking;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationAccountingService {

    private final LocationPointRepository locationPointRepository;
    private final LocationFlowRepository locationFlowRepository;
    private final LocationTrackingRepository locationTrackingRepository;
    private final LocationReservationRecordRepository locationReservationRecordRepository;

    /**
     * 建帳（入帳）
     */
    @Transactional
    public void entry(Long containerMainId, Long locationPointId,
                      EntryType entryType, String operator, Long sourceTaskId) {

        // === 檢查儲位是否存在 ===
        LocationPoint location = locationPointRepository.findById(locationPointId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid location_point_id: " + locationPointId));

        // === 檢查儲位狀態 ===
        if ("N".equalsIgnoreCase(location.getEnabled())) {
            throw new IllegalStateException("Location is not enabled: " + locationPointId);
        }
        if ("Y".equalsIgnoreCase(location.getIsLocked())) {
            throw new IllegalStateException("Location is locked: " + locationPointId);
        }
        if ("Y".equalsIgnoreCase(location.getIsOccupied())) {
            throw new IllegalStateException("Location is already occupied: " + locationPointId);
        }

        // === 預約狀態檢查 ===
        Optional<LocationReservationRecord> reservationOpt =
                locationReservationRecordRepository.findActiveByLocationPoint(locationPointId);

        if (reservationOpt.isPresent()) {
            LocationReservationRecord reservation = reservationOpt.get();
            if (!containerMainId.equals(reservation.getContainerMainId())) {
                throw new IllegalStateException("Location is reserved for another container: " + locationPointId);
            }

            // 預約屬於此容器，標記為 fulfilled
            locationReservationRecordRepository.markFulfilled(reservation.getId(), LocalDateTime.now());
        } else {
            // 若無有效預約，仍不允許使用 flagged Y 的儲位
            if ("Y".equalsIgnoreCase(location.getIsReserved())) {
                throw new IllegalStateException("Location is flagged reserved but no valid reservation: " + locationPointId);
            }
        }

        // === 檢查 tracking 是否存在（此容器不可重複入帳） ===
        if (locationTrackingRepository.findByContainerMainId(containerMainId).isPresent()) {
            throw new IllegalStateException("Container already has active tracking: " + containerMainId);
        }

        // === 建立 flow 記錄 ===
        LocationFlow flow = new LocationFlow();
        flow.setContainerMainId(containerMainId);
        flow.setLocationPointId(locationPointId);
        flow.setEntryType(entryType.name());
        flow.setArrivedTime(LocalDateTime.now());
        flow.setEntryOperator(operator);
        flow.setSourceTaskId(sourceTaskId);
        locationFlowRepository.save(flow);

        // === 建立 tracking 記錄 ===
        LocationTracking tracking = new LocationTracking();
        tracking.setContainerMainId(containerMainId);
        tracking.setLocationPointId(locationPointId);
        tracking.setArrivedTime(LocalDateTime.now());
        tracking.setFlowId(flow.getId());
        locationTrackingRepository.save(tracking);

        // === 更新儲位佔用狀態 ===
        locationPointRepository.markOccupied(locationPointId);

        log.info("[建帳] container#{} 入帳於位置#{}，來源={}", containerMainId, locationPointId, entryType.name());
    }

    /**
     * 清帳（出帳）
     */
    @Transactional
    public void exit(Long containerMainId, ExitType exitType, String operator) {
        Optional<LocationTracking> trackingOpt = locationTrackingRepository.findByContainerMainId(containerMainId);
        if (trackingOpt.isEmpty()) {
            throw new IllegalStateException("No tracking found for container: " + containerMainId);
        }

        LocationTracking tracking = trackingOpt.get();
        Long locationPointId = tracking.getLocationPointId();

        // === 更新 flow 離開時間 ===
        locationFlowRepository.markExit(containerMainId, locationPointId, LocalDateTime.now(), exitType, operator);

        // === 刪除 tracking 記錄 ===
        locationTrackingRepository.deleteByContainerMainId(containerMainId);

        // === 更新儲位為空閒 ===
        locationPointRepository.markVacant(locationPointId);

        log.info("[清帳] container#{} 離開位置#{}，原因={}", containerMainId, locationPointId, exitType.name());
    }

    /**
     * 轉帳（離開原位 + 進入新位）
     */
    @Transactional
    public void transfer(Long containerMainId, Long toLocationPointId,
                         EntryType entryType, ExitType exitType,
                         String operator, Long sourceTaskId) {

        // 出帳前需檢查原 tracking 存在
        if (locationTrackingRepository.findByContainerMainId(containerMainId).isEmpty()) {
            throw new IllegalStateException("Cannot transfer: container is not tracked anywhere.");
        }

        // 取得原 tracking
        Long fromLocationPointId = locationTrackingRepository.findByContainerMainId(containerMainId)
                .map(LocationTracking::getLocationPointId)
                .orElseThrow(() -> new IllegalStateException("Failed to retrieve current tracking"));

        // 避免轉帳至相同位置
        if (fromLocationPointId.equals(toLocationPointId)) {
            throw new IllegalStateException("Transfer target location is same as current location.");
        }

        // === 清帳 + 建帳 ===
        this.exit(containerMainId, exitType, operator);
        this.entry(containerMainId, toLocationPointId, entryType, operator, sourceTaskId);
    }
}
