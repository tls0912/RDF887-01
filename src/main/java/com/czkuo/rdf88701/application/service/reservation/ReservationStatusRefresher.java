package com.czkuo.rdf88701.application.service.reservation;

import com.czkuo.rdf88701.domain.repository.LocationPointRepository;
import com.czkuo.rdf88701.domain.repository.LocationReservationRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 負責根據 reservation record 更新 location_point 的 is_reserved 欄位
 */
@Service
@RequiredArgsConstructor
public class ReservationStatusRefresher {

    private final LocationReservationRecordRepository reservationRepository;
    private final LocationPointRepository locationPointRepository;

    /**
     * 根據目前預約資料，刷新 is_reserved 狀態
     *
     * @param locationPointId 要刷新的儲位 ID
     */
    public void refresh(Long locationPointId) {
        boolean hasReservation = reservationRepository.findActiveByLocationPoint(locationPointId).isPresent();
        if (hasReservation) {
            locationPointRepository.markReserved(locationPointId);
        } else {
            locationPointRepository.markUnreserved(locationPointId);
        }
    }
}
