package com.czkuo.rdf88701.application.service.query;

import com.czkuo.rdf88701.domain.repository.LocationPointRepository;
import com.czkuo.rdf88701.infra.entity.LocationPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Location 查詢服務
 * - 封裝 LocationPoint 查詢邏輯
 * - 提供應用層查詢 API
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Service
@RequiredArgsConstructor
public class LocationQueryService {

    private final LocationPointRepository locationPointRepository;

    /**
     * 依 ID 查詢單一位置點
     */
    public LocationPoint getById(Long id) {
        return locationPointRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("LocationPoint not found: id=" + id));
    }

    /**
     * Optional 查詢方式
     */
    public Optional<LocationPoint> findById(Long id) {
        return locationPointRepository.findById(id);
    }
}
