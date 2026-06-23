package com.czkuo.rdf88701.application.monitor;

import com.czkuo.rdf88701.application.service.reservation.ReservationStatusRefresher;
import com.czkuo.rdf88701.domain.repository.LocationReservationRecordRepository;
import com.czkuo.rdf88701.infra.entity.LocationReservationRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 預約狀態清理器
 * - 定期將過期預約標記 expired
 * - 並刷新對應儲位的 reserved 狀態
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationStatusCleaner {

    private final LocationReservationRecordRepository reservationRepository;
    private final ReservationStatusRefresher refresher;

    /**
     * 每分鐘執行一次，清理過期預約紀錄
     */
    @Scheduled(fixedDelay = 60_000)
    public void cleanExpiredReservations() {
        LocalDateTime now = LocalDateTime.now();
        List<LocationReservationRecord> expiredList = reservationRepository.findUnmarkedExpired(now);

        for (LocationReservationRecord record : expiredList) {
            reservationRepository.markExpired(record.getId());
            refresher.refresh(record.getLocationPointId());

            log.info("[ReservationCleaner] 標記過期 reservation#{} at location#{}", record.getId(), record.getLocationPointId());
        }
    }
}
