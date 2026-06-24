package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.TtRecordItem;
import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

public interface TtRecordItemRepository {

    Optional<TtRecordItem> findById(Long id);

    boolean save(TtRecordItem entity);

    boolean saveBatch(List<TtRecordItem> items);

    boolean update(TtRecordItem entity);

    boolean deleteById(Long id);

    List<TtRecordItem> findAll();

    List<TtRecordItem> findByRecordId(Long recordId);
}
