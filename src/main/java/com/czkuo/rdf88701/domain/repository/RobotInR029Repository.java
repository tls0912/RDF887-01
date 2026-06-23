package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.RobotInR029;
import java.util.List;
import java.util.Optional;

/**
 * RobotInR029 倉儲介面（入站 R029 主檔）
 * <p>
 * 對應資料表：<b>robot_in_r029</b>（PK = log_id，等同於 mqtt_message_log.id）
 * <br/>用途：保存 R029 指令 MESSAGE 的主檔欄位（count / tray_type / tray_desc）。
 * <br/>注意：每一筆入站 R029 COMMAND 僅對應一筆主檔（log_id 唯一）。
 * <p>
 * 說明：
 * - LOT 清單請參考「robot_in_r029_lot」表，建議使用 {@code RobotInR029LotRepository} 來操作。
 */
public interface RobotInR029Repository {

    // ===== 基本 CRUD =====

    /** 依主鍵（log_id）查詢主檔 */
    Optional<RobotInR029> findById(Long id);

    /** 新增一筆主檔資料（log_id 為外部指定：mqtt_message_log.id） */
    boolean save(RobotInR029 entity);

    /** 依主鍵（log_id）更新主檔資料 */
    boolean update(RobotInR029 entity);

    /** 依主鍵（log_id）刪除主檔資料（連帶刪除會由 FK on delete cascade 處理 LOT 明細） */
    boolean deleteById(Long id);

    /** 查詢全部主檔（通常僅用於管理/稽核，不建議在熱路徑呼叫） */
    List<RobotInR029> findAll();
}
