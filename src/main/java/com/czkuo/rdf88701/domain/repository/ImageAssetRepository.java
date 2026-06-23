package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.ImageAsset;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ImageAssetRepository {

    Optional<ImageAsset> findById(Long id);

    boolean save(ImageAsset entity);

    boolean update(ImageAsset entity);

    boolean deleteById(Long id);

    List<ImageAsset> findAll();

    // ===== 便捷查詢 =====
    List<ImageAsset> findByRef(String refType, Long refId);

    List<ImageAsset> findByRefAndRole(String refType, Long refId, String role);

    Optional<ImageAsset> findLatestByRef(String refType, Long refId);

    Optional<ImageAsset> findBySha256(String sha256);

    List<ImageAsset> findBySceneSince(String scene, LocalDateTime since, int limit);

    /**
     * 依 retention_days 清理過期索引（以 created_time 為基準）
     * @return 受影響列數
     */
    int deleteExpiredByRetention(LocalDateTime now);
}
