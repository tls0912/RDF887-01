package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.SafetyPoint;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SafetyPoint 存取介面
 * <p>
 * 封裝對資料表 {@code safety_point} 的常用操作，
 * 並提供便於監控/解碼流程使用的輔助查詢（例如依啟用狀態查詢、建立地址對照表）。
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public interface SafetyPointRepository {

    /**
     * 依主鍵查詢單筆安全點位。
     *
     * @param id 資料表主鍵 ID
     * @return 查到時回傳 {@code Optional.of(entity)}；查無回傳 {@code Optional.empty()}
     */
    Optional<SafetyPoint> findById(Long id);

    /**
     * 以 PLC 位址字串查詢單筆安全點位（大小寫不敏感）。
     * 會先把參數做 {@code trim().toUpperCase(Locale.ROOT)} 再查詢。
     */
    Optional<SafetyPoint> findByAddrExpr(String addrExpr);

    /**
     * 新增一筆安全點位。
     *
     * @param entity 要新增的實體
     * @return 影響筆數 &gt; 0 則回傳 {@code true}，否則 {@code false}
     */
    boolean save(SafetyPoint entity);

    /**
     * 以主鍵更新一筆安全點位。
     *
     * @param entity 含主鍵的實體（其餘欄位為欲更新值）
     * @return 影響筆數 &gt; 0 則回傳 {@code true}，否則 {@code false}
     */
    boolean update(SafetyPoint entity);

    /**
     * 以主鍵刪除一筆安全點位。
     *
     * @param id 資料表主鍵 ID
     * @return 影響筆數 &gt; 0 則回傳 {@code true}，否則 {@code false}
     */
    boolean deleteById(Long id);

    /**
     * 查詢所有安全點位（不過濾 enabled 狀態）。
     *
     * @return 全部點位清單
     */
    List<SafetyPoint> findAll();

    /**
     * 查詢所有「啟用中」的安全點位（{@code enabled = 'Y'}）。
     * <p>
     * 監控執行緒/輪詢邏輯通常只需要已啟用的點位，建議優先使用本方法。
     *
     * @return 啟用中點位清單
     */
    List<SafetyPoint> findAllEnabled();

    /**
     * 建立 {@code addr_expr -> point_id} 對照表，便於將 PLC 位址（如 {@code W1044.A}）
     * 快速映射到資料表主鍵 ID。
     *
     * @param onlyEnabled 若為 {@code true}，只納入 {@code enabled = 'Y'} 的點位；若為 {@code false}，則包含所有點位
     * @return 位址字串對應主鍵 ID 的 Map；若無資料回傳空 Map
     */
    Map<String, Long> buildAddrToIdMap(boolean onlyEnabled);
}
