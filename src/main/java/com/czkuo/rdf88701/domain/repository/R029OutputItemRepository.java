package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.R029OutputItem;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface R029OutputItemRepository {

    Optional<R029OutputItem> findById(Long id);

    boolean save(R029OutputItem entity);

    boolean update(R029OutputItem entity);

    boolean deleteById(Long id);

    List<R029OutputItem> findAll();

    // ====== 新增 ======
    Optional<R029OutputItem> findOneByTaskIdAndNewCarrierId(Long taskId, String newCarrierId);

    List<R029OutputItem> findByTaskId(Long taskId);

    int countByTaskId(Long taskId);

    boolean deleteByTaskId(Long taskId);

    // 狀態相關
    int countByTaskIdAndStates(Long taskId, Collection<String> states);

    List<R029OutputItem> findByTaskIdAndStateIn(Long taskId, Collection<String> states);

    List<R029OutputItem> findByTaskIdAndStateNotIn(Long taskId, Collection<String> states);

    boolean updateStateById(Long id, String state);

    boolean updateStateByTaskAndCarrier(Long taskId, String newCarrierId, String state);

    // 去重的新載具清單（終結判斷可能用得到）
    List<String> findDistinctNewCarrierIdsByTaskId(Long taskId);
}
