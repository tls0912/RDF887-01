package com.czkuo.rdf88701.domain.repository;


import com.czkuo.rdf88701.domain.plc.state.gripper.GripperHandshakeContext;

/**
 * Gripper 任務握手上下文快取/儲存介面
 * 可實作為記憶體、Redis 或資料庫
 */
public interface GripperHandshakeContextRepository {

    /**
     * 取得上下文，若不存在則初始化
     * @param taskId 任務 ID
     * @param gripperName Gripper 裝置名稱
     * @return 握手上下文
     */
    GripperHandshakeContext getOrInit(Long taskId, String gripperName);

    /**
     * 儲存上下文
     * @param context 上下文物件
     */
    void save(GripperHandshakeContext context);

    /**
     * 清除上下文（任務完成或失敗後）
     * @param taskId 任務 ID
     */
    void clear(Long taskId);
}
