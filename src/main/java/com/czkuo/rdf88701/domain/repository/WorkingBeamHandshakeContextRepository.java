package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.domain.plc.state.workingbeam.WorkingBeamHandshakeContext;

/**
 * WorkingBeam 任務握手上下文快取/儲存介面
 * 可實作為記憶體、Redis 或 DB
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public interface WorkingBeamHandshakeContextRepository {

    /**
     * 取得上下文，若不存在則初始化
     * @param taskId 任務 ID
     * @param beamName WorkingBeam 裝置 Name
     * @return 握手上下文
     */
    WorkingBeamHandshakeContext getOrInit(Long taskId, String beamName);

    /**
     * 儲存上下文
     */
    void save(WorkingBeamHandshakeContext context);

    /**
     * 清除上下文
     */
    void clear(Long taskId);
}
