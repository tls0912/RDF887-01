package com.czkuo.rdf88701.domain.service.strategy.impl;

import com.czkuo.rdf88701.application.service.command.CraneRequestCommandService;
import com.czkuo.rdf88701.application.service.reservation.ReservationOrchestrator;
import com.czkuo.rdf88701.domain.repository.ContainerMainRepository;
import com.czkuo.rdf88701.domain.repository.LocationPointRepository;
import com.czkuo.rdf88701.domain.service.strategy.AutoWalkStrategyExecutor;
import com.czkuo.rdf88701.infra.dto.ContainerWithLocation;
import com.czkuo.rdf88701.infra.entity.AutoWalkConfig;
import com.czkuo.rdf88701.infra.entity.LocationPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Slf4j
@Component("NEXT_SLOT")
@RequiredArgsConstructor
public class NextSlotWalkExecutor implements AutoWalkStrategyExecutor {

    private final ContainerMainRepository containerMainRepository;
    private final LocationPointRepository locationPointRepository;
    private final CraneRequestCommandService craneRequestCommandService;
    private final ReservationOrchestrator reservationOrchestrator;

    @Override
    public void execute(AutoWalkConfig config) {
        List<ContainerWithLocation> containers = containerMainRepository.findAllInWarehouseWithLocation();
        Set<Long> blockedContainerIds = containerMainRepository.findContainerIdsWithUnfinishedTasksOrUnacceptedRequests();
        Set<Long> excludedContainerIds = new HashSet<>(Optional.ofNullable(config.getExcludedContainerIds()).orElse(Collections.emptyList()));
        containers.removeIf(c -> excludedContainerIds.contains(c.getId()) || blockedContainerIds.contains(c.getId()));

        if (containers.isEmpty()) {
            log.info("[AutoWalk-NEXT_SLOT] 無可搬移容器");
            return;
        }

        // 解析 TTL（秒），未設定預設 600 秒
        long ttlSeconds = parseTtlSeconds(config.getExtraConfig(), 600);

        Set<Long> excludedLocationIds = parseExcludedLocationIds(config.getExtraConfig());
        // 候選目標位（已排除鎖位/佔用/已預留）
        List<LocationPoint> availableLocations = locationPointRepository.findAllAvailableStorageExcluding(excludedLocationIds);

        if (availableLocations.isEmpty()) {
            log.warn("[AutoWalk-NEXT_SLOT] 無可用儲位");
            return;
        }

        // 本輪已預約過的目標位，避免重複指派
        Set<Long> usedTargetIds = new HashSet<>();

        for (ContainerWithLocation container : containers) {
            Integer level = container.getLevel();
            Integer bank = container.getBank();
            Integer bay  = container.getBay();

            if (level == null || bank == null || bay == null) {
                log.warn("[AutoWalk-NEXT_SLOT] container#{} 缺少位置資訊，略過", container.getId());
                continue;
            }

            // 1) 依“下一格”邏輯挑一格（若沒有，就取清單第一格）
            LocationPoint candidate = availableLocations.stream()
                    .filter(loc -> isAfter(loc, level, bank, bay))
                    .filter(loc -> !usedTargetIds.contains(loc.getId()))
                    .findFirst()
                    .orElseGet(() ->
                            availableLocations.stream()
                                    .filter(loc -> !usedTargetIds.contains(loc.getId()))
                                    .findFirst()
                                    .orElse(null)
                    );

            if (candidate == null) {
                log.warn("[AutoWalk-NEXT_SLOT] 無可用（未重複）儲位可指派，略過");
                continue;
            }

            // 2) 嘗試對“這一格”做精準預約（若被搶先，fallback 用保底預約找別格）
            Long targetId = null;

            var exact = reservationOrchestrator.reserveExactIfAvailable(
                    container.getId(),
                    candidate.getId(),
                    ttlSeconds,
                    "AUTO_WALK",
                    "NEXT_SLOT"
            );

            if (exact.isPresent()) {
                targetId = exact.get().getLocationPointId();
            } else {
                // 保底：排除來源位 + 已用位 + 最初挑的 candidate，再找一格
                Set<Long> exclude = new HashSet<>(usedTargetIds);
                exclude.add(candidate.getId());
                if (container.getLocationId() != null) exclude.add(container.getLocationId());

                var fallback = reservationOrchestrator.reserveForInbound(
                        container.getId(),
                        exclude,
                        ttlSeconds,
                        "AUTO_WALK",
                        "NEXT_SLOT_FALLBACK"
                );
                if (fallback.isPresent()) {
                    targetId = fallback.get().getLocationPointId();
                }
            }

            if (targetId == null) {
                log.warn("[AutoWalk-NEXT_SLOT] container#{} 預約失敗（candidate:{}），略過",
                        container.getId(), candidate.getCode());
                continue;
            }

            usedTargetIds.add(targetId);

            log.info("[AutoWalk-NEXT_SLOT] 建立搬運任務 container#{} {} → loc#{}",
                    container.getId(), container.getLocationCode(), targetId);

            craneRequestCommandService.createRelocateRequest(
                    container.getId(),
                    container.getLocationId(),
                    targetId,
                    "AUTO_WALK_NEXT_SLOT"
            );
        }
    }

    private boolean isAfter(LocationPoint loc, int level, int bank, int bay) {
        if (loc.getLevel() > level) return true;
        if (loc.getLevel() == level && loc.getBank() < bank) return true;
        return loc.getLevel() == level && loc.getBank() == bank && loc.getBay() > bay;
    }

    private Set<Long> parseExcludedLocationIds(Map<String, Object> extraConfig) {
        if (extraConfig == null || !extraConfig.containsKey("excluded_location_ids")) return Collections.emptySet();
        Object raw = extraConfig.get("excluded_location_ids");
        if (raw instanceof List<?>) {
            return ((List<?>) raw).stream()
                    .filter(Objects::nonNull)
                    .map(v -> Long.valueOf(v.toString()))
                    .collect(Collectors.toSet());
        }
        return Collections.emptySet();
    }

    private long parseTtlSeconds(Map<String, Object> extraConfig, long def) {
        if (extraConfig == null) return def;
        Object v = extraConfig.get("ttl_seconds");
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) try { return Long.parseLong(s); } catch (Exception ignored) {}
        return def;
    }
}
