package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.RobotR031Task;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RobotR031TaskRepository {

    Optional<RobotR031Task> findById(Long id);

    Optional<RobotR031Task> findByLogId(Long logId);

    Optional<RobotR031Task> findLatestByTid(String tid);

    List<RobotR031Task> findOpen();

    List<RobotR031Task> findOpenLimited(int limit);

    /**
     * 取最近一段時間內「已終結」的 R031 任務（歷史用）
     * 條件：
     *   - external_last_result IN ('END','FAIL','CANCEL')
     *   - created_time >= since 或 external_last_time >= since
     * 排序：
     *   - created_time DESC, id DESC
     * limit：最大筆數
     */
    List<RobotR031Task> findRecentSince(LocalDateTime since, int limit);

    boolean save(RobotR031Task entity);

    boolean update(RobotR031Task entity);

    boolean updateByLogId(RobotR031Task entity);

    boolean updateInboxIdByLogId(Long logId, Long inboxId);

    boolean deleteById(Long id);

    List<RobotR031Task> findAll();
}
