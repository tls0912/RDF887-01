package com.czkuo.rdf88701.application.generator.impl.transfer;

import com.czkuo.rdf88701.application.generator.TransferRequestGenerator;
import com.czkuo.rdf88701.application.service.command.ContainerCreateService;
import com.czkuo.rdf88701.application.service.location.LocationAccountingService;
import com.czkuo.rdf88701.application.service.process.DeviceProcessStateReader;
import com.czkuo.rdf88701.common.enums.EntryType;
import com.czkuo.rdf88701.common.enums.ExitType;
import com.czkuo.rdf88701.common.enums.ProcessStatus;
import com.czkuo.rdf88701.domain.plc.state.site.SiteDeviceStatus;
import com.czkuo.rdf88701.domain.repository.*;
import com.czkuo.rdf88701.infra.cache.SiteStatusCache;
import com.czkuo.rdf88701.infra.entity.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * TR6RequestGenerator
 * - Transfer#6：負責在 Site#16 與 Site#15 之間搬運
 * - 新規則：
 *   1) 先看 Site 是否有帳；若有，且該 Site != activeTarget，則 PICK 該 Site → DROP 到 activeTarget
 *   2) 若 Transfer 本身已有容器，且 activeTarget 為空，則 DROP 到 activeTarget
 *   3) 不做任何 PLC 補帳/建帳
 */
@Slf4j
@Component("TR6")
@RequiredArgsConstructor
public class TR6RequestGenerator implements TransferRequestGenerator {

    private final TransferRequestRepository requestRepository;
    private final TransferTaskRepository taskRepository;
    private final LocationPointRepository locationPointRepository;
    private final LocationTrackingRepository locationTrackingRepository;
    private final LocationFlowRepository locationFlowRepository;
    private final SiteBidirRouteRepository siteBidirRouteRepository;
    private final ContainerMainRepository  containerMainRepository;
    private final ContainerAttrRepository containerAttrRepository;
    private final ContainerCreateService containerCreateService;
    private final LocationAccountingService locationAccountingService;

    private final L005SessionRepository l005SessionRepository;

    private final SiteStatusCache siteStatusCache;
    private final DeviceProcessStateReader stateReader;

    // 線性站位（靠近 Transfer 的在前面；此處保留但流程改為依 activeTarget 決策）
    private static final String SITE_15 = "Site#15";
    private static final String SITE_16 = "Site#16";
    private static final List<String> LINEAR_SITES = List.of(SITE_16, SITE_15); // 16（靠近 TR6） → 15

    @Value("${app.transfer.TR6.pair-code:SITE15_16}")
    private String pairCode; // site_bidir_route 的 pair_code

    @Override
    public Optional<Long> generateRequest(Long transferId) {

        // 0) 若已有未完成請求/任務 → 略過
        if (requestRepository.existsUnfinishedRequestForDevice(transferId)
                || taskRepository.existsUnfinishedTaskForTransfer(transferId)) {
            //log.debug("[TR6] Transfer#{} 已有未完成請求或任務，略過", transferId);
            return Optional.empty();
        }

        // 1) 讀取目前 activeTarget（雙向表）
        String activeTarget = siteBidirRouteRepository.findAll().stream()
                .filter(r -> pairCode.equalsIgnoreCase(r.getPairCode()))
                .map(SiteBidirRoute::getActiveTarget)
                .findFirst()
                .orElse(null);

        if (activeTarget == null || (!activeTarget.equals(SITE_15) && !activeTarget.equals(SITE_16))) {
            //log.debug("[TR6] 無有效 activeTarget（pairCode={}），略過", pairCode);
            return Optional.empty();
        }

        SiteDeviceStatus siteDeviceStatus = siteStatusCache.getLatest("Site#16");
        boolean plcHasContainer = siteDeviceStatus != null && StringUtils.hasText(siteDeviceStatus.getProductId());

        Optional<Long> site15Cid = locationTrackingRepository.findContainerAtLocationName(SITE_15);
        Optional<Long> site16Cid = locationTrackingRepository.findContainerAtLocationName(SITE_16);
        boolean site15Has = site15Cid.isPresent();
        boolean site16Has = site16Cid.isPresent();

        if (plcHasContainer && !site16Has) {
            log.warn("[TR6] Site#16 PLC 有帳但 DB 無紀錄，進行自動補帳");
            compensateSiteAccount("Site#16");
        }

        if (!plcHasContainer && site16Has) {
            log.warn("[TR6] Site#16 PLC 無帳但 DB 有帳，進行自動刪帳");
            boolean cleared = clearLocationTracking(SITE_16);
            if (cleared) {
                log.info("[TR6] {} 清帳成功", SITE_16);
            }
            else {
                log.warn("[TR6] {} 清帳失敗", SITE_16);
            }
            return Optional.empty();
        }

        // 2) 先看 Site 是否有帳 → 若該 Site != activeTarget，將其搬到 activeTarget（目標需為空）
        if (SITE_16.equals(activeTarget)) { // 去 ZIPA
            // 目標 Site#16，要把 Site#15 的料搬過去
            if (site15Has && !site16Has) {
                return moveBetweenSites(transferId, SITE_15, SITE_16);
            }
        } else { // activeTarget = Site#15
            if (site16Has && !site15Has) { // 去 WIP
                return moveBetweenSites(transferId, SITE_16, SITE_15);
            }
        }

        // 3) 若 Site 沒有可搬來源，但 Transfer#6 上已有容器 → 嘗試 DROP 到 activeTarget（需為空）
        Optional<Long> onTr = locationTrackingRepository.findContainerOnTransfer(transferId);
        if (onTr.isPresent()) {
            if (locationTrackingRepository.findContainerAtLocationName(activeTarget).isEmpty()) {
                return createDropToTarget(transferId, activeTarget, onTr.get());
            } else {
                //log.debug("[TR6] Transfer 上有容器但 {} 已被占用，略過", activeTarget);
                return Optional.empty();
            }
        }

        //log.debug("[TR6] 無可搬移條件（source/transfer 無容器 或 目標被占），略過");
        return Optional.empty();
    }

    // ------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------

    /**
     * 清空指定站點的 LocationTracking（冪等）
     * 1) 有 tracking → 先把 location_flow 最後一筆補離開時間與 exitType / exitOperator
     * 2) 刪除 tracking
     * 3) 將 location_point.is_occupied 設為 'N'
     *
     * 不一致仍強制清除，但會寫 warning。
     */
    private boolean clearLocationTracking(String siteName) {
        try {
            // 先找到對應的 LocationPoint（假設名稱就是 siteName）
            LocationPoint p = locationPointRepository.findByName(siteName)
                    .orElse(null);
            if (p == null) {
                log.warn("[TR6] {} 找不到對應 LocationPoint，無法清帳", siteName);
                return false;
            }

            String reasonBase = "PC auto remove account site=" + siteName;
            String reason = reasonBase;

            // 先找 Tracking（找得到才有 containerMainId 可補 flow 離開 & 做 product 比對）
            var trackingOpt = locationTrackingRepository.findByLocationPointId(p.getId());

            if (trackingOpt.isPresent()) {
                var tr = trackingOpt.get();

                // 先比對 PLC Product vs ContainerMain（保留你原本的邏輯）
                Long cmId = tr.getContainerMainId();
                if (cmId != null) {
                    ContainerMain cm = containerMainRepository.findById(cmId).orElse(null);
                    if (cm != null) {
                        String containerCode = org.apache.commons.lang3.StringUtils.trimToEmpty(cm.getContainerCode());
                        String aliasCode = org.apache.commons.lang3.StringUtils.trimToEmpty(cm.getAliasCode());

                        log.info("[TR6] Auto remove {} aliasCode='{}', containerCode='{}'", siteName, sample(aliasCode), sample(containerCode));
                    }
                }

                // [Step 1] 將該 container 在此 location 的最後未離開紀錄標示離開
                locationFlowRepository.markExit(
                        tr.getContainerMainId(),
                        p.getId(),
                        LocalDateTime.now(),
                        ExitType.NORMAL,          // 或依需求改 NORMAL/PLC...
                        "SYS-SITE-REMOVE"
                );

                // [Step 2] 刪除 tracking
                locationTrackingRepository.deleteById(tr.getId());
            } else {
                log.info("[TR6] {} 無容器紀錄，直接視為空位清帳", siteName);
            }

            // [Step 3] 點位改為未佔用
            p.setIsOccupied("N");
            p.setUpdatedTime(LocalDateTime.now());
            locationPointRepository.update(p);

            log.info("[TR6] 清帳成功 <- {} ({})", siteName, reason);
            return true;
        } catch (Exception ex) {
            log.warn("[TR6] {} 清帳例外：{}", siteName, ex.toString(), ex);
            return false;
        }
    }

    /**
     * PLC 有帳但 DB 無 tracking → 以 L005 session 補資料：
     * 1) 先找現役 L005（同你 StatusReport 補 ACK 用法）
     * 2) 找不到再找最近成功的一筆（同站同條碼）
     * 3) container_main 不存在就依 L005 建立；存在則依 L005「有值欄位」覆寫後更新
     * 4) 若尚未入帳則入帳
     */
    private void compensateSiteAccount(String siteName) {
        locationPointRepository.findByName(siteName).ifPresentOrElse(locationPoint -> {
            Long locationPointId = locationPoint.getId();

            // 1) 取 PLC 的 barcode（=productId）
            SiteDeviceStatus status = siteStatusCache.getLatest(siteName);
            String barcode = (status != null && StringUtils.hasText(status.getProductId()))
                    ? status.getProductId().trim()
                    : null;

            if (!StringUtils.hasText(barcode)) {
                log.warn("[TR6] PLC 有帳但無有效 barcode（productId），略過補帳：{}", siteName);
                return;
            }

            // 2) 先找現役 L005，無則找最近成功
            Optional<L005Session> sOpt = l005SessionRepository.findActiveByBarcode(barcode);
            if (sOpt.isEmpty()) {
                sOpt = findLatestL005ForBarcode(barcode);
            }
            if (sOpt.isEmpty()) {
                log.warn("[TR6] 找不到 L005 session（site={}, barcode={}），保守略過補帳", siteName, barcode);
                return;
            }
            L005Session s = sOpt.get();

            // 3) upsert container_main
            Long containerId = upsertContainerFromL005(s);

            // 4) 未入帳才入帳
            boolean hasTracking = locationTrackingRepository.findByContainerMainId(containerId).isPresent();
            if (!hasTracking) {
                locationAccountingService.entry(containerId, locationPointId, EntryType.PLC, "system-auto", null);
                log.warn("[TR6] 補入帳完成 → site={}, containerId={}, barcode={}", siteName, containerId, barcode);
            } else {
                //log.debug("[TR6] 已入帳 → site={}, containerId={}, barcode={}", siteName, containerId, barcode);
            }
        }, () -> log.error("[TR6] 找不到位置：{}", siteName));
    }

    /** 依 siteName + barcode 找最近成功 L005；若有精準 API 先用之，否則用最近 N 筆過濾 */
    private Optional<L005Session> findLatestL005ForBarcode(String barcode) {
        List<L005Session> recent = l005SessionRepository.findRecentByBarcode(barcode, 10);
        return recent.stream()
                .filter(x -> "PASS".equalsIgnoreCase(nvl(x.getPeerResult())))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .findFirst();
    }

    /** 用 L005 建 container_main（若不存在），存在則依 L005 欄位「有值才覆寫」後更新；回傳 containerId */
    private Long upsertContainerFromL005(L005Session s) {
        String aliasCode = nvl(s.getPeerCarrierId()).trim();
        if (aliasCode.isEmpty()) throw new IllegalArgumentException("L005 Carrier Id is blank");

        Optional<ContainerMain> cmOpt = containerMainRepository.findByAliasCode(aliasCode);
        if (cmOpt.isEmpty()) {
            // === 新建 ===
            ContainerMain cm = new ContainerMain();
            cm.setAliasCode(aliasCode);
            cm.setContainerCode(nvl(s.getBarcode()));
            cm.setContainerType("TRAY");
            cm.setLotNo(nvl(s.getPeerLotId()));
            cm.setPartNo(nvl(s.getPeerTrayType()));
            cm.setCreatedTime(LocalDateTime.now());

            boolean ok = containerMainRepository.save(cm);
            if (!ok || cm.getId() == null) {
                throw new IllegalStateException("Create container_main failed, carrierId=" + aliasCode);
            }
            log.info("[TR6] 建立 container_main：id={}, carrierId={}", cm.getId(), aliasCode);
            return cm.getId();
        }

        // === 已存在 → 只 patch 有值且不同的欄位 ===
        ContainerMain cm = cmOpt.get();
        boolean changed = patchContainerMainFromL005(cm, s);
        if (changed) {
            boolean ok = containerMainRepository.update(cm);
            if (!ok) throw new IllegalStateException("Update container_main failed, id=" + cm.getId());
            log.info("[TR6] 更新 container_main：id={}, carrierId={}", cm.getId(), aliasCode);
        } else {
            //log.debug("[TR6] container_main 無需更新：id={}, carrierId={}", cm.getId(), aliasCode);
        }

        // 補厚度屬性 ---
        upsertAttr(cm.getId(), "tray_thickness_mm", s.getPeerTrayHigh(), "mm");

        return cm.getId();
    }

    /** 只用 L005 有值欄位覆寫；回傳是否有變更 */
    private boolean patchContainerMainFromL005(ContainerMain cm, L005Session s) {
        boolean changed = false;

        // 條碼以查詢 key 為準，正常不改；要支援條碼變更需先評估影響
        String barcode = nvl(s.getBarcode()).trim();
        if (!barcode.isEmpty() && !barcode.equals(nvl(cm.getContainerCode()))) {
            cm.setContainerCode(barcode);
            changed = true;
        }

        String type = "TRAY";
        if (!type.equals(nvl(cm.getContainerType()))) {
            cm.setContainerType(type);
            changed = true;
        }

        String lot = nvl(s.getPeerLotId()).trim();
        if (!lot.isEmpty() && !lot.equals(nvl(cm.getLotNo()))) {
            cm.setLotNo(lot);
            changed = true;
        }

        String part = nvl(s.getPeerTrayType()).trim();
        if (!part.isEmpty() && !part.equals(nvl(cm.getPartNo()))) {
            cm.setPartNo(part);
            changed = true;
        }

        return changed;
    }

    private void upsertAttr(Long containerId, String key, String value, String unit) {
        ContainerAttr a = new ContainerAttr();
        a.setContainerMainId(containerId);
        a.setAttrKey(key);
        a.setAttrValue(value);
        a.setUnit(unit);
        containerAttrRepository.upsert(a); // 需 UNIQUE KEY (container_main_id, attr_key)
    }

    /**
     * 從 sourceSite PICK，DROP 到 targetSite（需確保 target 為空）
     */
    private Optional<Long> moveBetweenSites(Long transferId, String sourceSite, String targetSite) {
        if (sourceSite.equals(targetSite)) {
            // 防呆：不應出現
            //log.debug("[TR6] source 與 target 相同（{}），略過", sourceSite);
            return Optional.empty();
        }

        if (locationTrackingRepository.findContainerAtLocationName(targetSite).isPresent()) {
            //log.debug("[TR6] 目標 {} 已有容器，無法 DROP", targetSite);
            return Optional.empty();
        }

        Long containerId = locationTrackingRepository.findContainerAtLocationName(sourceSite)
                .orElse(null);
        if (containerId == null) {
            //log.debug("[TR6] source {} 查無容器，略過", sourceSite);
            return Optional.empty();
        }

        return createPickDropRequest(transferId, sourceSite, targetSite, containerId);
    }

    /**
     * Transfer 上已有容器 → 直接 DROP 到 targetSite
     */
    private Optional<Long> createDropToTarget(Long transferId, String targetSite, Long containerId) {
        Long targetId = locationIdOrNull(targetSite);
        if (targetId == null) {
            log.warn("[TR6] 找不到目標位置：{}", targetSite);
            return Optional.empty();
        }

        TransferRequest req = baseRequest(transferId, "DROP", containerId);
        req.setTargetLocationId(targetId);
        req.setTargetLocationName(targetSite);

        boolean ok = requestRepository.save(req);
        if (ok) {
            log.info("[TR6] 建立 TransferRequest 成功: TRANSFER → {} [DROP], containerId={}",
                    targetSite, containerId);
            return Optional.of(req.getId());
        } else {
            log.warn("[TR6] 建立 DROP 請求失敗");
            return Optional.empty();
        }
    }

    /**
     * 建立 PICK（source）→ DROP（target）的一筆請求
     */
    private Optional<Long> createPickDropRequest(Long transferId, String sourceSite, String targetSite, Long containerId) {
        Long sourceId = locationIdOrNull(sourceSite);
        Long targetId = locationIdOrNull(targetSite);

        if (sourceId == null || targetId == null) {
            log.warn("[TR6] 站位查無對應 LocationPoint：source={}, target={}", sourceSite, targetSite);
            return Optional.empty();
        }

        TransferRequest req = baseRequest(transferId, "PICK", containerId);
        req.setSourceLocationId(sourceId);
        req.setTargetLocationId(targetId);
        req.setSourceLocationName(sourceSite);
        req.setTargetLocationName(targetSite);

        boolean ok = requestRepository.save(req);
        if (ok) {
            log.info("[TR6] 建立 TransferRequest 成功: {} → {} [PICK→DROP], containerId={}",
                    sourceSite, targetSite, containerId);
            return Optional.of(req.getId());
        } else {
            log.warn("[TR6] 建立 PICK→DROP 請求失敗");
            return Optional.empty();
        }
    }

    private TransferRequest baseRequest(Long transferId, String taskType, Long containerMainId) {
        TransferRequest r = new TransferRequest();
        r.setRequestKey(UUID.randomUUID().toString());
        r.setVersion(1);
        r.setRequestSource("SYSTEM");
        r.setTransferId(transferId);
        r.setTaskType(taskType);           // "PICK" 或 "DROP"
        r.setAccepted("N");
        r.setRequestTime(LocalDateTime.now());
        r.setCreatedTime(LocalDateTime.now());
        r.setContainerMainId(containerMainId);
        return r;
    }

    private String nvl(String s) { return s == null ? "" : s; }

    // ---------- ASCII50 工具：去除尾端 NUL 與控制字元，僅供稽核/記錄 ----------
    private String normalizeAscii50(String s) {
        if (s == null) return null;
        // 去尾端 NUL
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '\0') end--;
        String t = s.substring(0, end);
        // 去控制字元（log 友善）
        StringBuilder out = new StringBuilder(t.length());
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c >= 0x20 && c <= 0x7E) out.append(c);
        }
        return out.toString();
    }

    /** 短顯示（避免把長字串塞爆 log） */
    private String sample(String s) {
        if (s == null) return "";
        return s.length() <= 50 ? s : s.substring(0, 50);
    }
    private boolean deviceIsRun(String deviceName) {
        return stateReader.getBestEffort(deviceName).getStatus() != ProcessStatus.STOP;
    }

    private Long locationIdOrNull(String name) {
        return TransferLocationCache.findLocationId(locationPointRepository, name);
    }
}
