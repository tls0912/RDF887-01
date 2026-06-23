package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.MqttMessageLog;
import java.util.List;
import java.util.Optional;

/**
 * MqttMessageLogRepository
 * - 提供對 mqtt_message_log 表的資料存取抽象介面
 * - 用於儲存 MQTT 指令與 ACK 的傳輸歷程
 */
public interface MqttMessageLogRepository {

    /**
     * 查詢指定主鍵 ID 的紀錄
     */
    Optional<MqttMessageLog> findById(Long id);

    /**
     * 新增一筆 command 或 ack 訊息記錄
     *
     * @param entity 欲儲存的資料
     * @return 是否儲存成功
     */
    boolean save(MqttMessageLog entity);

    boolean saveBatch(List<MqttMessageLog> entities);

    /**
     * 更新一筆現有訊息記錄（依據 id）
     *
     * @param entity 欲更新的資料（必須有 ID）
     * @return 是否更新成功
     */
    boolean update(MqttMessageLog entity);

    /**
     * 刪除指定 ID 的訊息記錄
     */
    boolean deleteById(Long id);

    /**
     * 查詢所有紀錄（⚠️ 非大量使用，不建議用於生產系統）
     */
    List<MqttMessageLog> findAll();

    /**
     * 判斷是否存在某筆 command（由我方發送）
     *
     * @param tid     指令的 TID
     * @param cmdId   指令代碼（如 S001）
     * @param sender  我方系統（如 SAA）
     * @param receiver 對方系統（如 SEEC / ASE）
     * @return true 表示已存在該筆我方發出的 command
     */
    boolean existsSentCommand(String tid, String cmdId, String sender, String receiver);

    /**
     * 查詢同一筆 TID 下的所有相關紀錄（含 COMMAND / ACK）
     * 可用於 UI 查歷程或 debug
     */
    List<MqttMessageLog> findAllByTid(String tid);

    /**
     * 是否已送過「ACK=START」
     * 條件：
     * - tid = ?
     * - cmd_id = ?
     * - message_type = 'ACK'
     * - result = 'START'
     *
     * @return true 表示至少存在一筆符合條件的紀錄
     */
    boolean existsAckStart(String tid, String cmdId);
}
