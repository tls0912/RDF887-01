package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.RobotR008Task;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

public interface RobotR008TaskRepository {

    Optional<RobotR008Task> findById(Long id);

    Optional<RobotR008Task> findByLogId(Long logId);

    Optional<RobotR008Task> findLatestByTid(String tid);

    List<RobotR008Task> findOpen();

    List<RobotR008Task> findOpenLimited(int limit);

    List<String> findBinTypeByCarrierId(String carrierId);

    /**
     * 取最近一段時間內「已終結」的任務（歷史用）
     * 條件：
     * - external_last_result IN ('END','FAIL','CANCEL')
     * - created_time >= since 或 external_last_time >= since
     * 排序：
     * - created_time DESC, id DESC
     * limit：最大筆數
     */
    List<RobotR008Task> findRecentSince(LocalDateTime since, int limit);

    boolean save(RobotR008Task entity);

    boolean update(RobotR008Task entity);

    boolean updateByLogId(RobotR008Task entity);

    boolean updateInboxIdByLogId(Long logId, Long inboxId);

    boolean deleteById(Long id);

    List<RobotR008Task> findAll();
}
