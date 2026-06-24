package com.czkuo.rdf88701.infra.dto;

import lombok.Getter;
import lombok.Setter;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Getter
@Setter
public class ContainerWithLocation {
    private Long id;
    private String aliasCode;
    private String containerType;
    private String containerCode;
    private String lotNo;
    private String partNo;
    private Long locationId;
    private String locationCode;  // 可選：儲位代碼，僅用於顯示用

    private Integer level;
    private Integer bank;
    private Integer bay;
}
