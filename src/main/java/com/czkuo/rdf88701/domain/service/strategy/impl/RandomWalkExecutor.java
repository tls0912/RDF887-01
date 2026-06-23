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

@Slf4j
@Component("RANDOM")
@RequiredArgsConstructor
public class RandomWalkExecutor implements AutoWalkStrategyExecutor {

    private final ContainerMainRepository containerMainRepository;
    private final LocationPointRepository locationPointRepository;
    private final CraneRequestCommandService craneRequestCommandService;
    private final ReservationOrchestrator reservationOrchestrator;

    @Override
    public void execute(AutoWalkConfig config) {
        // 1) 所有容器
        List<ContainerWithLocation> containers = containerMainRepository.findAllInWarehouseWithLocation();

        // 2) 過濾
        Set<Long> blockedContainerIds = containerMainRepository.findContainerIdsWithUnfinishedTasksOrUnacceptedRequests();
        Set<Long> excludedContainerIds = new HashSet<>(Optional.ofNullable(config.getExcludedContainerIds()).orElse(Collections.emptyList()));
        containers.removeIf(c -> excludedContainerIds.contains(c.getId()) || blockedContainerIds.contains(c.getId()));

        if (containers.isEmpty()) {
            log.info("[AutoWalk-RANDOM] 無可搬移容器，結束");
            return;
        }

        // 3) 隨機取前 N 顆
        Collections.shuffle(containers);
        int limit = Optional.ofNullable(config.getContainerLimit()).orElse(containers.size());
        containers = containers.subList(0, Math.min(limit, containers.size()));

        // 4) 候選儲位（已排除/已解耦 is_reserved/is_occupied）
        Set<Long> excludedLocationIds = parseExcludedLocationIds(config.getExtraConfig());
        List<LocationPoint> availableLocations = locationPointRepository.findRandomAvailableStorageListExcluding(limit, excludedLocationIds);

        if (availableLocations.isEmpty()) {
            log.warn("[AutoWalk-RANDOM] 無可用儲位，結束");
            return;
        }
        // TTL
        long ttlSeconds = parseTtlSeconds(config.getExtraConfig(), 600);

        // 本輪避免重複
        Set<Long> usedTargetIds = new HashSet<>();
        Iterator<LocationPoint> it = availableLocations.iterator();

        for (ContainerWithLocation container : containers) {
            // 5) 先挑一格隨機候選（可用清單中走 iterator）
            LocationPoint candidate = null;
            while (it.hasNext()) {
                LocationPoint p = it.next();
                if (!usedTargetIds.contains(p.getId())) { candidate = p; break; }
            }

            // 若沒候選了，直接用保底預約去找（不再限制清單）
            Long targetId = null;

            if (candidate != null) {
                var exact = reservationOrchestrator.reserveExactIfAvailable(
                        container.getId(), candidate.getId(), ttlSeconds, "AUTO_WALK", "RANDOM");
                if (exact.isPresent()) {
                    targetId = exact.get().getLocationPointId();
                }
            }

            if (targetId == null) {
                // 保底：排除來源位 + 已用位 +（若有）candidate
                Set<Long> exclude = new HashSet<>(usedTargetIds);
                if (candidate != null) exclude.add(candidate.getId());
                if (container.getLocationId() != null) exclude.add(container.getLocationId());

                var fallback = reservationOrchestrator.reserveForInbound(
                        container.getId(), exclude, ttlSeconds, "AUTO_WALK", "RANDOM_FALLBACK");

                if (fallback.isEmpty()) {
                    log.warn("[AutoWalk-RANDOM] container#{} 預約失敗（無可用儲位），略過", container.getId());
                    continue;
                }
                targetId = fallback.get().getLocationPointId();
            }

            usedTargetIds.add(targetId);

            log.info("[AutoWalk-RANDOM] 建立搬運任務：container#{} {} → loc#{}",
                    container.getId(), container.getLocationCode(), targetId);

            craneRequestCommandService.createRelocateRequest(
                    container.getId(),
                    container.getLocationId(),
                    targetId,
                    "AUTO_WALK_RANDOM"
            );
        }
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
