package com.czkuo.rdf88701.application.service.reservation;

import com.czkuo.rdf88701.domain.repository.LocationPointRepository;
import com.czkuo.rdf88701.domain.repository.LocationReservationRecordRepository;
import com.czkuo.rdf88701.infra.entity.LocationPoint;
import com.czkuo.rdf88701.infra.entity.LocationReservationRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

/**
 * ReservationOrchestrator
 * --------------------------------------------------------------------
 * 「儲位預約」的統一協調服務，負責：
 *  1) 入庫前先從可用儲位中挑一格並立即建立「預約」（避免目標位被其他流程搶用）
 *  2) 出庫時「預留原儲位」（避免被其他流程拿去當目標位）
 *  3) TO 成功後，若該目標位曾被預約，將其標記為 fulfilled 並刷新 reserved 旗標
 *  4) 任務失敗/取消時，若該位曾被預約，立即取消並刷新 reserved 旗標
 *
 * 設計重點：
 *  - 所有寫入流程皆以 @Transactional 包裹，確保「選位 → 建預約 → 刷 reserved 旗標」的原子性。
 *  - 可用儲位篩選由 LocationPointMapper SQL 保障：排除 is_locked / is_occupied，
 *    並且排除尚有效的 reservation（fulfilled=0, cancelled=0, expired=0，且過期時間未到）。
 *  - TTL：透過 expiredTime 控制預約有效期，逾時由外部排程（ReservationStatusCleaner）統一標記 expired。
 *
 * 執行緒安全與競態：
 *  - 以資料庫層級（篩選條件 + 立即寫入 reservation）降低同輪搶位可能性。
 *  - 如需更嚴格，可在 Mapper 加入「悲觀鎖」或以唯一索引進一步約束（視你的 DB 與負載情境調整）。
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationOrchestrator {

    private final LocationPointRepository locationPointRepository;
    private final LocationReservationRecordRepository reservationRepository;
    private final ReservationStatusRefresher refresher;

    /**
     * 入庫專用：從「可用儲位」挑出一格並立即建立預約。
     *
     * @param containerId    即將入庫的容器主鍵 ID（追蹤用）
     * @param exclude        需排除的儲位 ID 集合（不為 null；空集合代表不排除）
     * @param ttlSeconds     預約有效秒數（<=0 表示無期限；建議 300~900）
     * @param reservedBy     預約來源（如 "AUTO_INBOUND", "UI_MANUAL"）
     * @param reservedReason 預約原因描述（可放場景名稱/任務代碼）
     * @return 成功：回傳建立好的預約紀錄；失敗/無可用儲位：empty
     */
    @Transactional
    public Optional<LocationReservationRecord> reserveForInbound(
            Long containerId, Set<Long> exclude, long ttlSeconds, String reservedBy, String reservedReason) {

        // 1) 從可用儲位中挑一格（SQL 已排除有效 reservation / 鎖位 / 佔用等）
        Optional<LocationPoint> slotOpt = locationPointRepository
                .findAvailableStorageWithoutReservationExcludingOne(exclude);

        if (slotOpt.isEmpty()) return Optional.empty();

        Long locationId = slotOpt.get().getId();

        // 2) 寫入預約主檔（設定 TTL 與初始狀態）
        LocationReservationRecord r = new LocationReservationRecord();
        r.setContainerMainId(containerId);
        r.setLocationPointId(locationId);
        r.setReservedBy(reservedBy);
        r.setReservedReason(reservedReason);
        r.setReservedTime(LocalDateTime.now());
        r.setExpiredTime(ttlSeconds > 0 ? LocalDateTime.now().plusSeconds(ttlSeconds) : null); // <=0 代表永不過期
        r.setFulfilled(Boolean.FALSE);
        r.setCancelled(Boolean.FALSE);
        r.setExpired(Boolean.FALSE);

        boolean ok = reservationRepository.save(r);
        if (!ok) return Optional.empty();

        // 3) 立刻刷新 is_reserved（使 UI/對外查詢即時顯示此位已被預留）
        refresher.refresh(locationId);

        log.info("[Reservation] INBOUND reserved loc#{} for container#{} (rid={})", locationId, containerId, r.getId());
        return Optional.of(r);
    }

    /**
     * 出庫專用：預留「來源儲位」本身，避免短時間內被其他流程搶去當目標位。
     *
     * @return 成功：回傳建立（或沿用）的預約紀錄；來源位為 null 時回 empty
     */
    @Transactional
    public Optional<LocationReservationRecord> reserveOriginForOutbound(
            Long containerId, Long sourceLocationId, long ttlSeconds, String reservedBy, String reservedReason) {

        if (sourceLocationId == null) return Optional.empty();

        // 1) 若已存在有效預約，直接回傳（避免重複建立）
        var existed = reservationRepository.findActiveByLocationPoint(sourceLocationId);
        if (existed.isPresent()) return existed;

        // 2) 建立來源位的預約（以 TTL 控制持有時間）
        LocationReservationRecord r = new LocationReservationRecord();
        r.setContainerMainId(containerId);
        r.setLocationPointId(sourceLocationId);
        r.setReservedBy(reservedBy);
        r.setReservedReason(reservedReason);
        r.setReservedTime(LocalDateTime.now());
        r.setExpiredTime(ttlSeconds > 0 ? LocalDateTime.now().plusSeconds(ttlSeconds) : null);
        r.setFulfilled(Boolean.FALSE);
        r.setCancelled(Boolean.FALSE);
        r.setExpired(Boolean.FALSE);

        if (!reservationRepository.save(r)) return Optional.empty();

        // 3) 刷新旗標
        refresher.refresh(sourceLocationId);

        log.info("[Reservation] OUTBOUND origin reserved loc#{} for container#{} (rid={})",
                sourceLocationId, containerId, r.getId());
        return Optional.of(r);
    }

    /**
     * 出庫／回退流程建議改呼叫本方法：
     * - 若來源位已有有效預約 → 直接「延長/改寫」有效期（或設永不過期）
     * - 若沒有 → 建立一筆新的來源位預約
     *
     * 使用場景：
     * - FROM 成功後（物已離位、尚未 TO 成功），為避免來源位在搬運途中被他人搶用，續命 TTL。
     * - 0x60 要回原位時，確保來源位持續被保護直到回放完成。
     *
     * 需求：LocationReservationRecordRepository 需提供 updateExpiredTime(Long id, LocalDateTime newExpiredTime)
     *
     * @param ttlSeconds <=0 表示永不過期（expiredTime=null）；>0 表示延長到 now+ttlSeconds
     */
    @Transactional
    public Optional<LocationReservationRecord> reserveOrExtendOriginForOutbound(
            Long containerId, Long sourceLocationId, long ttlSeconds, String reservedBy, String reservedReason) {

        if (sourceLocationId == null) return Optional.empty();

        var active = reservationRepository.findActiveByLocationPoint(sourceLocationId);
        LocalDateTime newExpiry = (ttlSeconds > 0) ? LocalDateTime.now().plusSeconds(ttlSeconds) : null;

        if (active.isPresent()) {
            // 已有有效預約 → 續命（或設為永不過期）
            LocationReservationRecord r = active.get();
            reservationRepository.updateExpiredTime(r.getId(), newExpiry);
            refresher.refresh(sourceLocationId);
            log.info("[Reservation] ORIGIN extend TTL rid={} loc#{} -> {}",
                    r.getId(), sourceLocationId, (newExpiry == null ? "PERMANENT" : newExpiry));
            // 回傳更新後的物件（可視需要補 get-by-id 取最新）
            r.setExpiredTime(newExpiry);
            return Optional.of(r);
        }

        // 無有效預約 → 建立新預約
        LocationReservationRecord r = new LocationReservationRecord();
        r.setContainerMainId(containerId);
        r.setLocationPointId(sourceLocationId);
        r.setReservedBy(reservedBy);
        r.setReservedReason(reservedReason);
        r.setReservedTime(LocalDateTime.now());
        r.setExpiredTime(newExpiry);
        r.setFulfilled(Boolean.FALSE);
        r.setCancelled(Boolean.FALSE);
        r.setExpired(Boolean.FALSE);

        boolean ok = reservationRepository.save(r);
        if (!ok) return Optional.empty();

        refresher.refresh(sourceLocationId);
        log.info("[Reservation] ORIGIN new reserve loc#{} for container#{} (rid={}, ttl={})",
                sourceLocationId, containerId, r.getId(), ttlSeconds);
        return Optional.of(r);
    }

    // 在 ReservationOrchestrator 補這段方法

    /**
     * 精準預約：「指定儲位」若仍可用且無有效預約，則建立預約並刷新旗標。
     * - 會檢查：enabled='Y'、location_type='STORAGE'、is_locked='N'、is_occupied='N'、is_reserved='N'
     * - 也會檢查是否已有有效 reservation（fulfilled/cancelled/expired 皆為 false，且 expired_time 未到）
     * - ttlSeconds <= 0 表示永不過期（expired_time = NULL）
     */
    @Transactional
    public Optional<LocationReservationRecord> reserveExactIfAvailable(
            Long containerId, Long locationPointId, long ttlSeconds, String reservedBy, String reservedReason) {

        if (locationPointId == null) return Optional.empty();

        // 已有有效預約就不重複建
        var existed = reservationRepository.findActiveByLocationPoint(locationPointId);
        if (existed.isPresent()) return Optional.empty();

        // 讀取儲位現況（基本 guard，避免對「不可用」位置建立預約）
        var lpOpt = locationPointRepository.findById(locationPointId);
        if (lpOpt.isEmpty()) return Optional.empty();

        var lp = lpOpt.get();
        boolean ok = "Y".equals(lp.getEnabled())
                && "STORAGE".equalsIgnoreCase(lp.getLocationType())
                && "N".equals(lp.getIsLocked())
                && "N".equals(lp.getIsOccupied())
                && "N".equals(lp.getIsReserved());

        if (!ok) return Optional.empty();

        // 建立預約
        LocationReservationRecord r = new LocationReservationRecord();
        r.setContainerMainId(containerId);
        r.setLocationPointId(locationPointId);
        r.setReservedBy(reservedBy);
        r.setReservedReason(reservedReason);
        r.setReservedTime(LocalDateTime.now());
        r.setExpiredTime(ttlSeconds > 0 ? LocalDateTime.now().plusSeconds(ttlSeconds) : null);
        r.setFulfilled(Boolean.FALSE);
        r.setCancelled(Boolean.FALSE);
        r.setExpired(Boolean.FALSE);

        if (!reservationRepository.save(r)) return Optional.empty();

        refresher.refresh(locationPointId);
        log.info("[Reservation] EXACT reserved loc#{} for container#{} (rid={})",
                locationPointId, containerId, r.getId());

        return Optional.of(r);
    }


    /**
     * 一般化的「續命 TTL」工具：若某 location 尚有「有效預約」，則把 expiredTime 往後延長。
     * - 若 ttlSeconds <= 0：視為永不過期（expiredTime=null）
     * - 若不存在有效預約：不動作
     */
    @Transactional
    public void extendIfActive(Long locationPointId, long ttlSeconds, String reason) {
        reservationRepository.findActiveByLocationPoint(locationPointId).ifPresent(r -> {
            LocalDateTime newExpiry = (ttlSeconds > 0) ? LocalDateTime.now().plusSeconds(ttlSeconds) : null;
            reservationRepository.updateExpiredTime(r.getId(), newExpiry);
            refresher.refresh(locationPointId);
            log.info("[Reservation] extend TTL rid={} at loc#{} -> {} ({})",
                    r.getId(), locationPointId, (newExpiry == null ? "PERMANENT" : newExpiry), reason);
        });
    }

    /**
     * TO 成功後：若目標位存在尚未結束的預約，將其標記為 fulfilled。
     *
     * 使用時機：
     *  - 天車 TO 段 retCode 成功，且帳籍已入帳到目標位後呼叫
     */
    @Transactional
    public void fulfillIfExists(Long locationPointId) {
        reservationRepository.findActiveByLocationPoint(locationPointId).ifPresent(r -> {
            reservationRepository.markFulfilled(r.getId(), LocalDateTime.now());
            refresher.refresh(locationPointId);
            log.info("[Reservation] fulfilled rid={} at loc#{}", r.getId(), locationPointId);
        });
    }

    /**
     * 任務失敗 / 取消：若該儲位存在尚未結束的預約，將其標記為 cancelled。
     *
     * 使用時機：
     *  - 天車 TO 失敗（放置失敗）、任務被取消、或策略切換需要釋放預約
     */
    @Transactional
    public void cancelIfExists(Long locationPointId, String reason) {
        reservationRepository.findActiveByLocationPoint(locationPointId).ifPresent(r -> {
            reservationRepository.markCancelled(r.getId(), LocalDateTime.now(), reason);
            refresher.refresh(locationPointId);
            log.info("[Reservation] cancelled rid={} at loc#{} ({})", r.getId(), locationPointId, reason);
        });
    }
}
