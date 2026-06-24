package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.TransferTaskHistory;
import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

public interface TransferTaskHistoryRepository {

    Optional<TransferTaskHistory> findById(Long id);

    boolean save(TransferTaskHistory entity);

    boolean update(TransferTaskHistory entity);

    boolean deleteById(Long id);

    List<TransferTaskHistory> findAll();
}
