package com.czkuo.rdf88701.application.service.strapping;

import com.czkuo.rdf88701.domain.repository.LocationFlowRepository;
import com.czkuo.rdf88701.domain.repository.LocationPointRepository;
import com.czkuo.rdf88701.domain.repository.LocationTrackingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 綑包完成後的清帳服務
 * - 標記原位置 flow 為離開
 * - 移除 LocationTracking 快照資料
 * - 釋放佔用的位址（Site#29）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrappingAccountingService {

    private final LocationFlowRepository locationFlowRepository;
    private final LocationTrackingRepository locationTrackingRepository;
    private final LocationPointRepository locationPointRepository;

    /**
     * 綁包完成 → 清除容器帳與位置佔用（通常為 Site#29）
     */
    public void markStrappingCompleted(Long containerId, String siteName) {
        log.info("[Strapping清帳] 處理容器#{} 位於 {}", containerId, siteName);

        // 1️⃣ 標記 flow 離開
        int updated = locationFlowRepository.markPreviousAsLeft(containerId, LocalDateTime.now());
        if (updated > 0) {
            //log.debug("[Strapping清帳] 成功標記離開，共 {} 筆 flow", updated);
        }

        // 2️⃣ 查詢位置並標記 vacant
        locationTrackingRepository.findByContainerMainId(containerId).ifPresent(tracking -> {
            Long locationId = tracking.getLocationPointId();
            locationPointRepository.markVacant(locationId);
            //log.debug("[Strapping清帳] 標記位置#{} 為未佔用", locationId);
        });

        // 3️⃣ 刪除 tracking
        boolean deleted = locationTrackingRepository.deleteByContainerMainId(containerId);
        if (deleted) {
            log.info("[Strapping清帳] 已移除容器#{} 的 tracking 紀錄", containerId);
        } else {
            log.warn("[Strapping清帳] 容器#{} 無 tracking 紀錄可刪除", containerId);
        }
    }
}
