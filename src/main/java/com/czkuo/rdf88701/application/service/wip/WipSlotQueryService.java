package com.czkuo.rdf88701.application.service.wip;


import com.czkuo.rdf88701.domain.dto.wip.WipSlotDetailDTO;
import com.czkuo.rdf88701.domain.repository.LocationPointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * WipSlotQueryService
 * - 封裝儲格現況查詢，供各種 Handler / UI / 業務調用
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Service
@RequiredArgsConstructor
public class WipSlotQueryService {

    private final LocationPointRepository locationPointRepository;

    /**
     * 查詢所有儲格現況（帳籍/物理/容器/產品）
     *
     * @return 儲格詳細資料清單
     */
    public List<WipSlotDetailDTO> queryAllWipSlots() {
        return locationPointRepository.findAllSlotDetails();
    }

    /**
     * 查詢指定區域下的儲格現況
     *
     * @param zoneCode 區域代碼
     * @return 該區所有儲格詳細資料
     */
    public List<WipSlotDetailDTO> queryWipSlotsByZone(String zoneCode) {
        return locationPointRepository.findSlotDetailsByZone(zoneCode);
    }

    /**
     * 查詢帳實不一致（帳 ON 物理 OFF / 帳 OFF 物理 ON）的儲格
     */
    public List<WipSlotDetailDTO> queryMismatchedWipSlots() {
        return locationPointRepository.findMismatchedSlotDetails();
    }
}
