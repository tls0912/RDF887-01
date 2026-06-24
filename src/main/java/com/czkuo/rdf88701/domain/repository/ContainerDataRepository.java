package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.ContainerData;
import java.util.List;
import java.util.Optional;

/**
 * ContainerData 的領域倉儲介面
 * - 單筆策略：每個 containerMainId 僅允許存在一筆
 * - 以 containerMainId 為主的存取鍵，並保留 id 型 CRUD 以相容既有程式
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public interface ContainerDataRepository {

    // ===== 既有 CRUD（以 id 為鍵，保留相容性） =====

    Optional<ContainerData> findById(Long id);

    boolean save(ContainerData entity);

    boolean update(ContainerData entity);

    boolean deleteById(Long id);

    List<ContainerData> findAll();

    // ===== 以 containerMainId 為主的單筆策略 API =====

    /** 依 containerMainId 查詢單筆紀錄（單筆策略） */
    Optional<ContainerData> findByContainerMainId(Long containerMainId);

    /**
     * Upsert（相容版）：
     * - 不存在則 INSERT
     * - 已存在則僅以非 NULL 欄位覆蓋（避免誤清空）
     * - 不處理 content_kind（維持既有行為）
     */
    boolean upsertByContainerMainId(Long containerMainId,
                                    Integer estimatedQuantity,
                                    String ocrText1,
                                    String ocrText2,
                                    Integer verifiedQuantity);

    /**
     * Upsert（含 content_kind）：
     * - 不存在則 INSERT
     * - 已存在則僅以非 NULL 欄位覆蓋
     * - content_kind 僅在舊值為 'UNKNOWN' 時才覆蓋（避免覆蓋已確認型態）
     */
    boolean upsertByContainerMainId(Long containerMainId,
                                    Integer estimatedQuantity,
                                    String ocrText1,
                                    String ocrText2,
                                    Integer verifiedQuantity,
                                    String contentKind);

    /**
     * Upsert 層別（工蓋 / 上蓋 / 一般）：
     * - 不存在則 INSERT 將非 NULL 欄位寫入
     * - 已存在則僅覆蓋對應「非 NULL」欄位
     * - 不改動未提供的欄位與 content_kind
     */
    boolean upsertLayers(Long containerMainId,
                         Integer workCoverLayers,
                         Integer coverLayers,
                         Integer productLayers);

    /** 便捷：僅 upsert「推估層數」 */
    default boolean upsertEstimated(Long containerMainId, Integer estimatedQuantity) {
        return upsertByContainerMainId(containerMainId, estimatedQuantity, null, null, null);
    }

    /** 便捷：僅 upsert「驗證層數（總數）」 */
    default boolean upsertVerified(Long containerMainId, Integer verifiedQuantity) {
        return upsertByContainerMainId(containerMainId, null, null, null, verifiedQuantity);
    }

    /** 便捷：僅 upsert「OCR 文字」 */
    default boolean upsertOcr(Long containerMainId, String ocrText1, String ocrText2) {
        return upsertByContainerMainId(containerMainId, null, ocrText1, ocrText2, null);
    }

    /** 便捷：僅 upsert「工蓋層數」 */
    default boolean upsertWorkCoverLayers(Long containerMainId, Integer workCoverLayers) {
        return upsertLayers(containerMainId, workCoverLayers, null, null);
    }

    /** 便捷：僅 upsert「上蓋層數」 */
    default boolean upsertCoverLayers(Long containerMainId, Integer coverLayers) {
        return upsertLayers(containerMainId, null, coverLayers, null);
    }

    /** 便捷：僅 upsert「一般層數」 */
    default boolean upsertProductLayers(Long containerMainId, Integer productLayers) {
        return upsertLayers(containerMainId, null, null, productLayers);
    }

    /** 便捷：僅 upsert「內容型態」 */
    default boolean upsertContentKind(Long containerMainId, String contentKind) {
        return upsertByContainerMainId(containerMainId, null, null, null, null, contentKind);
    }

    /** 僅在舊值為 UNKNOWN 時，設定 content_kind（Site#35 預設用） */
    boolean setContentKindIfUnknown(Long containerMainId, String contentKind);

    /** 直接覆寫 content_kind（需謹慎使用，通常在量測/人工確認後） */
    boolean updateContentKind(Long containerMainId, String contentKind);

    /** 取得 content_kind（若無資料回傳 empty） */
    Optional<String> getContentKind(Long containerMainId);

    /**
     * 只在層別欄位為 NULL 時，依 content_kind 與 verified_quantity 反推：
     * - ALL_COVER         → cover = verified, product = 0
     * - NORMAL_WITH_COVER → cover = (verified>0 ? 1 : 0), product = verified - cover
     * - NORMAL_NO_COVER   → cover = 0, product = verified
     * - EMPTY             → cover = 0, product = 0
     * - 其他/NULL         → 視為 NORMAL_WITH_COVER
     *
     * 不覆蓋已有數值；不處理工蓋（work_cover_layers）。
     */
    boolean fillLayersByKindIfUnset(Long containerMainId);

    /** 依 containerMainId 刪除（單筆策略） */
    boolean deleteByContainerMainId(Long containerMainId);
}
