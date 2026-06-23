package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.Transfer;
import java.util.List;
import java.util.Optional;

public interface TransferRepository {

    Optional<Transfer> findById(Long id);

    boolean save(Transfer entity);

    boolean update(Transfer entity);

    boolean deleteById(Long id);

    List<Transfer> findAll();
}
