package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.OcrTask;
import java.util.List;
import java.util.Optional;

public interface OcrTaskRepository {

    Optional<OcrTask> findById(Long id);

    boolean save(OcrTask entity);

    boolean update(OcrTask entity);

    boolean deleteById(Long id);

    List<OcrTask> findAll();

    /** 該 container 最新的一筆任務（依 created_time DESC, task_id DESC） */
    Optional<OcrTask> findLatestByContainerId(Long containerMainId);

    /** 該 container 是否存在「未完成」的任務（非 COMPLETED/FAILED/CANCELLED） */
    boolean existsUnfinishedForContainer(Long containerMainId);

    /** 撈出未完成任務，依建立時間由舊到新，限制筆數（limit<=0 則給預設 50） */
    List<OcrTask> findUnfinished(int limit);
}
