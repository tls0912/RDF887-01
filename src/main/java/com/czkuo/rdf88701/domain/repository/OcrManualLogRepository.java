package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.OcrManualLog;

import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

public interface OcrManualLogRepository {

    Optional<OcrManualLog> findById(Long id);

    boolean save(OcrManualLog entity);

    boolean update(OcrManualLog entity);

    boolean deleteById(Long id);

    List<OcrManualLog> findAll();

    /** 依 container_main_id 取得最新一筆驗證紀錄（目前一顆 tray 一筆） */
    Optional<OcrManualLog> findByContainerMainId(Long containerMainId);

    /** 依 anchor(container_main_id) + ref_container_id 取得最新一筆驗證紀錄（避免 ref 變動造成誤續跑） */
    Optional<OcrManualLog> findLatestByContainerMainIdAndRefContainerId(Long containerMainId, Long refContainerId);


}
