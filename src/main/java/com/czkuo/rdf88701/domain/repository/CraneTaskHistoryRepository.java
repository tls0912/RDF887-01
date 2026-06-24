package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.application.dto.query.CraneTaskHistoryQuery;
import com.czkuo.rdf88701.common.dto.PageResult;
import com.czkuo.rdf88701.infra.entity.CraneTaskHistory;

import java.util.List;
import java.util.Optional;

/**
 * Crane 任務歷史紀錄 Repository
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public interface CraneTaskHistoryRepository {

    /**
     * 查詢單筆紀錄
     */
    Optional<CraneTaskHistory> findById(Long id);

    /**
     * 查詢所有歷史紀錄（不含條件）
     */
    List<CraneTaskHistory> findAll();

    /**
     * 根據查詢條件查詢多筆紀錄（不分頁）
     */
    List<CraneTaskHistory> findByCondition(CraneTaskHistoryQuery query);

    /**
     * 根據查詢條件查詢分頁結果
     */
    PageResult<CraneTaskHistory> findPageByCondition(CraneTaskHistoryQuery query);

    /**
     * 儲存歷史紀錄
     */
    boolean save(CraneTaskHistory entity);

    /**
     * 更新歷史紀錄（以 ID 為主）
     */
    boolean update(CraneTaskHistory entity);

    /**
     * 刪除歷史紀錄
     */
    boolean deleteById(Long id);
}
