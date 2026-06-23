package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.CraneTaskFollowUpRecord;
import java.util.List;
import java.util.Optional;

public interface CraneTaskFollowUpRecordRepository {

    Optional<CraneTaskFollowUpRecord> findById(Long id);

    boolean save(CraneTaskFollowUpRecord entity);

    boolean update(CraneTaskFollowUpRecord entity);

    boolean deleteById(Long id);

    List<CraneTaskFollowUpRecord> findAll();

    /**
     * 依 originalTaskId 查詢最新補償紀錄（通常用於找 root_task_id）
     */
    Optional<CraneTaskFollowUpRecord> findOriginalTaskId(Long taskId);

    /**
     * 查詢是否已有針對某個任務產生的補償紀錄（即此任務是否本身是補償目標）
     */
    Optional<CraneTaskFollowUpRecord> findByFollowUpTaskId(Long taskId);
}
