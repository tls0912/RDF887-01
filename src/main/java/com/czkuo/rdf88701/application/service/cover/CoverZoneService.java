package com.czkuo.rdf88701.application.service.cover;

import com.czkuo.rdf88701.common.enums.cover.CoverLane;
import com.czkuo.rdf88701.domain.repository.ContainerMainRepository;
import com.czkuo.rdf88701.domain.repository.LocationTrackingRepository;
import com.czkuo.rdf88701.infra.entity.ContainerMain;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class CoverZoneService {

    private final LocationTrackingRepository locationTrackingRepository;
    private final ContainerMainRepository containerMainRepository;
    private final Map<CoverLane, Integer> idleCounter = new ConcurrentHashMap<>();
    private static final int IDLE_RECLAIM_THRESHOLD = 1;
    /**
     * 三格快照：pool / staging / transfer 上的 ContainerMain
     */
    public record GroupSnapshot(
            ContainerMain pool,
            ContainerMain staging,
            ContainerMain transfer
    ) {}

    private String upper(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t.toUpperCase(Locale.ROOT);
    }

    /** 取得某 lane 三格的 ContainerMain 快照（找不到就為 null） */
    public GroupSnapshot snapshot(CoverLane lane) {
        if (lane == null) return new GroupSnapshot(null, null, null);

        ContainerMain pool = findCmAtSite(lane.getPoolSite()).orElse(null);
        ContainerMain staging = findCmAtSite(lane.getStagingSite()).orElse(null);
        ContainerMain transfer = findCmOnTransfer(lane.getTransferId()).orElse(null);

        return new GroupSnapshot(pool, staging, transfer);
    }

    private Optional<ContainerMain> findCmAtSite(String siteName) {
        return locationTrackingRepository.findContainerAtLocationName(siteName)
                .flatMap(containerMainRepository::findById);
    }

    private Optional<ContainerMain> findCmOnTransfer(Long transferId) {
        if (transferId == null) return Optional.empty();
        return locationTrackingRepository.findContainerOnTransfer(transferId)
                .flatMap(containerMainRepository::findById);
    }

    public boolean hasAnyCover(GroupSnapshot g) {
        return g.pool() != null || g.staging() != null || g.transfer() != null;
    }

    /** 三格中是否存在「part_no == trayType」的公蓋 */
    public boolean hasMatchingCover(GroupSnapshot g, String trayType) {
        String key = upper(trayType);
        if (key == null) return false;
        return isPartNoEquals(g.pool(), key)
                || isPartNoEquals(g.staging(), key)
                || isPartNoEquals(g.transfer(), key);
    }

    /**
     * 三格中是否存在「料號不符」的公蓋：
     * - 有 trayType：只要某格的 part_no != trayType → true
     * - 無 trayType（沒任務）：只要有任何公蓋 → 都算「不需要」→ true
     */
    public boolean hasMismatchCover(GroupSnapshot g, String trayType) {
        String key = upper(trayType);
        if (!hasAnyCover(g)) return false;

        if (key == null) {
            // 沒任務 → 有蓋就是不需要
            return true;
        }

        return isPartNoMismatch(g.pool(), key)
                || isPartNoMismatch(g.staging(), key)
                || isPartNoMismatch(g.transfer(), key);
    }

    /** staging 上是否有「應該回收」的蓋（有任務時：料號不符；沒任務時：任何蓋） */
    public Optional<Long> findEvictCandidateAtStaging(CoverLane lane, String trayType) {
        if (lane == null) return Optional.empty();
        Optional<Long> containerOpt =
                locationTrackingRepository.findContainerAtLocationName(lane.getStagingSite());
        if (containerOpt.isEmpty()) {
            idleCounter.remove(lane);
            return Optional.empty();
        }
        String key = upper(trayType);
        return containerOpt
                .flatMap(cid -> containerMainRepository.findById(cid).map(cm -> {
                    String p = upper(cm.getPartNo());
                    if (key == null) {
                        int count = idleCounter.merge(lane, 1, Integer::sum);
                        // 延遲 x 次 polling 才回收
                        if (count == 1) {
                            log.info("[Cover] Lane {} enter idle reclaim countdown", lane);
                        }
                        if (count < IDLE_RECLAIM_THRESHOLD) {
                            return null;
                        }
                        // 沒任務 → staging 有蓋就回收
                        idleCounter.remove(lane);
                        log.info("[Cover] Lane {} idle timeout reclaim cover cid={}", lane, cid);
                        return cid;
                    }
                    // 有任務 -> reset idle
                    idleCounter.remove(lane);
                    // 有任務 → staging 料號不符才回收
                    if (p == null || !p.equals(key)) {
                        return cid;
                    }
                    return null;
                }))
                .filter(id -> id != null);
    }

    /** 找出這三格中的所有公蓋 containerId（沒任務且要淨空時用） */
    public List<Long> findAllCoverIds(CoverLane lane) {
        List<Long> result = new ArrayList<>();
        if (lane == null) return result;

        locationTrackingRepository.findContainerAtLocationName(lane.getPoolSite())
                .ifPresent(result::add);
        locationTrackingRepository.findContainerAtLocationName(lane.getStagingSite())
                .ifPresent(result::add);
        if (lane.getTransferId() != null) {
            locationTrackingRepository.findContainerOnTransfer(lane.getTransferId())
                    .ifPresent(result::add);
        }
        return result;
    }

    // ===== private helpers =====

    private boolean isPartNoEquals(ContainerMain cm, String keyUpper) {
        if (cm == null) return false;
        String p = upper(cm.getPartNo());
        if (p == null) return false;
        return p.equals(keyUpper);
    }

    private boolean isPartNoMismatch(ContainerMain cm, String keyUpper) {
        if (cm == null) return false;
        String p = upper(cm.getPartNo());
        if (p == null) return true;         // 沒料號也算不符
        return !p.equals(keyUpper);
    }
}
