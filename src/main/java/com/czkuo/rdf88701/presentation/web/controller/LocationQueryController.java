package com.czkuo.rdf88701.presentation.web.controller;

import com.czkuo.rdf88701.application.dto.query.LocationPointQuery;
import com.czkuo.rdf88701.domain.repository.ContainerMainRepository;
import com.czkuo.rdf88701.domain.repository.LocationPointRepository;
import com.czkuo.rdf88701.domain.repository.LocationTrackingRepository;
import com.czkuo.rdf88701.infra.entity.LocationPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@RestController
@RequestMapping("/api/locations")
@RequiredArgsConstructor
public class LocationQueryController {

    private final LocationPointRepository locationPointRepository;
    private final LocationTrackingRepository locationTrackingRepository;
    private final ContainerMainRepository containerMainRepository;

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(required = false) String zone,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,     // 前端 0-based
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(defaultValue = "false") boolean onlyTracked
    ) {
        int p0 = Math.max(0, page);
        int s  = Math.max(1, size);

        String zoneNorm = norm(zone);
        String typeNorm = normType(type); // "ALL" -> null

        // 用分頁查詢一次就拿到 data + total
        LocationPointQuery q = new LocationPointQuery();
        q.setZoneCode(zoneNorm);
        q.setLocationType(typeNorm);
        q.setPageNum(p0 + 1);   // 你的 PageResult 是 1-based
        q.setPageSize(s);

        var pageResult = locationPointRepository.findPageByCondition(q);
        long totalDb   = pageResult.getTotal() != null ? pageResult.getTotal() : 0L;
        List<LocationPoint> points = pageResult.getData(); // ★★★ 改用 getData()

        // 組 content
        List<Map<String, Object>> content = new ArrayList<>(points.size());
        for (LocationPoint p : points) {
            var trackingOpt = locationTrackingRepository.findByLocationPointId(p.getId());
            boolean hasContainer = trackingOpt.isPresent();

            if (onlyTracked && !hasContainer) continue;

            var dto = new LinkedHashMap<String, Object>();
            dto.put("id", p.getId());
            dto.put("zoneCode", p.getZoneCode());
            dto.put("code", p.getCode());
            dto.put("name", p.getName());
            dto.put("bank", p.getBank());
            dto.put("bay", p.getBay());
            dto.put("level", p.getLevel());
            dto.put("locationType", p.getLocationType());
            dto.put("enabled", p.getEnabled());
            dto.put("isOccupied", p.getIsOccupied());
            dto.put("isLocked", p.getIsLocked());
            dto.put("isReserved", p.getIsReserved());
            dto.put("lockReason", p.getLockReason());
            dto.put("preferredStatus", p.getPreferredStatus());
            dto.put("createdTime", p.getCreatedTime());
            dto.put("updatedTime", p.getUpdatedTime());

            if (hasContainer) {
                var tr = trackingOpt.get();
                dto.put("hasContainer", true);
                dto.put("arrivedTime", tr.getArrivedTime());
                dto.put("lastVerifiedTime", tr.getLastVerifiedTime());

                containerMainRepository.findById(tr.getContainerMainId()).ifPresent(m -> {
                    dto.put("carrierId", m.getAliasCode());
                    dto.put("containerCode", m.getContainerCode());
                    dto.put("lotNo", m.getLotNo());
                    dto.put("partNo", m.getPartNo());
                });
            } else {
                dto.put("hasContainer", false);
            }

            content.add(dto);
        }

        // total：onlyTracked=true 時改成「過濾後的總數」
        long totalOut = onlyTracked ? computeTrackedTotal(zoneNorm, typeNorm) : totalDb;

        return Map.of("content", content, "total", totalOut);
    }

    private static String norm(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String normType(String s) {
        String t = norm(s);
        if (t == null) return null;
        return "ALL".equalsIgnoreCase(t) ? null : t;
    }

    /**
     * 估算 onlyTracked=true 的總數。
     * 這裡用簡單「分頁迴圈」把所有點位掃過一次；資料量很大時建議做一支 JOIN COUNT SQL。
     */
    private long computeTrackedTotal(String zone, String type) {
        long count = 0;
        int pageNum = 1;
        final int pageSize = 500; // 依你 PageResult 上限

        while (true) {
            LocationPointQuery q = new LocationPointQuery();
            q.setZoneCode(zone);
            q.setLocationType(type);
            q.setPageNum(pageNum);
            q.setPageSize(pageSize);

            var pr = locationPointRepository.findPageByCondition(q);
            List<LocationPoint> data = pr.getData(); // ★★★ 改用 getData()
            if (data == null || data.isEmpty()) break;

            for (LocationPoint p : data) {
                if (locationTrackingRepository.findByLocationPointId(p.getId()).isPresent()) {
                    count++;
                }
            }

            long pages = pr.getPages() != null ? pr.getPages() : 1L;
            if (pageNum >= pages) break;
            pageNum++;
        }
        return count;
    }
}
