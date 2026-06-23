package com.czkuo.rdf88701.application.generator.impl.gripper;

import com.czkuo.rdf88701.application.generator.GripperRequestGenerator;
import com.czkuo.rdf88701.domain.plc.state.gripper.GripperDeviceStatus;
import com.czkuo.rdf88701.domain.repository.*;
import com.czkuo.rdf88701.infra.cache.GripperStatusCache;
import com.czkuo.rdf88701.infra.entity.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * GP3RequestGenerator
 * - Site#10 的容器移動邏輯
 * - 優先 DROP（Gripper 上有貨 → 依 R029 lane→Site 對應）
 * - 否則 PICK（來源 Site#10 → 同樣依 R029 lane→Site 對應）
 */
@Slf4j
@Component("GP3")
@RequiredArgsConstructor
public class GP3RequestGenerator implements GripperRequestGenerator {

    private final GripperRequestRepository requestRepository;
    private final GripperTaskRepository taskRepository;
    private final ContainerAttrRepository containerAttrRepository;
    private final LocationPointRepository locationPointRepository;
    private final LocationTrackingRepository locationTrackingRepository;

    // 改走 container_main 讀載具代號
    private final ContainerMainRepository containerMainRepository;

    // R029 資料來源
    private final RobotR029TaskRepository r029TaskRepository;
    private final RobotInR029LotRepository inR029LotRepository;

    // 以快取取得 PLC 狀態
    private final GripperStatusCache statusCache;

    /** 固定來源站點 */
    private static final String SOURCE_NAME = "Site#10";

    /** lane→目標站點對應 */
    private static final Map<String, String> LANE_TARGET_MAP = Map.of(
            "MAIN", "Site#23",
            "SUB",  "Site#34"
    );

    /** 厚度屬性鍵 */
    private static final String ATTR_TRAY_THICKNESS_MM = "tray_thickness_mm";

    @Override
    public Optional<Long> generateRequest(Long gripperId) {
        // 0) 早退：已有未完成請求/任務
        if (requestRepository.existsUnfinishedRequestForDevice(gripperId)
                || taskRepository.existsUnfinishedTaskForGripper(gripperId)) {
            //log.debug("[GP3] Gripper#{} 已有未完成請求/任務，略過", gripperId);
            return Optional.empty();
        }

        String gripperName = "Gripper#" + gripperId;
        GripperDeviceStatus ds = statusCache.getLatest(gripperName);

        boolean fresh = ds != null && ds.isValidAndComplete(3);
        if (!fresh) {
            //log.debug("[GP3] GP3 狀態快取無效（ds==null 或 !isValidAndComplete(3)），此次不建請求");
            return Optional.empty();
        }

        if (!ds.isTransferStandby()) {
            //log.debug("[GP3] GP3 設備狀態尚未準備好，此次不建請求");
            return Optional.empty();
        }

        // 1) Gripper 上有容器 → 優先 DROP（依 R029 lane→Site）
        Optional<Long> onArm = locationTrackingRepository.findContainerOnGripper(gripperId);
        if (onArm.isPresent()) {
            Long containerId = onArm.get();

            Optional<String> optCarrierId = getCarrierIdByContainerId(containerId);
            if (optCarrierId.isEmpty()) {
                //log.debug("[GP3] Gripper#{} 容器#{} 取不到 carrierId，略過 DROP", gripperId, containerId);
                return Optional.empty();
            }

            Optional<String> targetOpt = resolveTargetSiteByCarrier(optCarrierId.get());
            if (targetOpt.isEmpty()) {
                //log.debug("[GP3] 找不到對應 R029 或 lane 無對應目標，略過 DROP");
                return Optional.empty();
            }

            String target = targetOpt.get();
            if (isFree(target)) {
                return createRequest(gripperId, "DROP", null, target, containerId);
            }

            //log.debug("[GP3] DROP 目標 {} 已占用，略過", target);
            return Optional.empty();
        }

        // 2) 來源站位沒貨 → 略過
        Optional<Long> atSource = locationTrackingRepository.findContainerAtLocationName(SOURCE_NAME);
        if (atSource.isEmpty()) {
            //log.debug("[GP3] 來源 {} 無容器，略過", SOURCE_NAME);
            return Optional.empty();
        }

        // 3) PICK（同樣依 R029 lane→Site）
        Long containerId = atSource.get();
        Optional<String> optCarrierId = getCarrierIdByContainerId(containerId);
        if (optCarrierId.isEmpty()) {
            //log.debug("[GP3] 來源容器#{} 取不到 carrierId，略過 PICK", containerId);
            return Optional.empty();
        }

        Optional<String> targetOpt = resolveTargetSiteByCarrier(optCarrierId.get());
        if (targetOpt.isEmpty()) {
            //log.debug("[GP3] 找不到對應 R029 或 lane 無對應目標，略過 PICK");
            return Optional.empty();
        }

        String target = targetOpt.get();
        if (isFree(target)) {
            return createRequest(gripperId, "PICK", SOURCE_NAME, target, containerId);
        }

        //log.debug("[GP3] PICK 目標 {} 已占用，略過", target);
        return Optional.empty();
    }

    // ----------------------
    // 內部工具
    // ----------------------

    /** 由 carrierId 推導 R029 背景並轉成目標 Site（PROCESSING 優先，否則第一筆） */
    private Optional<String> resolveTargetSiteByCarrier(String carrierIdRaw) {
        String key = norm(carrierIdRaw);
        List<RobotR029Task> open = safeList(r029TaskRepository.findOpen());
        if (open.isEmpty()) return Optional.empty();

        List<RobotR029Task> matched = new ArrayList<>();
        for (RobotR029Task t : open) {
            Long logId = t.getLogId();
            if (logId == null) continue;

            List<String> carriers = safeList(inR029LotRepository.findCarrierIdsByLogId(logId));
            boolean hit = carriers.stream()
                    .filter(Objects::nonNull)
                    .map(this::norm)
                    .anyMatch(key::equals);

            if (hit) matched.add(t);
        }
        if (matched.isEmpty()) return Optional.empty();

        RobotR029Task ctx = matched.stream()
                .filter(x -> "PROCESSING".equalsIgnoreCase(safe(x.getInternalState())))
                .findFirst()
                .orElse(matched.get(0));

        String lane = safe(ctx.getLane());
        String target = LANE_TARGET_MAP.get(lane);
        return Optional.ofNullable(target);
    }

    /** 是否空閒 */
    private boolean isFree(String locationName) {
        return !locationTrackingRepository.hasContainerAtLocationName(locationName);
    }

    /** 建立請求（位置名稱版） */
    private Optional<Long> createRequest(Long gripperId, String taskType, String source, String target, Long containerMainId) {
        Long sourceId = (source != null) ? locationId(source) : null;

        Long targetId = locationId(target);

        // 讀托盤厚度（必要）
        Optional<BigDecimal> trayThicknessMm = resolveTrayThicknessMm(containerMainId);
        if (trayThicknessMm.isEmpty()) {
            log.warn("[GP3] 取不到 tray_thickness_mm 或非正數，放棄建立請求：containerMainId={}", containerMainId);
            return Optional.empty();
        }

        LocalDateTime now = LocalDateTime.now();

        GripperRequest req = new GripperRequest();
        req.setRequestKey(UUID.randomUUID().toString());
        req.setVersion(1);
        req.setRequestSource("SYSTEM");
        req.setGripperId(gripperId);
        req.setTaskType(taskType); // "DROP" or "PICK"
        req.setAccepted("N");
        req.setRequestTime(now);
        req.setCreatedTime(now);
        req.setSourceLocationId(sourceId);
        req.setTargetLocationId(targetId);
        req.setSourceLocationName(source);
        req.setTargetLocationName(target);
        req.setContainerMainId(containerMainId);
        req.setTargetHeightMm(trayThicknessMm.get());
        req.setLayerCount(0);

        boolean ok = requestRepository.save(req);
        if (ok) {
            log.info("[GP3] 建立 GripperRequest 成功: {} → {} [{}], containerId={}",
                    source != null ? source : ("Gripper#" + gripperId), target, taskType, containerMainId);
            return Optional.ofNullable(req.getId());
        } else {
            log.warn("[GP3] 建立 GripperRequest 失敗 [{}], containerId={}, target={}", taskType, containerMainId, target);
            return Optional.empty();
        }
    }

    /** 讀取單片托盤厚度（mm）：解析寬鬆，需 > 0 */
    private Optional<BigDecimal> resolveTrayThicknessMm(Long containerMainId) {
        try {
            Optional<ContainerAttr> opt = containerAttrRepository.findOne(containerMainId, ATTR_TRAY_THICKNESS_MM);
            String raw = opt.map(ContainerAttr::getAttrValue).orElse(null);
            return parsePositiveDecimal(raw);
        } catch (Exception e) {
            log.error("[GP3] 讀取 {} 例外：containerMainId={}, err={}",
                    ATTR_TRAY_THICKNESS_MM, containerMainId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    private Long locationId(String name) {
        return GripperLocationCache.requireLocationId(locationPointRepository, name);
    }

    /** 寬鬆正數解析：允許 "5.62", "5,62", "5.62mm"；逗點與小數點處理見註解 */
    private static Optional<BigDecimal> parsePositiveDecimal(String raw) {
        if (raw == null) return Optional.empty();
        String n = raw.trim();
        if (n.isEmpty()) return Optional.empty();

        n = n.replaceAll("[^0-9,\\.\\-]", "");
        if (n.isEmpty()) return Optional.empty();

        if (n.contains(".") && n.contains(",")) {
            n = n.replace(",", "");
        } else if (n.contains(",") && !n.contains(".")) {
            n = n.replace(',', '.');
        }

        try {
            BigDecimal v = new BigDecimal(n);
            return v.compareTo(BigDecimal.ZERO) > 0 ? Optional.of(v) : Optional.empty();
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    /** 由 container_main 取得 alias_code（carrierId） */
    private Optional<String> getCarrierIdByContainerId(Long containerMainId) {
        return containerMainRepository.findById(containerMainId)
                .map(ContainerMain::getAliasCode)
                .map(s -> s == null ? "" : s.trim())
                .filter(s -> !s.isEmpty());
    }

    private String norm(String s) { return safe(s).trim().toUpperCase(Locale.ROOT); }
    private static String safe(String s) { return (s == null) ? "" : s; }
    private static <T> List<T> safeList(List<T> list) { return (list == null) ? Collections.emptyList() : list; }
}
