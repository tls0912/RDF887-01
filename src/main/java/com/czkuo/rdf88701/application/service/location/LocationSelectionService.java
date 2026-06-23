package com.czkuo.rdf88701.application.service.location;

import com.czkuo.rdf88701.domain.repository.LocationPointRepository;
import com.czkuo.rdf88701.infra.entity.LocationPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LocationSelectionService {

    private final LocationPointRepository locationPointRepository;

    /**
     * 查詢一個可用的儲位（基礎條件）
     */
    public Optional<LocationPoint> findAvailableStorage() {
        return locationPointRepository.findFirstAvailableStorage();
    }

    // 可擴充條件版本（依 zone、料號、preferredStatus）
    public Optional<LocationPoint> findAvailableStorage(String zoneCode, String preferredStatus) {
        return locationPointRepository.findFirstAvailableStorageWithFilter(zoneCode, preferredStatus);
    }
}
