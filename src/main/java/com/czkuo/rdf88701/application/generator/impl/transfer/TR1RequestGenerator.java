package com.czkuo.rdf88701.application.generator.impl.transfer;

import com.czkuo.rdf88701.application.generator.TransferRequestGenerator;
import com.czkuo.rdf88701.application.service.PlcAccessService;
import com.czkuo.rdf88701.application.service.command.ContainerCreateService;
import com.czkuo.rdf88701.domain.plc.state.site.SiteDeviceStatus;
import com.czkuo.rdf88701.domain.repository.*;
import com.czkuo.rdf88701.infra.cache.SiteStatusCache;
import com.czkuo.rdf88701.infra.entity.ContainerMain;
import com.czkuo.rdf88701.infra.entity.LocationPoint;
import com.czkuo.rdf88701.infra.entity.LocationTracking;
import com.czkuo.rdf88701.infra.entity.TransferRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static com.czkuo.rdf88701.application.monitor.AutoR029Planner.TestMode;

/**
 * TR1RequestGenerator
 * - 根據線性 Site 狀態產生搬運請求
 * - 策略說明：
 *   1. 若 Transfer 裝置上已有容器 → 嘗試 DROP 至後方 Site
 *   2. 否則由後往前尋找可 PICK 的容器，並搬往最遠的可用空 Site
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component("TR1")
@RequiredArgsConstructor
public class TR1RequestGenerator implements TransferRequestGenerator {

    private final ContainerCreateService containerCreateService;
    private final TransferRequestRepository requestRepository;
    private final TransferTaskRepository taskRepository;
    private final LocationPointRepository locationPointRepository;
    private final LocationTrackingRepository locationTrackingRepository;
    private final ContainerMainRepository containerMainRepository;
    private final PlcAccessService plcAccessService;

    private final SiteStatusCache siteStatusCache;

    // 注意：目前 Site 編碼順序寫死
    private static final List<String> LINEAR_SITES = List.of("Site#1", "Site#2", "Site#3");

    @Override
    public Optional<Long> generateRequest(Long transferId) {

        // Step 1: 嘗試與 Site#1 交握（若有帳）
        try {
            tryHandshakeWithSite1();
        }
        catch (Exception e) {
            return Optional.empty();
        }

        // 1. 若已有未完成請求/任務 → 略過
        if (requestRepository.existsUnfinishedRequestForDevice(transferId)
                || taskRepository.existsUnfinishedTaskForTransfer(transferId)) {
            //log.debug("[TR1] Transfer#{} 已有未完成請求或任務，略過", transferId);
            return Optional.empty();
        }

        // 2. 若 Transfer 裝置上已有容器 → DROP
        Optional<Long> containerOnTransfer = locationTrackingRepository.findContainerOnTransfer(transferId);
        if (containerOnTransfer.isPresent()) {
            Long containerId = containerOnTransfer.get();
            return findFirstAvailableSiteFromBack()
                    .flatMap(target -> createRequest(transferId, "DROP", null, target, containerId));
        }

        // 3. 從後往前尋找可搬的 Site，依序嘗試 PICK
        for (int i = LINEAR_SITES.size() - 2; i >= 0; i--) {
            String source = LINEAR_SITES.get(i);
            Optional<Long> containerAtSite = locationTrackingRepository.findContainerAtLocationName(source);
            if (containerAtSite.isEmpty()) continue;


            // 找最靠後的空 Site（可覆蓋為 Site 優先策略）
            for (int j = i + 1; j < LINEAR_SITES.size(); j++) {
                String target = LINEAR_SITES.get(j);
                if (locationTrackingRepository.findContainerAtLocationName(target).isEmpty()) {
                    return createRequest(transferId, "PICK", source, target, containerAtSite.get());
                }
            }
        }

        //log.debug("[TR1] 無可搬移容器或空位，略過 Transfer#{}", transferId);
        return Optional.empty();
    }

    /**
     * 從 Site 後方開始尋找第一個可放置的位置
     */
    private Optional<String> findFirstAvailableSiteFromBack() {
        for (int i = LINEAR_SITES.size() - 1; i >= 0; i--) {
            String site = LINEAR_SITES.get(i);
            if (locationTrackingRepository.findContainerAtLocationName(site).isEmpty()) {
                return Optional.of(site);
            }
        }
        return Optional.empty();
    }

    /**
     * 建立 TransferRequest 請求（帶入容器 ID）
     */
    private Optional<Long> createRequest(Long transferId, String taskType, String source, String target, Long containerMainId) {
        Long sourceId = source != null ? locationId(source) : null;

        Long targetId = target != null ? locationId(target) : null;

        TransferRequest request = new TransferRequest();
        request.setRequestKey(UUID.randomUUID().toString());
        request.setVersion(1);
        request.setRequestSource("SYSTEM");
        request.setTransferId(transferId);
        request.setTaskType(taskType);
        request.setAccepted("N");
        request.setRequestTime(LocalDateTime.now());
        request.setCreatedTime(LocalDateTime.now());
        request.setSourceLocationId(sourceId);
        request.setTargetLocationId(targetId);
        request.setSourceLocationName(source);
        request.setTargetLocationName(target);
        request.setContainerMainId(containerMainId);

        boolean success = requestRepository.save(request);
        if (success) {
            log.info("[TR1] 建立 TransferRequest 成功: {} → {} [{}], containerId={}",
                    source, target, taskType, containerMainId);
            return Optional.of(request.getId());
        } else {
            log.warn("[TR1] 建立 TransferRequest 失敗 [{}]", taskType);
            return Optional.empty();
        }
    }

    /**
     * 若 Site#1 有帳 → 執行 aliasCode 寫入與 PLC handshake（B28B）
     */
    private void tryHandshakeWithSite1() {
        String siteName = "Site#1";
        long locationPointId = 205L;

        Optional<LocationTracking> trackingOpt = locationTrackingRepository.findByLocationPointId(locationPointId);
        // if (trackingOpt.isEmpty()) return;

        boolean hasContainer = trackingOpt.isPresent();

        SiteDeviceStatus ds = siteStatusCache.getLatest(siteName);
        boolean fresh = ds != null && ds.isValidAndComplete(3);
        if (!fresh) {
            //log.debug("[SITE1] SITE1 狀態快取無效，略過此次請求生成");
            return;
        }
        if(TestMode)
        {
            if (!hasContainer && ds.isProductPresent()) {
                String barcode = genTyBarcode(); // 例：TY1234AB
                Long newContainerId = containerCreateService.createAndEntryRealTrayForLocationAuto(
                        barcode,
                        locationPointId,
                        "ALL_COVER"
                        //"NORMAL_WITH_COVER"
                );
                log.info("[SITE1] 自動建檔完成 containerId={} barcode={}", newContainerId, barcode);

                // 重新判斷是否已在 SITE 上（建帳後可能已在該 SITE 的點位）
                trackingOpt = locationTrackingRepository.findByLocationPointId(locationPointId);
                hasContainer = trackingOpt.isPresent();
            }
        }
        if (!hasContainer) return;

        Long containerMainId = trackingOpt.get().getContainerMainId();
        Optional<ContainerMain> containerOpt = containerMainRepository.findById(containerMainId);
        if (containerOpt.isEmpty()) return;

        String aliasCode = containerOpt.get().getAliasCode();

        plcAccessService.writeBoolean("PLC-Sub", "B24B", false);

        String current = plcAccessService.readString("PLC-Sub", "W13E4", 25);
        String next = plcAccessService.readString("PLC-Sub", "W1404", 25);

        if (current != null && !current.trim().isEmpty()) {
            //log.debug("[TR1] Site#1 PLC 已有帳 '{}', 略過寫入", current);
            return;
        }

        if (next == null || !next.equals(aliasCode)) {
            plcAccessService.writeString("PLC-Sub", "W3E6", aliasCode);
            log.info("[TR1] Site#1 寫入 aliasCode={} 至 PLC", aliasCode);
        }

        plcAccessService.writeBoolean("PLC-Sub", "B24B", true);

        // 等待 B84B 回應（輪詢 3 秒）
        int retries = 15;
        for (int i = 0; i < retries; i++) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            boolean ack = plcAccessService.readBoolean("PLC-Sub", "B84B");
            if (ack) {
                plcAccessService.writeBoolean("PLC-Sub", "B28B", false);
                log.info("[TR1] PLC 回應 B84B 成功，完成 Site#1 帳資料 handshake");
                return;
            }
        }

        log.warn("[TR1] Site#1 寫入 PLC 後超時未收到 B84B Ack，保留 B28B=True");
    }

    // ---------- 條碼產生器：TY + 4位數字 + 2位大寫英文 ----------
    private static final SecureRandom RAND = new SecureRandom();
    private String genTyBarcode() {
        int num = RAND.nextInt(10_000);             // 0000..9999
        String digits = String.format("%04d", num);
        char c1 = (char) ('A' + RAND.nextInt(26));
        char c2 = (char) ('A' + RAND.nextInt(26));
        return "TY" + digits + c1 + c2;
    }

    private Long locationId(String name) {
        return TransferLocationCache.requireLocationId(locationPointRepository, name);
    }
}
