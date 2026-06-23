package com.czkuo.rdf88701.application.service.polling;

import java.time.Instant;

/**
 * PollingDataRouter
 * - 封裝不同設備的資料解析與分派邏輯
 */
public interface PollingDataRouter {

    /**
     * 將輪詢結果路由至對應設備處理器
     *
     * @param deviceName    設備名稱（來源於 PlcProperties）
     * @param tag           標示區域
     * @param areaType      資料區類型（"B" / "W"）
     * @param startAddress  起始位址
     * @param data          位元組資料
     * @param snapshotTime  本次輪詢擷取時間
     */
    void route(String deviceName, String tag, String areaType, int startAddress, byte[] data, Instant snapshotTime);
}
