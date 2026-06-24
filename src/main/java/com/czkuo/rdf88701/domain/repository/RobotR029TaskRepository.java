package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.RobotR029Task;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

public interface RobotR029TaskRepository {

    Optional<RobotR029Task> findById(Long id);

    Optional<RobotR029Task> findByLogId(Long logId);

    Optional<RobotR029Task> findLatestByTid(String tid);

    List<RobotR029Task> findOpen();

    List<RobotR029Task> findOpenLimited(int limit);

    /**
     * 取最近一段時間內「已終結」的 R029 任務（歷史用）
     * 條件：
     *   - external_last_result IN ('END','NG','CANCEL')
     *   - created_time >= since 或 external_last_time >= since
     * 排序：
     *   - created_time DESC, id DESC
     * limit：最大筆數
     */
    List<RobotR029Task> findRecentSince(LocalDateTime since, int limit);

    boolean save(RobotR029Task entity);

    boolean update(RobotR029Task entity);

    boolean updateByLogId(RobotR029Task entity);

    boolean updateInboxIdByLogId(Long logId, Long inboxId);

    boolean deleteById(Long id);

    List<RobotR029Task> findAll();

    int countProcessingByLane(String lane); // lane=MAIN/SUB

    /** 嘗試把某 logId 對應的任務設置 lane 並從 QUEUED→PROCESSING（原子性） */
    boolean trySetLaneAndProcessingByLogId(Long logId, String lane);

    /** 以 logId 切換狀態（from→to），回傳是否成功（樂觀鎖） */
    boolean updateStateByLogId(Long logId, String fromState, String toState, String reason);

    /**
     * 取指定 lane（MAIN/SUB）「任一筆正在 PROCESSING 中」的任務
     * - 主要用於公蓋區監控，取得 trayType 等資訊
     * - 排序策略：created_time ASC, id ASC（越早的優先）
     */
    Optional<RobotR029Task> findFirstProcessingByLane(String lane);

}
