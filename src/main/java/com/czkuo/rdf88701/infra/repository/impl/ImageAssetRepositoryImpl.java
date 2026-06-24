package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.ImageAssetRepository;
import com.czkuo.rdf88701.infra.entity.ImageAsset;
import com.czkuo.rdf88701.infra.mapper.ImageAssetMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Repository
public class ImageAssetRepositoryImpl implements ImageAssetRepository {

    private final ImageAssetMapper imageAssetMapper;

    public ImageAssetRepositoryImpl(ImageAssetMapper imageAssetMapper) {
        this.imageAssetMapper = imageAssetMapper;
    }

    @Override
    public Optional<ImageAsset> findById(Long id) {
        return Optional.ofNullable(imageAssetMapper.selectById(id));
    }

    @Override
    public boolean save(ImageAsset entity) {
        return imageAssetMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(ImageAsset entity) {
        return imageAssetMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return imageAssetMapper.deleteById(id) > 0;
    }

    @Override
    public List<ImageAsset> findAll() {
        return imageAssetMapper.selectList(new QueryWrapper<>());
    }

    // ================= 便捷查詢 =================

    @Override
    public List<ImageAsset> findByRef(String refType, Long refId) {
        if (refId == null) return List.of();
        QueryWrapper<ImageAsset> qw = new QueryWrapper<ImageAsset>()
                .eq("ref_type", upper(refType))
                .eq("ref_id", refId)
                .orderByAsc("created_time").orderByAsc("id");
        return imageAssetMapper.selectList(qw);
    }

    @Override
    public List<ImageAsset> findByRefAndRole(String refType, Long refId, String role) {
        if (refId == null || isBlank(role)) return List.of();
        QueryWrapper<ImageAsset> qw = new QueryWrapper<ImageAsset>()
                .eq("ref_type", upper(refType))
                .eq("ref_id", refId)
                .eq("role", role.trim())
                .orderByDesc("created_time").orderByDesc("id");
        return imageAssetMapper.selectList(qw);
    }

    @Override
    public Optional<ImageAsset> findLatestByRef(String refType, Long refId) {
        if (refId == null) return Optional.empty();
        QueryWrapper<ImageAsset> qw = new QueryWrapper<ImageAsset>()
                .eq("ref_type", upper(refType))
                .eq("ref_id", refId)
                .orderByDesc("created_time").orderByDesc("id")
                .last("LIMIT 1");
        return Optional.ofNullable(imageAssetMapper.selectOne(qw));
    }

    @Override
    public Optional<ImageAsset> findBySha256(String sha256) {
        if (isBlank(sha256)) return Optional.empty();
        QueryWrapper<ImageAsset> qw = new QueryWrapper<ImageAsset>()
                .eq("sha256", sha256.trim())
                .last("LIMIT 1");
        return Optional.ofNullable(imageAssetMapper.selectOne(qw));
    }

    @Override
    public List<ImageAsset> findBySceneSince(String scene, LocalDateTime since, int limit) {
        if (isBlank(scene) || since == null) return List.of();
        QueryWrapper<ImageAsset> qw = new QueryWrapper<ImageAsset>()
                .eq("scene", scene.trim())
                .ge("created_time", since)
                .orderByDesc("created_time").orderByDesc("id")
                .last("LIMIT " + Math.max(1, limit));
        return imageAssetMapper.selectList(qw);
    }

    @Override
    public int deleteExpiredByRetention(LocalDateTime now) {
        // 用 created_time 當基準；MySQL: created_time < DATE_SUB(:now, INTERVAL retention_days DAY)
        // MyBatis-Plus 用 apply 注入函式
        QueryWrapper<ImageAsset> qw = new QueryWrapper<ImageAsset>()
                .isNotNull("retention_days")
                .apply("created_time < DATE_SUB({0}, INTERVAL retention_days DAY)", now);
        return imageAssetMapper.delete(qw);
    }

    // ================= utils =================

    private static String nvl(String s) { return s == null ? "" : s; }

    private static String upper(String s) {
        return s == null ? null : s.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
}
