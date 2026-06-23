package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.TransferRequestHistory;
import java.util.List;
import java.util.Optional;

public interface TransferRequestHistoryRepository {

    Optional<TransferRequestHistory> findById(Long id);

    boolean save(TransferRequestHistory entity);

    boolean update(TransferRequestHistory entity);

    boolean deleteById(Long id);

    List<TransferRequestHistory> findAll();
}
