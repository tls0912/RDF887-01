package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.LabelingInfo;
import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

public interface LabelingInfoRepository {

    Optional<LabelingInfo> findById(Long id);

    Optional<LabelingInfo> findByRequestKey(String requestKey);

    boolean save(LabelingInfo entity);

    boolean update(LabelingInfo entity);

    boolean updateStatus(Long id, String status);

    boolean deleteById(Long id);

    List<LabelingInfo> findAll();

    // ===== Watermark / Claim（S020 之後只取最新）=====
    Long selectMaxId();

    /** 站點挑一筆 READY（最早），使用 FOR UPDATE，請在 @Transactional 範圍內呼叫 */
    Optional<LabelingInfo> selectReadyForClaim(String siteCode);

    /** 綁定站點/容器/標籤號（只限 READY），回傳是否成功 */
    boolean bindToSiteAndContainer(Long id, String siteCode, Long containerMainId, Integer labelNo);

    /** 只挑「某 id 之後」出現的第一筆 READY（FOR UPDATE）」 */
    Optional<LabelingInfo> selectReadyAfterId(String siteCode, Long afterId);

    /** 依 container/site 查一筆 READY（無鎖，僅查詢） */
    Optional<LabelingInfo> findReady(Long containerMainId, String siteCode);
}
