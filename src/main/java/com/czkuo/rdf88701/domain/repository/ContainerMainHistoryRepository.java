package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.ContainerMainHistory;
import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

public interface ContainerMainHistoryRepository {

    Optional<ContainerMainHistory> findById(Long id);

    boolean save(ContainerMainHistory entity);

    boolean update(ContainerMainHistory entity);

    boolean deleteById(Long id);

    List<ContainerMainHistory> findAll();

    List<ContainerMainHistory> findByContainerMainId(Long containerMainId);
}
