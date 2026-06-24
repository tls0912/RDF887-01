package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.ButtonLog;
import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

public interface ButtonLogRepository {

    Optional<ButtonLog> findById(Long id);

    boolean save(ButtonLog entity);

    boolean update(ButtonLog entity);

    boolean deleteById(Long id);

    List<ButtonLog> findAll();

    /**
     * 取得指定區域的最後一筆 seq_index（最大值）
     * - 給 ButtonReportMonitor 在啟動時初始化用
     */
    Optional<Integer> findLastSeqIndexByArea(String area);

    /**
     * 檢查指定區域 + seq_index 是否已存在
     * - 防止重複寫入
     */
    boolean existsByAreaAndSeqIndex(String area, int seqIndex);
}
