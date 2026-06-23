package com.czkuo.rdf88701.infra.adapter.plc.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Site → PLC Word 指令 DTO
 * - 區段：W03E0 ~ W03FF（32 Words）
 * - Product ID：W03E6 ~ W03FE（25 Words = 50 ASCII）
 * - Spare Tail：W03FF
 */
@Data
@Builder
public class PlcSiteWordCommand {

    /** 產品條碼（最多 50 ASCII；對應 W03E6 ~ W03FE） */
    private String productId;
}
