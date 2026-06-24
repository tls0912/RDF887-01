package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.OcrVerification;
import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

public interface OcrVerificationRepository {

    Optional<OcrVerification> findById(Long id);

    boolean save(OcrVerification entity);

    boolean update(OcrVerification entity);

    boolean deleteById(Long id);

    List<OcrVerification> findAll();

    /** 依 container_main_id 取得最新一筆驗證紀錄（目前一顆 tray 一筆） */
    Optional<OcrVerification> findByContainerMainId(Long containerMainId);

    /** 依 anchor(container_main_id) + ref_container_id 取得最新一筆驗證紀錄（避免 ref 變動造成誤續跑） */
    Optional<OcrVerification> findLatestByContainerMainIdAndRefContainerId(Long containerMainId, Long refContainerId);

    /** 依 S073 TID 取得驗證紀錄（每個 TID 對應一筆） */
    Optional<OcrVerification> findByS073Tid(String s073Tid);

    /** 取出待人工判定的清單（state=ACTIVE 且 manual_decision=PENDING），依 created_time 由舊到新 */
    List<OcrVerification> findPendingManualDecisions(int limit);

    /** 只更新自動欄位（TR3 自判 / 比對用） */
    boolean updateAutoFields(OcrVerification entity);

/** 新增：只更新 S073 相關欄位（TID / 狀態 / 圖片路徑） */
    boolean updateS073Fields(OcrVerification entity);
}
