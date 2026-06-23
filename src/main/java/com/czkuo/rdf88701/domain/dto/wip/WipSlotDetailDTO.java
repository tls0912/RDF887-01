package com.czkuo.rdf88701.domain.dto.wip;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * WipSlotDetailDTO
 * - 儲格/位置資訊 + 容器資訊 + 產品資訊（給 UI 展示）
 */
@Data
public class WipSlotDetailDTO {
    // 儲格/位置資訊
    private String locationCode;         // 儲格代碼 (location_point.code)
    private String locationName;         // 儲格名稱 (location_point.name)
    private String zoneCode;             // 所屬區域 (location_point.zone_code)
    private String locationType;         // 地點類型 (location_point.location_type)
    private String status;               // 儲格狀態：ON(有容器)/OFF(無容器)（帳籍）
    private Boolean isLocked;            // 是否鎖定 (location_point.is_locked)
    private Boolean isReserved;          // 是否預約 (location_point.is_reserved)
    private Boolean isOccupied;          // 是否佔用 (location_point.is_occupied)（物理感測）

    // 帳籍狀態（建議補充說明用途）
    private String slotPresentStatus;    // 帳籍佔用狀態："ON"=帳面掛有容器,"OFF"=無容器（由 location_tracking 判斷）

    // 容器資訊
    private String containerAliasCode;   // 虛擬容器代號 (container_main.alias_code)
    private String containerType;        // 容器類型 (container_main.container_type)
    private String containerBarcode;     // 條碼 (container_main.container_code)
    private String lotNo;                // 批號 (container_main.lot_no)
    private String partNo;               // 料號 (container_main.part_no)
    private Integer estimatedQuantity;   // 預估層數 (container_data.estimated_quantity)
    private Integer verifiedQuantity;    // 驗證層數 (container_data.verified_quantity)
    private String ocrText1;             // OCR 掃描 (container_data.ocr_text1)
    private String ocrText2;             // OCR 掃描 (container_data.ocr_text2)
    private LocalDateTime arrivedTime;   // 抵達時間 (location_tracking.arrived_time)
}
