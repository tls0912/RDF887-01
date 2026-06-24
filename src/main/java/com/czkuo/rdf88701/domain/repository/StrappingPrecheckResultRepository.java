package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.StrappingPrecheckResult;
import java.util.List;
import java.util.Optional;

/**
 * StrappingPrecheckResultRepository
 * - 保留原本基於 id 的 CRUD
 * - 新增以 tid 為核心的查詢/刪除/存取方法
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public interface StrappingPrecheckResultRepository {

    /* ==================== 原本的 CRUD (by id) ==================== */

    Optional<StrappingPrecheckResult> findById(Long id);

    boolean save(StrappingPrecheckResult entity);

    boolean update(StrappingPrecheckResult entity);

    boolean deleteById(Long id);

    List<StrappingPrecheckResult> findAll();


    /* ==================== 新增的 (by tid) ==================== */

    /**
     * 依 TID 查詢
     */
    Optional<StrappingPrecheckResult> findByTid(String tid);

    /**
     * 新增或更新（依 TID 判斷：存在則更新，不存在則插入）
     */
    boolean saveOrUpdateByTid(StrappingPrecheckResult entity);

    /**
     * 刪除（依 TID）
     */
    boolean deleteByTid(String tid);
}
