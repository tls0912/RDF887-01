package com.czkuo.rdf88701.application.monitor;

import com.czkuo.rdf88701.application.service.PlcAccessService;
import com.czkuo.rdf88701.domain.plc.state.site.SiteDeviceStatus;
import com.czkuo.rdf88701.domain.repository.ContainerAttrRepository;
import com.czkuo.rdf88701.domain.repository.ContainerDataRepository;
import com.czkuo.rdf88701.domain.repository.ContainerMainRepository;
import com.czkuo.rdf88701.domain.repository.LocationTrackingRepository;
import com.czkuo.rdf88701.infra.cache.SiteStatusCache;
import com.czkuo.rdf88701.infra.entity.ContainerAttr;
import com.czkuo.rdf88701.infra.entity.ContainerMain;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * TopCoverInquiryMonitor
 * - PLC 詢問是否補公蓋：B08DF(Request)
 * - 我方回覆結果到 W0883：0=不用蓋、1=要蓋；W0884=層數、W0885=厚度(mm)
 * - 回覆後拉起 B02DF(Ack)；待 PLC 清除 Request 後我方也清除 Ack
 *
 * 來源：以 Site#38 的現場帳為主（PLC → SiteStatusCache），DB 只做補助比對。
 *
 * 時序：
 *   PLC:   B08DF ↑ ------------------------- ↓
 *   Host:        W0883=0/1, W0884, W0885, B02DF ↑         B02DF ↓
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TopCoverInquiryMonitor {

    private final PlcAccessService plc;
    private final SiteStatusCache siteStatusCache;
    private final LocationTrackingRepository locationTrackingRepository;
    private final ContainerMainRepository containerMainRepository;
    private final ContainerDataRepository containerDataRepository;
    private final ContainerAttrRepository containerAttrRepository;

    // ===== 位址（零補齊 4 位十六進位；若驅動允許，可改回 B8DF/W883/W884/W885/B2DF）=====
    private static final String B_REQ = "B08DF"; // PLC → Host: 請求是否補蓋
    private static final String W_ANS = "W0883"; // Host → PLC: 0=不用、1=要
    private static final String W_QTY = "W0884"; // Host → PLC: 層數
    private static final String W_HGT = "W0885"; // Host → PLC: 厚度(mm)
    private static final String B_ACK = "B02DF"; // Host → PLC: 答覆完成

    // 來源站點
    private static final String INQUIRY_SITE = "Site#38";

    // 內部狀態：做上/下降緣偵測（避免重覆送 Ack）
    private boolean lastReq = false;
    private boolean lastAck = false;

    @Value("${labeler.device-name:PLC-Packer}")
    private String deviceName;

    @Scheduled(fixedDelay = 100)
    public void monitor() {
        try {
            boolean req = plc.readBoolean(device(), B_REQ);
            boolean ack = plc.readBoolean(device(), B_ACK);

            // 1) 上緣：PLC 發出新請求 -> 依 Site#38 現場帳決策，寫回答案/層數/厚度
            if (req && !lastReq) {
                CoverDecision d = decideCoverFromSite38();

                if (d.containerId == null) {
                    log.error("[TopCover] Site#38 無帳");
                    return;
                }

                // 層數：estimated_quantity
                plc.writeUInt16(device(), W_QTY, Math.max(d.qty(), 0));

                // 厚度：mm × 100（不做四捨五入，直接截去小數）
                plc.writeUInt16(device(), W_HGT, Math.max(d.thicknessX100(), 0));

                // 是否要補：qty>0 且 thickness>0
                plc.writeUInt16(device(), W_ANS, d.needCover() ? 1 : 0);
                plc.writeBoolean(device(), B_ACK, true);

                log.info("[TopCover] B08DF↑ -> ans={}, qty={}, hgt(x100)={}, containerId={}, source={}",
                        d.needCover() ? 1 : 0, d.qty(), d.thicknessX100(), d.containerId(), d.source());
            }


            // 2) 下降緣：PLC 清除請求 -> 我方也清除 ACK
            if (!req && lastReq) {
                if (ack) {
                    plc.writeBoolean(device(), B_ACK, false);
                    log.info("[TopCover] B08DF↓ -> 清除 B02DF=OFF，握手完成");
                }
            }

            // 3) 補償：若 PLC 請求仍為 ON，但我方 ACK 不小心掉了 -> 重新拉起 ACK（不重寫答案）
            if (req && !ack && lastAck) {
                plc.writeBoolean(device(), B_ACK, true);
                log.warn("[TopCover] 補償：請求仍在但 ACK=OFF，重新置 B02DF=ON");
            }

            lastReq = req;
            lastAck = plc.readBoolean(device(), B_ACK); // 以實際值更新
        } catch (Exception e) {
            log.error("[TopCover] 例外", e);
        }
    }

    /** 依 Site#38 的現場帳，決定是否補公蓋以及層數、厚度(mm)。 */
    private CoverDecision decideCoverFromSite38() {
        try {
            SiteDeviceStatus ds = siteStatusCache.getLatest(INQUIRY_SITE);
            if (ds == null || !ds.isValidAndComplete(3) || !ds.isProductPresent()) {
                log.warn("[TopCover] Site#38 快取無效/無產品，回不用蓋");
                return CoverDecision.no("cache-miss");
            }

            Long containerId = resolveContainerIdFromPlc(INQUIRY_SITE, ds);
            if (containerId == null) {
                containerId = locationTrackingRepository.findContainerAtLocationName(INQUIRY_SITE).orElse(null);
                if (containerId == null) {
                    log.warn("[TopCover] 取不到 containerId（PLC/DB 都無），回不用蓋");
                    return CoverDecision.no("no-container");
                }
                log.info("[TopCover] DB tracking fallback containerId={}", containerId);
            }

            // === 層數：container_data.estimated_quantity（空/負值視為 0）
            int qty = containerDataRepository.findByContainerMainId(containerId)
                    .map(cd -> cd.getEstimatedQuantity() == null ? 0 : Math.max(cd.getEstimatedQuantity(), 0))
                    .orElse(0);

            // === 厚度(mm)：container_attr.tray_thickness_mm（照 GP4 的解析再四捨五入）
            int thicknessX100 = resolveTrayThicknessX100(containerId).orElse(0);

            // === 屬性
            String binType = resolveTrayBinTypeSafe(containerId);

            boolean need = binType != null && binType.equals("B");
            return new CoverDecision(need, qty, thicknessX100, containerId, "data+attr");
        } catch (Exception e) {
            log.warn("[TopCover] decideCoverFromSite38 error", e);
            return CoverDecision.no("error");
        }
    }

    /** 從 SiteStatusCache 的 productId 解析出 containerMainId（序號或 LOT 任一命中即回）。 */
    private Long resolveContainerIdFromPlc(String site, SiteDeviceStatus ds) {
        try {
            String raw = ds.getProductId();
            String productId = raw == null ? "" : raw.trim();
            if (productId.isEmpty()) {
                //log.debug("[TopCover] PLC productId 為空. site={}", site);
                return null;
            }
            // 1) 以序號（alias_code）對應
            try {
                Optional<ContainerMain> byAlias = containerMainRepository.findByAliasCode(productId);
                if (byAlias.isPresent()) return byAlias.get().getId();
            } catch (Throwable ignore) { /* 專案尚未實作可略過 */ }

            //log.debug("[TopCover] productId='{}' 於 ContainerMain 未命中", productId);
            return null;
        } catch (Exception e) {
            log.warn("[TopCover] resolveContainerIdFromPlc error. site={}", site, e);
            return null;
        }
    }

    /** 讀 tray_thickness_mm，回傳「mm × 100」的整數；不四捨五入，直接截去小數。 */
    private Optional<Integer> resolveTrayThicknessX100(Long containerMainId) {
        try {
            Optional<ContainerAttr> opt =
                    containerAttrRepository.findOne(containerMainId, "tray_thickness_mm");
            String raw = opt.map(ContainerAttr::getAttrValue).orElse(null);
            Double mm = parseDecimalPositive(raw);     // ex: 5.62, 5,62, 5.62mm
            if (mm == null || mm <= 0.0) return Optional.empty();

            // 不做四捨五入：*100 後 floor（避免 5.62 → 562 四捨五入到 563）
            double scaled = mm * 100.0;
            int x100 = (int) Math.floor(scaled + 1e-9);  // 1e-9 避免二進位浮點誤差
            return Optional.of(Math.max(x100, 0));
        } catch (Exception e) {
            log.error("[TopCover] 讀取 tray_thickness_mm 例外：containerMainId={}, err={}",
                    containerMainId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /** 讀取托盤屬性。格式寬鬆；錯誤回 null。 */
    private String resolveTrayBinTypeSafe(Long containerMainId) {
        try {
            Optional<ContainerAttr> opt = containerAttrRepository.findOne(containerMainId, "bin_type");
            return opt.map(ContainerAttr::getAttrValue).orElse(null);
        } catch (Exception e) {
            log.error("[LAYER] 讀取 tray_thickness_mm 例外：containerMainId={}, err={}", containerMainId, e.getMessage(), e);
            return null;
        }
    }

    /** 與 GP4 相同：允許 "5.62", "5,62", "5.62mm"；非正或格式不對回 null。 */
    private static Double parseDecimalPositive(String raw) {
        if (raw == null) return null;
        String n = raw.trim().replaceAll("[^0-9,\\.\\-]", "");
        if (n.isEmpty()) return null;
        if (n.contains(".") && n.contains(",")) {
            n = n.replace(",", "");
        } else if (n.contains(",") && !n.contains(".")) {
            n = n.replace(',', '.');
        }
        try {
            double v = Double.parseDouble(n);
            return v > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String device() {
        return deviceName != null && !deviceName.isBlank() ? deviceName : "PLC-Packer";
    }

    // ==== 小型回傳物件 =====
    private record CoverDecision(boolean needCover, int qty, int thicknessX100, Long containerId, String source) {
        static CoverDecision no(String source) { return new CoverDecision(false, 0, 0, null, source); }
    }
}
