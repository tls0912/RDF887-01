package com.czkuo.rdf88701.application.service.cover;


import com.czkuo.rdf88701.common.enums.cover.CoverLane;
import com.czkuo.rdf88701.domain.repository.ContainerDataRepository;
import com.czkuo.rdf88701.domain.repository.LocationTrackingRepository;
import com.czkuo.rdf88701.domain.repository.RobotR029TaskRepository;
import com.czkuo.rdf88701.infra.entity.ContainerData;
import com.czkuo.rdf88701.infra.entity.RobotR029Task;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

/**
 * CoverLaneDecisionService
 * -----------------------------------------------------------------------------
 * 抽出「上蓋區 lane 的路徑判斷」供 TR4/TR5/OCR2 共用，避免規則漂移。
 *
 * 共同規則（TR4/TR5 皆相同，只是站點/transfer 不同）：
 * 1) 任務存在且 mismatch        → RECALL（pool → staging）
 * 2) 任務存在且無 mismatch      → SUPPLY（staging → pool）
 * 3) 沒任務                    → RECALL（把 pool 的蓋退回 staging，再由 monitor 入庫）
 * 4) 有任務但 trayType 為空     → NONE（保守不搬）
 *
 * 另外提供「是否需要 OCR2 避讓」判斷（依你定義的更精準條件）：
 * - 供蓋路徑: 產品在 staging 或 transfer 時，OCR2 不能卡在 pool
 * - 回收路徑: 產品在 pool 或 transfer 時，OCR2 不能卡在 pool
 *
 * 其中：
 * - SUB lane: staging=Site#11, pool=Site#12, transfer=Transfer#4
 * - MAIN lane: staging=Site#13, pool=Site#14, transfer=Transfer#5
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoverLaneDecisionService {

    private final RobotR029TaskRepository r029TaskRepository;
    private final CoverZoneService coverZoneService;
    private final LocationTrackingRepository locationTrackingRepository;
    private final ContainerDataRepository containerDataRepository;

    public enum TrMode { SUPPLY, RECALL, NONE }

    /**
     * 計算 lane 本輪路徑（完全對齊 TR4/TR5 的 modeSupply/modeRecall 邏輯）
     *
     * @param lane     CoverLane.SUB / CoverLane.MAIN
     * @param laneName "SUB" / "MAIN"（對應 RobotR029Task.lane）
     */
    public TrMode resolveMode(CoverLane lane, String laneName) {
        boolean hasTask;
        try {
            hasTask = r029TaskRepository.countProcessingByLane(laneName) > 0;
        } catch (Exception e) {
            // 例外時保守：回收（你也強調沒任務要回倉，RECALL 較安全）
            log.warn("[CoverLaneDecision] countProcessingByLane({}) failed, fallback RECALL: {}",
                    laneName, e.getMessage());
            return TrMode.RECALL;
        }

        if (!hasTask) {
            // 沒任務：TR4/TR5 都是回收（pool → staging）
            return TrMode.RECALL;
        }

        Optional<RobotR029Task> taskOpt;
        try {
            taskOpt = r029TaskRepository.findFirstProcessingByLane(laneName);
        } catch (Exception e) {
            log.warn("[CoverLaneDecision] findFirstProcessingByLane({}) failed, fallback RECALL: {}",
                    laneName, e.getMessage());
            return TrMode.RECALL;
        }

        if (taskOpt.isEmpty()) {
            // count>0 但找不到 processing：保守回收
            return TrMode.RECALL;
        }

        String trayTypeUpper = upper(taskOpt.get().getTrayType());
        if (trayTypeUpper == null) {
            // 有任務但 trayType 空：TR4/TR5 都是保守不搬
            return TrMode.NONE;
        }

        // 有任務且 trayType 明確：看 mismatch
        var snap = coverZoneService.snapshot(lane);
        boolean mismatch = coverZoneService.hasMismatchCover(snap, trayTypeUpper);

        // mismatch=true → RECALL； mismatch=false → SUPPLY
        return mismatch ? TrMode.RECALL : TrMode.SUPPLY;
    }

    // -------------------------------------------------------------------------
    // Lane 專用便利方法（你用起來會更直覺）
    // -------------------------------------------------------------------------

    /** TR4 SUB lane 的路徑判斷（等價於 resolveMode(CoverLane.SUB, "SUB")） */
    public TrMode resolveModeSub() {
        return resolveMode(CoverLane.SUB, "SUB");
    }

    /** TR5 MAIN lane 的路徑判斷（等價於 resolveMode(CoverLane.MAIN, "MAIN")） */
    public TrMode resolveModeMain() {
        return resolveMode(CoverLane.MAIN, "MAIN");
    }

    /**
     * 依你定義的條件判斷「是否需要讓 OCR2 離開 pool 站位」（你會在 OCR2 monitor 裡用）
     *
     * 說明：
     * - 這個方法不檢查 OCR2 目前在哪一站（因為 OCR2 monitor 自己已經知道自己卡在 12/14）
     * - 這個方法只回答：在此 lane 狀態下，若 OCR2 卡在 pool，是否應該避讓？
     */
    public boolean shouldYieldOcrFromPoolForLane(CoverLane lane) {
        LaneSpec spec = LaneSpec.of(lane);
        if (spec == null) return false;

        // 1) 先決定這輪路徑
        TrMode mode = resolveMode(lane, spec.laneName);

        // 2) 依路徑 + 你指定的「產品所在位置」決定是否需要避讓
        //    供蓋路徑: staging 或 transfer 有產品 → OCR2 不能卡 pool
        //    回收路徑: pool 或 transfer 有產品 → OCR2 不能卡 pool
        boolean stagingHas  = locationTrackingRepository.hasContainerAtLocationName(spec.stagingSite);
        boolean poolHas     = locationTrackingRepository.hasContainerAtLocationName(spec.poolSite);
        boolean transferHas = locationTrackingRepository.findContainerOnTransfer(spec.transferId).isPresent();

        if (mode == TrMode.SUPPLY) {
            return stagingHas || transferHas;
        }
        if (mode == TrMode.RECALL) {
            return poolHas || transferHas;
        }
        // NONE：trayType 空，TR4/TR5 保守不搬 → 不要求 OCR2 避讓
        return false;
    }

    /**
     * 取得 trayTypeUpper（trim + uppercase；空字串→null）
     * - 你若在 log 想印出當前 trayType，可以用這個。
     */
    public String resolveTrayTypeUpper(String laneName) {
        try {
            return r029TaskRepository.findFirstProcessingByLane(laneName)
                    .map(RobotR029Task::getTrayType)
                    .map(this::upper)
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    // =========================================================================
    // 「還有可補的」判斷（TR5/TR4 用來阻擋 RECALL）
    // =========================================================================

    /**
     * MAIN lane：pool(Site#14) 的蓋是否仍「可能被用來補位」？
     *
     * 語意：
     * - 若 Site#27 為無上蓋(cov=0) 且 OCR(27) 與 OCR(14) 成對相同
     *   → 表示 Site#14 的蓋還能補 → Transfer#5 不應 RECALL 把蓋載走
     *
     * 你後續若要把 TR8@V12 也納入（像 OCR2 monitor 那樣），建議用另一個方法/參數擴充，
     * 但先把「Site#27」這條最核心的補位依賴建立起來（避免 TR5 誤回收）。
     */
    public boolean hasPendingCoverSupplyMain() {
        return hasPendingCoverSupply(
                "Site#14",          // pool
                new String[]{"Site#27"} // demand side(s)
        );
    }

    /**
     * SUB lane：pool(Site#12) 的蓋是否仍「可能被用來補位」？
     *
     * 語意：
     * - 若 Site#26 或 Site#37 為無上蓋(cov=0) 且 OCR(26/37) 與 OCR(12) 成對相同
     *   → 表示 Site#12 的蓋還能補 → Transfer#4 不應 RECALL 把蓋載走
     */
    public boolean hasPendingCoverSupplySub() {
        return hasPendingCoverSupply(
                "Site#12",                 // pool
                new String[]{"Site#26", "Site#37"} // demand side(s)
        );
    }

    /**
     * 通用：pool 的容器 OCR 與任一 demandSide 容器 OCR 成對相同，且 demandSide 為無蓋 → true
     */
    private boolean hasPendingCoverSupply(String poolSite, String[] demandSites) {

        Optional<Long> poolCmOpt = locationTrackingRepository.findContainerAtLocationName(poolSite);
        if (poolCmOpt.isEmpty()) return false;

        Long poolCmId = poolCmOpt.get();
        OcrPair poolOcr = getOcrPair(poolCmId);
        if (poolOcr.isBlank()) return false;

        for (String demandSite : demandSites) {
            Optional<Long> demandCmOpt = locationTrackingRepository.findContainerAtLocationName(demandSite);
            if (demandCmOpt.isEmpty()) continue;

            Long demandCmId = demandCmOpt.get();

            // 只有「對側為無上蓋」才視為「可補」
            if (!isNoCover(demandCmId)) continue;

            OcrPair demandOcr = getOcrPair(demandCmId);
            if (demandOcr.isBlank()) continue;

            if (equalsOcrAligned(poolOcr, demandOcr)) {
                //log.debug("[CoverLaneDecision] pending cover supply: pool={} cm#{} matches demand={} cm#{} (demand cov=0)",
//                        poolSite, poolCmId, demandSite, demandCmId);
                return true;
            }
        }
        return false;
    }

    /** 只在 coverLayers 明確為 0 時回 true；null/其他值皆視為不確定或有上蓋 */
    private boolean isNoCover(Long cmId) {
        return containerDataRepository.findByContainerMainId(cmId)
                .map(ContainerData::getCoverLayers)
                .map(v -> v != null && v == 0)
                .orElse(false);
    }

    /** 取 OCR 兩欄（trim；空字串→null） */
    private OcrPair getOcrPair(Long containerMainId) {
        return containerDataRepository.findByContainerMainId(containerMainId)
                .map(cd -> new OcrPair(cd.getOcrText1(), cd.getOcrText2()))
                .orElse(new OcrPair(null, null));
    }

    /**
     * 對齊比：1↔1、2↔2。
     * - 若某欄兩邊都有值但不同 → false
     * - 否則只要任一對齊欄位相等 → true
     */
    private boolean equalsOcrAligned(OcrPair a, OcrPair b) {
        boolean can1 = a.has1() && b.has1();
        boolean can2 = a.has2() && b.has2();
        if (can1 && !a.t1.equalsIgnoreCase(b.t1)) return false;
        if (can2 && !a.t2.equalsIgnoreCase(b.t2)) return false;
        boolean match1 = can1 && a.t1.equalsIgnoreCase(b.t1);
        boolean match2 = can2 && a.t2.equalsIgnoreCase(b.t2);
        return match1 || match2;
    }

    /** 兩欄 OCR 值（皆已 trim；空字串→null） */
    private static final class OcrPair {
        final String t1; final String t2;
        OcrPair(String a, String b) { this.t1 = normalize(a); this.t2 = normalize(b); }
        boolean has1() { return t1 != null; }
        boolean has2() { return t2 != null; }
        boolean isBlank() { return !has1() && !has2(); }
    }
    private static String normalize(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    // -------------------------------------------------------------------------
    // 內部：lane 對應表（SUB / MAIN）
    // -------------------------------------------------------------------------
    private static final class LaneSpec {
        final CoverLane lane;
        final String laneName;
        final String stagingSite;
        final String poolSite;
        final long transferId;

        private LaneSpec(CoverLane lane, String laneName, String stagingSite, String poolSite, long transferId) {
            this.lane = lane;
            this.laneName = laneName;
            this.stagingSite = stagingSite;
            this.poolSite = poolSite;
            this.transferId = transferId;
        }

        static LaneSpec of(CoverLane lane) {
            if (lane == CoverLane.SUB) {
                // TR4：11/12/TR4
                return new LaneSpec(CoverLane.SUB, "SUB", "Site#11", "Site#12", 4L);
            }
            if (lane == CoverLane.MAIN) {
                // TR5：13/14/TR5
                return new LaneSpec(CoverLane.MAIN, "MAIN", "Site#13", "Site#14", 5L);
            }
            return null;
        }
    }

    private String upper(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t.toUpperCase(Locale.ROOT);
    }
}
