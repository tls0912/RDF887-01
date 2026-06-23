package com.czkuo.rdf88701.presentation.web.dto;

import lombok.Data;

/**
 * 綁定（建帳）請求。
 * 你可以只傳 containerMainId；或不傳 containerMainId，但要帶 carrierId/containerCode/lotNo/partNo
 * 後者會在系統內新建一筆 ContainerMain。
 */
@Data
public class BindRequest {

    /** 既有的 ContainerMain ID（可選） */
    private Long containerMainId;

    /** 承載序號（可選） */
    private String carrierId;

    /** 容器代碼（可選） */
    private String containerCode;

    /** LOT（可選） */
    private String lotNo;

    /** 料號（可選） */
    private String partNo;
}
