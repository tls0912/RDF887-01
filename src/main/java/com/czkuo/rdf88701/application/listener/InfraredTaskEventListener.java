package com.czkuo.rdf88701.application.listener;

import com.czkuo.rdf88701.application.service.task.InfraredTaskLifecycleService;
import com.czkuo.rdf88701.domain.repository.ContainerAttrRepository;
import com.czkuo.rdf88701.domain.repository.ContainerDataRepository;
import com.czkuo.rdf88701.domain.repository.LocationPointRepository;
import com.czkuo.rdf88701.domain.repository.LocationTrackingRepository;
import com.czkuo.rdf88701.infra.entity.*;
import com.czkuo.rdf88701.infra.event.model.plc.infrared.InfraredTaskCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Infrared 任務完成事件監聽器
 * <p>
 * 職責：
 *  1) 平整性檢查、偏心補償
 *  2) 推估「總層數」→ 同步回填 verified_quantity 與 estimated_quantity（★）
 *  3) 若 cover_layers / product_layers 為 NULL，依 content_kind 反推一次（只補空值，不覆蓋既有值）
 * <p>
 * 不處理：
 *  - 工蓋層數（work_cover_layers）：交由其他業務流程設定
 *  - 已有值的 cover/product：不覆蓋
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InfraredTaskEventListener {

    // ====== 服務與倉儲依賴 ======
    private final InfraredTaskLifecycleService infraredTaskLifecycleService;
    private final ContainerDataRepository containerDataRepository;
    private final LocationPointRepository locationPointRepository;
    private final LocationTrackingRepository locationTrackingRepository;
    private final ContainerAttrRepository containerAttrRepository; // 厚度只從這裡讀

    // ====== 推估參數（厚度改 DB 取，其餘維持常數）======
    /**
     * 每層縫隙 (mm)
     */
    private static final double GAP_PER_LAYER = 0.06;
    /**
     * 層高容忍 (mm)，用於判斷「落在某層高度的容忍區間」
     */
    private static final double LAYER_TOLERANCE = 2.0;
    /**
     * 對角允許差 (mm)，用於平整性檢查
     */
    private static final double CAMERA_TOLERANCE = 2.5;
    /**
     * 中心偏差補償常量 (mm)
     */
    private static final double CENTER_BIAS = 1.0;
    /**
     * 偏心修正門檻的比例（與 CENTER_BIAS 相乘形成實際門檻）
     */
    private static final double CENTER_THRESHOLD_RATIO = 0.8;

    /**
     * 接收並處理紅外線量測完成事件。
     * <p>
     * 事件內預期欄位：
     * <ul>
     *   <li>productHeight1 / productHeight2：整數以 centi-mm（1/100 mm）表示，這裡換算成 mm（double）後使用</li>
     *   <li>productQuantity：PLC 端層數估計，僅作為參考上限</li>
     *   <li>retCode / description：回傳碼與文字描述</li>
     *   <li>task：內含 containerMainId 以供 DB 操作</li>
     * </ul>
     */
    @EventListener
    public void onInfraredTaskCompleted(InfraredTaskCompletedEvent event) {
        // 1) 取得任務與 PLC 回傳
        InfraredTask task = event.getTask();
        int retCode = event.getRetCode();
        String description = event.getDescription();
        long taskId = task.getId();

        // 2) 取得 PLC 測量資料（事件需帶入）
        //    事件提供的高度單位為 centi-mm（1/100 mm），轉成 mm（double）
        double height1 = event.getProductHeight1() / 100.0;   // mm（已扣平台高）
        double height2 = event.getProductHeight2() / 100.0;   // mm（已扣平台高）
        int plcQuantity = event.getProductQuantity();         // 參考值（非絕對值）

        // 3) 從 DB 解析本任務所需的「單片托盤厚度(mm)」（僅從 container_attr）
        final Long containerMainId = task.getContainerMainId();
        final Double trayThickness;
        try {
            trayThickness = resolveTrayThicknessMm(containerMainId);
        } catch (IllegalStateException ex) {
            // 缺設定或格式錯誤 → 不視為最終失敗，改為重試讓上游可補設定
            log.error("[EVENT] 取托盤厚度失敗：taskId={} containerMainId={} err={} → 改為重試",
                    taskId, containerMainId, ex.getMessage());
            infraredTaskLifecycleService.markRetry(task);
            return;
        }

        // ========== 4) 平整性檢查 ==========
        double diff = Math.abs(height1 - height2);
        if (diff > CAMERA_TOLERANCE) {
            // 對角差異過大 → 視為不平整 → 任務失敗
            log.warn("⚠️ 對角差異過大：{} mm（容忍 {} mm）→ 異常：不平整，任務失敗", diff, CAMERA_TOLERANCE);
            log.info(
                    "[EVENT-FAIL] Infrared平整性異常: taskId={} retCode=0x{}({}) 高度1={} 高度2={} 差異={} 容忍={} PLC層數={} 設定: THICKNESS={} GAP={} CAMERA_TOLERANCE={}",
                    taskId, Integer.toHexString(retCode), description,
                    height1, height2, diff, CAMERA_TOLERANCE, plcQuantity,
                    trayThickness, GAP_PER_LAYER, CAMERA_TOLERANCE
            );
            infraredTaskLifecycleService.markFailed(task, "對角差異過大，不平整");
            return;
        }

        // ====== 5) 偏心補償（依兩側差值調整平均高度）======
        double avg = (height1 + height2) / 2.0;
        double correctedHeight = avg;
        double bias1 = height1 - avg;
        double bias2 = height2 - avg;
        // 若兩側都顯著偏高 → 減去一個常量；若單側偏高 → 減去半個常量
        //if (bias1 > CENTER_BIAS * CENTER_THRESHOLD_RATIO && bias2 > CENTER_BIAS * CENTER_THRESHOLD_RATIO) {
        //    correctedHeight -= CENTER_BIAS;
        //} else if (bias1 > CENTER_BIAS * CENTER_THRESHOLD_RATIO || bias2 > CENTER_BIAS * CENTER_THRESHOLD_RATIO) {
        //    correctedHeight -= CENTER_BIAS * 0.5;
        //}

        // ====== 6) 層數推估（以容忍區間找到最貼近的整數層）======
        // 先取理論連續層數（含縫隙），再向上取整以便掃描尋找容忍區間
        double estimatedLayerRaw = (correctedHeight + GAP_PER_LAYER) / (trayThickness + GAP_PER_LAYER);
        int estimatedLayer = (int) Math.ceil(estimatedLayerRaw);

        // 設定一個搜尋上限：取 max(估計層數, PLC 參考層數) + 2 作為安全上限
        int maxLayer = Math.max(estimatedLayer, plcQuantity) + 2;
        int finalLayer = -1;
        String reason = "無法對應任何層數";
        for (int layer = 0; layer <= maxLayer; layer++) {
            // N 層的理論高度：N * 厚度 + (N-1) * 縫隙；0 層高度為 0
            double h = (layer == 0) ? 0.0 : layer * trayThickness + (layer - 1) * GAP_PER_LAYER;
            double lower = h - LAYER_TOLERANCE - (layer - 1) * GAP_PER_LAYER;
            double upper = h + LAYER_TOLERANCE;
            if (correctedHeight >= lower && correctedHeight <= upper) {
                finalLayer = layer;
                reason = "落在第 " + layer + " 層容許區間內";
                break;
            }
        }

        // ====== 7) 主 log ======
        log.info(
                "[EVENT] Infrared 任務完成: taskId={} retCode=0x{}({}) 高度1={} 高度2={} PLC層數={} 推估層數={} 判斷層數={} 原因={} 參數: THICKNESS={} GAP={}",
                taskId, Integer.toHexString(retCode), description,
                height1, height2, plcQuantity, estimatedLayer, finalLayer, reason,
                trayThickness, GAP_PER_LAYER
        );

        // ====== 8) 狀態判斷與後續處理 ======
        switch (retCode) {
            case 0x100 -> {
                // 測量成功
                if (finalLayer != -1) {
                    // 8.1 標記任務完成
                    infraredTaskLifecycleService.markCompleted(task);

                    if (containerMainId == null) {
                        log.warn("[EVENT] 任務#{} 無 container_main_id，略過回填", taskId);
                        return;
                    }

                    try {
                        // ====== 8.2「更新前快照」：讀取現有 container_data，完整記錄目前值 ======
                        Optional<ContainerData> beforeOpt = containerDataRepository.findByContainerMainId(containerMainId);
                        if (beforeOpt.isPresent()) {
                            ContainerData b = beforeOpt.get();
                            log.info("[EVENT][BEFORE] container#{} estimated={} verified={} workCover={} cover={} product={} kind={}",
                                    containerMainId,
                                    nvl(b.getEstimatedQuantity()),
                                    nvl(b.getVerifiedQuantity()),
                                    nvl(b.getWorkCoverLayers()),
                                    nvl(b.getCoverLayers()),
                                    nvl(b.getProductLayers()),
                                    b.getContentKind());
                        } else {
                            log.info("[EVENT][BEFORE] container#{} 無 container_data 紀錄（將以 UPSERT 建立）", containerMainId);
                        }

                        // ====== 8.3 依據當前 location 判斷 content_kind（僅示範部分站點；可依實務擴充） ======
                        String contentKind = "UNKNOWN";
                        Optional<LocationTracking> trackingOpt = locationTrackingRepository.findByContainerMainId(containerMainId);
                        if (trackingOpt.isPresent()) {
                            LocationTracking t = trackingOpt.get();
                            Optional<LocationPoint> lpOpt = locationPointRepository.findById(t.getLocationPointId());
                            if (lpOpt.isPresent()) {
                                String locationName = lpOpt.get().getName();
                                switch (locationName) {
                                    case "Site#12", "Site#14" -> contentKind = "ALL_COVER";
                                    case "Site#24", "Site#35" -> contentKind = "NORMAL_WITH_COVER";
                                    default -> contentKind = "UNKNOWN";
                                }
                                log.info("[EVENT][BEFORE] container#{} location={}", containerMainId, locationName);
                            } else {
                                log.info("[EVENT][BEFORE] container#{} 找不到對應 location_point 記錄", containerMainId);
                            }
                        } else {
                            log.info("[EVENT][BEFORE] container#{} 無 location_tracking 記錄", containerMainId);
                        }

                        // ====== 8.4 回填 estimated/verified（以 finalLayer 為準），同時 patch content_kind ======
                        int layer = Math.max(finalLayer, 0);
                        boolean upOk = containerDataRepository.upsertByContainerMainId(
                                containerMainId,
                                layer,      // estimated_quantity
                                null,       // ocr_text 不動
                                null,       // ocr_text 不動
                                layer,      // verified_quantity
                                contentKind // content_kind（只 patch 該欄位）
                        );
                        log.info("[EVENT] 回填 estimated/verified {}: containerMainId={}, value={}",
                                upOk ? "成功" : "無異動", containerMainId, layer);

                        // ====== 8.5 若 cover/product 為 NULL，依 content_kind 反推一次（只補空值，不覆蓋既有值） ======
                        boolean filled = containerDataRepository.fillLayersByKindIfUnset(containerMainId);
                        log.info("[EVENT] 依 kind 回填層別（只補 NULL）{}: containerMainId={}",
                                filled ? "有異動" : "無異動", containerMainId);

                        // ====== 8.6「更新後快照」：再讀一次並記錄 ======
                        containerDataRepository.findByContainerMainId(containerMainId).ifPresent(a -> {
                            log.info("[EVENT][AFTER] container#{} estimated={} verified={} workCover={} cover={} product={} kind={}",
                                    containerMainId,
                                    nvl(a.getEstimatedQuantity()),
                                    nvl(a.getVerifiedQuantity()),
                                    nvl(a.getWorkCoverLayers()),
                                    nvl(a.getCoverLayers()),
                                    nvl(a.getProductLayers()),
                                    a.getContentKind());
                        });

                    } catch (Exception e) {
                        // DB 操作出錯 → 記錄錯誤但不丟出，避免事件處理線程中斷
                        log.error("[EVENT] 回填 estimated/verified 或層別失敗: taskId={}, containerMainId={}, err={}",
                                taskId, containerMainId, e.getMessage(), e);
                    }
                } else {
                    // 雖成功量測，但無法對應層數 → 任務失敗
                    infraredTaskLifecycleService.markFailed(task, "推估層數失敗: " + reason);
                }
            }
            case 0x800 -> {
                // 0x800：測量中斷（依你協議）
                infraredTaskLifecycleService.markFailed(task, "任務中斷");
            }
            case 0xF00 -> {
                // 0xF00：測量失敗（依你協議）
                infraredTaskLifecycleService.markFailed(task, "任務異常");
            }
            default -> {
                // 其他未知/未處理回傳碼 → 進行重試
                log.warn("[EVENT] Infrared 任務#{} 未處理的回傳碼：0x{}", taskId, Integer.toHexString(retCode));
                infraredTaskLifecycleService.markRetry(task);
            }
        }
    }

    // =====================================================================
    //                             私有工具方法
    // =====================================================================

    /**
     * 從 container_attr 取單片托盤厚度（mm）。
     * <ul>
     *   <li>key 固定為：<code>tray_thickness_mm</code></li>
     *   <li>容錯解析允許：<code>5.62</code>、<code>5,62</code>、<code>5.62mm</code> 等</li>
     *   <li>取不到或格式不正確 → 拋 {@link IllegalStateException}，由上層改為重試</li>
     * </ul>
     *
     * @param containerMainId 目標容器主鍵
     * @return 厚度（mm，正數）
     */
    private Double resolveTrayThicknessMm(Long containerMainId) {
        if (containerMainId == null) {
            throw new IllegalStateException("container_main_id 為空");
        }

        // 使用自定義的接口：findOne(container_main_id, attr_key)
        Optional<ContainerAttr> opt = containerAttrRepository.findOne(containerMainId, "tray_thickness_mm");

        // 依實體欄位名稱取值：這裡假設 getter 為 getAttrValue()；若你的欄位是 value，請改成 getValue()
        String raw = opt.map(ContainerAttr::getAttrValue).orElse(null);

        Double v = parseDecimal(raw);
        if (v != null && v > 0) {
            return v;
        }

        throw new IllegalStateException("缺少或格式錯誤的 container_attr『tray_thickness_mm』");
    }

    /**
     * 寬鬆的數值解析：允許 "5.62", "5,62", "5.62mm" 等；回傳正數 Double，否則回 null。
     *
     * @param raw 原始字串
     * @return 正數 Double 或 null
     */
    private static Double parseDecimal(String raw) {
        if (raw == null) return null;

        // 去除非數字/小數點/逗號/負號的字元（如單位 mm）
        String n = raw.trim().replaceAll("[^0-9,\\.\\-]", "");
        if (n.isEmpty()) return null;

        // 小數分隔規則：
        //   - 同時有 '.' 與 ',' → 視 ',' 為千分位，移除 ','
        //   - 只有 ',' → 視為小數點，替換為 '.'
        if (n.contains(".") && n.contains(",")) {
            n = n.replace(",", "");
        } else if (n.contains(",") && !n.contains(".")) {
            n = n.replace(',', '.');
        }

        try {
            double v = Double.parseDouble(n);
            return v >= 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Null-to-zero：避免 Integer 直接輸出 NPE 或 null 字樣。
     */
    private static int nvl(Integer v) {
        return v == null ? 0 : v;
    }
}
