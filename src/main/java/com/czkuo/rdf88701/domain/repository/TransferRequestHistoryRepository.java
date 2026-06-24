package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.TransferRequestHistory;
import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

public interface TransferRequestHistoryRepository {

    Optional<TransferRequestHistory> findById(Long id);

    boolean save(TransferRequestHistory entity);

    boolean update(TransferRequestHistory entity);

    boolean deleteById(Long id);

    List<TransferRequestHistory> findAll();
}
