package com.czkuo.rdf88701.infra.dto;

import lombok.Getter;
import lombok.Setter;

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
