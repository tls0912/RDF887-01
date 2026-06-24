package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.MqttConnectionState;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MqttConnectionStateRepository
 * - 管理 MQTT 對應系統的連線狀態（mqtt_connection_state 資料表）
 * - 提供 CRUD 與業務邏輯專用查詢方法
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public interface MqttConnectionStateRepository {

    // === 基本 CRUD ===

    /**
     * 根據主鍵 ID 查詢紀錄
     *
     * @param id 主鍵 ID
     * @return 查詢結果（如不存在則為 empty）
     */
    Optional<MqttConnectionState> findById(Long id);

    /**
     * 新增一筆連線狀態紀錄
     *
     * @param entity 欲新增的物件
     * @return 是否新增成功
     */
    boolean insert(MqttConnectionState entity);

    /**
     * 更新一筆已存在的連線狀態
     *
     * @param entity 欲更新的物件（需包含 ID 或 remote_system）
     * @return 是否更新成功
     */
    boolean update(MqttConnectionState entity);

    /**
     * 根據主鍵 ID 刪除紀錄
     *
     * @param id 欲刪除的主鍵
     * @return 是否刪除成功
     */
    boolean deleteById(Long id);

    /**
     * 查詢全部紀錄（通常僅用於初始化或管理介面）
     *
     * @return 所有系統連線狀態
     */
    List<MqttConnectionState> findAll();

    // === 業務邏輯專用 ===

    /**
     * 透過 remote_system 查詢特定對象的連線狀態
     *
     * @param remoteSystem 對方系統代號（如 ASE / SEEC）
     * @return 查詢結果（如不存在則為 empty）
     */
    Optional<MqttConnectionState> findByRemoteSystem(String remoteSystem);

    /**
     * 依 remote_system 執行 upsert 操作（若存在則更新，否則新增）
     *
     * @param entity 欲儲存或更新的紀錄
     * @return 是否成功
     */
    boolean upsertByRemoteSystem(MqttConnectionState entity);

    /**
     * 查詢目前所有處於斷線狀態的對象（connected = false）
     *
     * @return 所有已斷線對象的狀態清單
     */
    List<MqttConnectionState> findDisconnectedSystems();

    /**
     * 查詢心跳已逾時（尚未收到 S002 ACK）的連線對象
     * 僅會查出目前 connected = true 且 last_heartbeat_time < now - timeoutSeconds
     *
     * @param timeoutSeconds 心跳逾時秒數門檻
     * @return 心跳異常對象的清單
     */
    List<MqttConnectionState> findExpiredHeartbeat(long timeoutSeconds);

    /**
     * 更新指定對象的 last_heartbeat_time（用於 S002 ACK 接收到時）
     *
     * @param remoteSystem 對方系統代號
     * @param heartbeatTime 心跳時間
     * @return 是否成功更新
     */
    boolean updateHeartbeatTime(String remoteSystem, LocalDateTime heartbeatTime);

    /**
     * 將指定對象標記為已斷線（connected = false）
     * 一般用於心跳逾時或連線失敗時的標記處理
     *
     * @param remoteSystem 對方系統代號
     * @return 是否更新成功
     */
    boolean markAsDisconnected(String remoteSystem);

    /**
     * 檢查指定系統是否為連線狀態。
     *
     * @param remoteSystem 對方系統代號
     * @return true 表示已連線；false 表示未連線或不存在
     */
    boolean isConnected(String remoteSystem);

    /**
     * 查詢所有目前為 connected = true 的對象名稱清單。
     * 可用於 UI 顯示線上節點列表。
     *
     * @return 系統代號清單（僅限已連線）
     */
    List<String> listCurrentlyConnectedSystems();

    /**
     * 回傳所有系統的目前連線狀態對照表。
     * 可用於前端呈現儀表板狀態列表、批次查詢等場景。
     *
     * @return Map：key = 系統代號，value = 是否已連線
     */
    Map<String, Boolean> getAllConnectionStatusMap();
}
