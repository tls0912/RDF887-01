package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.RobotInR029Lot;
import java.util.List;
import java.util.Optional;

/**
 * RobotInR029Lot 倉儲介面（入站 R029 LOT 明細）
 * <p>
 * 對應資料表：robot_in_r029_lot（(log_id, lot_id) 唯一）
 * <br/>用途：保存 R029 指令中 LOT 清單；配合主檔 robot_in_r029 使用
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public interface RobotInR029LotRepository {

    // ===== 基本 CRUD（保留原樣） =====
    Optional<RobotInR029Lot> findById(Long id);
    boolean save(RobotInR029Lot entity);
    boolean update(RobotInR029Lot entity);
    boolean deleteById(Long id);
    List<RobotInR029Lot> findAll();

    // ===== R029 業務常用擴充 API =====

    /** 依 logId 取得該筆 R029 的 LOT 明細（整筆實體） */
    List<RobotInR029Lot> findByLogId(Long logId);

    /** 依 logId 取得 LOT_ID 清單（僅回傳字串，方便 Walker 直接使用） */
    List<String> findCarrierIdsByLogId(Long logId);

    List<String> findIdByCarrierId(String carrierId);

    /**
     * 批次新增 LOT 清單（去重由 DB 唯一鍵保障）
     * <p>建議實作：INSERT IGNORE INTO robot_in_r029_lot(log_id, lot_id) VALUES ...</p>
     * @param logId  主檔 log_id（= mqtt_message_log.id）
     * @param lotIds LOT_ID 清單（呼叫端可先 trim/distinct）
     * @return 是否至少成功寫入一筆
     */
    boolean batchUpsert(Long logId, List<String> lotIds);

    /** 依 logId + lotId 移除單筆 LOT（修正/重灌時使用） */
    boolean deleteByLogIdAndLotId(Long logId, String lotId);

    /** 依 logId 移除該筆 R029 的所有 LOT（通常主檔刪除由 FK cascade 處理，這裡提供顯式清除） */
    boolean deleteByLogId(Long logId);
}
