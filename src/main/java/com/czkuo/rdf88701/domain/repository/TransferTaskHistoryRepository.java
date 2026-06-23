package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.TransferTaskHistory;
import java.util.List;
import java.util.Optional;

public interface TransferTaskHistoryRepository {

    Optional<TransferTaskHistory> findById(Long id);

    boolean save(TransferTaskHistory entity);

    boolean update(TransferTaskHistory entity);

    boolean deleteById(Long id);

    List<TransferTaskHistory> findAll();
}
