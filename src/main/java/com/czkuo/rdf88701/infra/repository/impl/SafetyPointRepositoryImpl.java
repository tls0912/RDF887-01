package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.SafetyPointRepository;
import com.czkuo.rdf88701.infra.entity.SafetyPoint;
import com.czkuo.rdf88701.infra.mapper.SafetyPointMapper;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * SafetyPointRepository 實作（MyBatis-Plus）
 * <p>
 * 封裝對資料表 {@code safety_point} 的讀寫操作：
 * - 基本 CRUD
 * - 只取啟用點位（enabled='Y'）
 * - 產生 addr_expr -> point_id 對照表以利高速查詢
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Repository
public class SafetyPointRepositoryImpl implements SafetyPointRepository {

    private final SafetyPointMapper safetyPointMapper;

    public SafetyPointRepositoryImpl(SafetyPointMapper safetyPointMapper) {
        this.safetyPointMapper = safetyPointMapper;
    }

    /**
     * 依主鍵查詢單筆安全點位
     */
    @Override
    public Optional<SafetyPoint> findById(Long id) {
        return Optional.ofNullable(safetyPointMapper.selectById(id));
    }

    /**
     * 以 PLC 位址字串查詢單筆安全點位
     */
    @Override
    public Optional<SafetyPoint> findByAddrExpr(String addrExpr) {
        if (addrExpr == null) return Optional.empty();
        String norm = addrExpr.trim().toUpperCase(Locale.ROOT);
        QueryWrapper<SafetyPoint> qw = new QueryWrapper<SafetyPoint>()
                .eq("addr_expr", norm)
                .last("LIMIT 1");
        return Optional.ofNullable(safetyPointMapper.selectOne(qw));
    }

    /**
     * 新增一筆安全點位
     */
    @Override
    public boolean save(SafetyPoint entity) {
        return safetyPointMapper.insert(entity) > 0;
    }

    /**
     * 以主鍵更新一筆安全點位
     */
    @Override
    public boolean update(SafetyPoint entity) {
        return safetyPointMapper.updateById(entity) > 0;
    }

    /**
     * 以主鍵刪除一筆安全點位
     */
    @Override
    public boolean deleteById(Long id) {
        return safetyPointMapper.deleteById(id) > 0;
    }

    /**
     * 查詢所有安全點位（不過濾 enabled 狀態）
     */
    @Override
    public List<SafetyPoint> findAll() {
        return safetyPointMapper.selectList(new QueryWrapper<>());
    }

    /**
     * 查詢所有啟用中的安全點位（enabled='Y'）
     */
    @Override
    public List<SafetyPoint> findAllEnabled() {
        return safetyPointMapper.selectList(
                new QueryWrapper<SafetyPoint>().eq("enabled", "Y")
        );
    }

    /**
     * 產生位址到主鍵 ID 的對照表。
     * <p>
     * 注意：
     * 1) 會將 {@code addr_expr} 正規化為大寫並去除前後空白（例如 W1044.a -> W1044.A）
     * 2) 若資料庫中意外有重複位址（理論上被唯一鍵約束避免），以「先出現者」為準
     *
     * @param onlyEnabled true 表示只納入 enabled='Y' 的點位；false 表示所有點位
     * @return addr_expr（大寫） -> point_id 的對照表
     */
    @Override
    public Map<String, Long> buildAddrToIdMap(boolean onlyEnabled) {
        List<SafetyPoint> rows = onlyEnabled ? findAllEnabled() : findAll();
        return rows.stream().collect(Collectors.toMap(
                sp -> normalizeAddr(sp.getAddrExpr()),
                sp -> sp.getId(),
                (first, ignored) -> first,               // 若衝突保留先出現者
                LinkedHashMap::new                                  // 維持迭代順序（非必要）
        ));
    }

    /**
     * 將位址正規化（去頭尾空白 + 大寫）
     */
    private static String normalizeAddr(String addr) {
        return addr == null ? "" : addr.trim().toUpperCase(Locale.ROOT);
    }
}
