package com.czkuo.rdf88701.infra.adapter.plc.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Transfer → PLC 傳送指令資料封裝 DTO
 * - 對應 Word Memory W0100~W011F
 * - 由上層 Application 組裝後交由 Encoder 編碼
 */
@Data
@Builder
public class PlcTransferWordCommand {

    /** 任務編號（Transfer No） - 對應 W0100 */
    private int transferNo;

    /** 指令類型（Transfer Type, 1:MOVE, 2:PICK, 3:DROP） - 對應 W0101 */
    private int transferType;

    /** 指令類型（Transfer Type, 1:MOVE, 2:PICK, 3:DROP） - 對應 W0101 */
    private int trayHeight;

    /** Bank（W0103） */
    private int locationBank;

    /** Bay（W0104） */
    private int locationBay;

    /** Level（W0105） */
    private int locationLevel;

    /**
     * 產品條碼（最大 50 字元，25 Word，每 Word 2 字）
     * - 對應 W0106 ~ W011E
     * - 使用 ASCII 編碼
     */
    private String productId;

    /**
     * 備用區塊（W011F） - 目前未使用，可保留空間或擴充用
     */
    private Integer spareWord;
}
