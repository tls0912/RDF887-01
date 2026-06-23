package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.czkuo.rdf88701.application.service.History.ContainerDataHistoryInsertService;
import com.czkuo.rdf88701.domain.repository.ContainerDataRepository;
import com.czkuo.rdf88701.infra.entity.ContainerData;
import com.czkuo.rdf88701.infra.entity.ContainerMain;
import com.czkuo.rdf88701.infra.entity.CraneRequest;
import com.czkuo.rdf88701.infra.mapper.ContainerDataMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ContainerDataRepositoryImpl implements ContainerDataRepository {

    private final ContainerDataMapper containerDataMapper;
    private final ContainerDataHistoryInsertService containerDataHistoryInsertService;

    // ====== 既有 CRUD (id 為主) ======

    @Override
    public Optional<ContainerData> findById(Long id) {
        return Optional.ofNullable(containerDataMapper.selectById(id));
    }

    @Override
    public boolean save(ContainerData entity) {
        boolean success = containerDataMapper.insert(entity) > 0;
        if (success) {
            insertHistory(entity, "INSERT");
        }
        return success;
        //return containerDataMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(ContainerData entity) {
        boolean success = containerDataMapper.updateById(entity) > 0;
        if (success) {
            insertHistory(entity, "UPDATE");
        }
        return success;
        //return containerDataMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        ContainerData beforeDelete = containerDataMapper.selectById(id);
        boolean success = containerDataMapper.deleteById(id) > 0;
        if (success && beforeDelete != null) {
            insertHistory(beforeDelete, "DELETE");
        }
        return success;
        //return containerDataMapper.deleteById(id) > 0;
    }

    @Override
    public List<ContainerData> findAll() {
        return containerDataMapper.selectList(new QueryWrapper<>());
    }

    // ====== 以 containerMainId 為主的單筆策略 ======

    @Override
    public Optional<ContainerData> findByContainerMainId(Long containerMainId) {
        return Optional.ofNullable(
                containerDataMapper.selectOne(
                        new LambdaQueryWrapper<ContainerData>()
                                .eq(ContainerData::getContainerMainId, containerMainId)
                )
        );
    }

    @Override
    public boolean upsertByContainerMainId(Long containerMainId,
                                           Integer estimatedQuantity,
                                           String ocrText1,
                                           String ocrText2,
                                           Integer verifiedQuantity) {
        if (containerMainId == null) throw new IllegalArgumentException("containerMainId is null");

        ContainerData row = new ContainerData();
        row.setContainerMainId(containerMainId);
        row.setEstimatedQuantity(estimatedQuantity);
        row.setVerifiedQuantity(verifiedQuantity);
        row.setOcrText1(ocrText1);
        row.setOcrText2(ocrText2);

        Optional<ContainerData> existing = findByContainerMainId(containerMainId);
        if (existing.isPresent()) {
            row.setId(existing.get().getId());
            return update(row);
        } else {
            return save(row);
        }

        // 使用 Mapper 的 UPSERT（僅覆蓋非 NULL；不動 content_kind 與層別）
        // return containerDataMapper.upsert(row) > 0;
    }

    @Override
    public boolean upsertByContainerMainId(Long containerMainId,
                                           Integer estimatedQuantity,
                                           String ocrText1,
                                           String ocrText2,
                                           Integer verifiedQuantity,
                                           String contentKind) {
        if (containerMainId == null) throw new IllegalArgumentException("containerMainId is null");

        ContainerData row = new ContainerData();
        row.setContainerMainId(containerMainId);
        row.setEstimatedQuantity(estimatedQuantity);
        row.setVerifiedQuantity(verifiedQuantity);
        row.setOcrText1(ocrText1);
        row.setOcrText2(ocrText2);
        row.setContentKind(contentKind);

        Optional<ContainerData> existing = findByContainerMainId(containerMainId);
        if (existing.isPresent()) {
            row.setId(existing.get().getId());
            return update(row);
        } else {
            return save(row);
        }

        // 使用 Mapper 的 UPSERT（content_kind 僅在舊值為 'UNKNOWN' 時覆蓋）
        // return containerDataMapper.upsert(row) > 0;
    }

    @Override
    public boolean upsertLayers(Long containerMainId,
                                Integer workCoverLayers,
                                Integer coverLayers,
                                Integer productLayers) {
        if (containerMainId == null) throw new IllegalArgumentException("containerMainId is null");

        ContainerData row = new ContainerData();
        row.setContainerMainId(containerMainId);
        // 只把呼叫者想更新的欄位設進去（其餘保持 null，讓 upsert 的 COALESCE 不覆蓋）
        row.setWorkCoverLayers(workCoverLayers);
        row.setCoverLayers(coverLayers);
        row.setProductLayers(productLayers);

        Optional<ContainerData> existing = findByContainerMainId(containerMainId);
        if (existing.isPresent()) {
            row.setId(existing.get().getId());
            return update(row);
        } else {
            return save(row);
        }

        // return containerDataMapper.upsert(row) > 0;
    }

    @Override
    public boolean setContentKindIfUnknown(Long containerMainId, String contentKind) {
        if (containerMainId == null) throw new IllegalArgumentException("containerMainId is null");

        return containerDataMapper.update(
                null,
                new LambdaUpdateWrapper<ContainerData>()
                        .eq(ContainerData::getContainerMainId, containerMainId)
                        .eq(ContainerData::getContentKind, "UNKNOWN")
                        .set(ContainerData::getContentKind, contentKind)
        ) > 0;
    }

    @Override
    public boolean updateContentKind(Long containerMainId, String contentKind) {
        if (containerMainId == null) throw new IllegalArgumentException("containerMainId is null");

        return containerDataMapper.update(
                null,
                new LambdaUpdateWrapper<ContainerData>()
                        .eq(ContainerData::getContainerMainId, containerMainId)
                        .set(ContainerData::getContentKind, contentKind)
        ) > 0;
    }

    @Override
    public Optional<String> getContentKind(Long containerMainId) {
        if (containerMainId == null) throw new IllegalArgumentException("containerMainId is null");

        ContainerData onlyKind = containerDataMapper.selectOne(
                new LambdaQueryWrapper<ContainerData>()
                        .select(ContainerData::getContentKind)
                        .eq(ContainerData::getContainerMainId, containerMainId)
                        .last("LIMIT 1")
        );
        return Optional.ofNullable(onlyKind == null ? null : onlyKind.getContentKind());
    }

    @Override
    public boolean deleteByContainerMainId(Long containerMainId) {
        if (containerMainId == null) throw new IllegalArgumentException("containerMainId is null");

        return containerDataMapper.delete(
                new LambdaQueryWrapper<ContainerData>()
                        .eq(ContainerData::getContainerMainId, containerMainId)
        ) > 0;
    }

    /**
     * 只在層別欄位為 NULL 時，依 content_kind 與 verified_quantity 反推並補值：
     * - ALL_COVER         → cover = verified, product = 0
     * - NORMAL_WITH_COVER → cover = (verified>0 ? 1 : 0), product = verified - cover
     * - NORMAL_NO_COVER   → cover = 0, product = verified
     * - EMPTY             → cover = 0, product = 0
     * - 其他/NULL         → 視為 NORMAL_WITH_COVER
     *
     * 不覆蓋既有數值；不處理工蓋（work_cover_layers）。
     */
    @Override
    @Transactional
    public boolean fillLayersByKindIfUnset(Long containerMainId) {
        if (containerMainId == null) throw new IllegalArgumentException("containerMainId is null");

        // 先撈現況
        ContainerData cur = containerDataMapper.selectOne(
                new LambdaQueryWrapper<ContainerData>()
                        .eq(ContainerData::getContainerMainId, containerMainId)
                        .last("LIMIT 1")
        );
        if (cur == null) return false;
        Integer verified = cur.getVerifiedQuantity();
        if (verified == null) return false;

        String kind = cur.getContentKind();
        if (kind == null || kind.isBlank()) kind = "NORMAL_WITH_COVER";

        int v = Math.max(verified, 0);
        int cover, product;
        switch (kind) {
            case "ALL_COVER":
                cover = v; product = 0; break;
            case "NORMAL_NO_COVER":
                cover = 0; product = v; break;
            case "EMPTY":
                cover = 0; product = 0; break;
            case "UNKNOWN":
            case "NORMAL_WITH_COVER":
            default:
                cover = (v > 0) ? 1 : 0;
                product = Math.max(v - cover, 0);
                break;
        }

        boolean needCover   = (cur.getCoverLayers() == null || cur.getCoverLayers() == 0);
        boolean needProduct = (cur.getProductLayers() == null || cur.getProductLayers() == 0);
        if (!needCover || !needProduct) return false;

        // 僅在欄位仍為 NULL 時才補值，避免覆蓋已存在值（並在 WHERE 加 isNull 增加併發安全）
        LambdaUpdateWrapper<ContainerData> uw = new LambdaUpdateWrapper<ContainerData>()
                .eq(ContainerData::getId, cur.getId());

        if (needCover) {
            // uw.isNull(ContainerData::getCoverLayers)
            //         .set(ContainerData::getCoverLayers, cover);

            uw.set(ContainerData::getCoverLayers, cover);
        }
        if (needProduct) {
            // uw.isNull(ContainerData::getProductLayers)
            //         .set(ContainerData::getProductLayers, product);

            uw.set(ContainerData::getProductLayers, product);
        }

        int rows = containerDataMapper.update(null, uw);
        return rows > 0;
    }

    private void insertHistory(ContainerData entity, String changeType) {
        containerDataHistoryInsertService.offer(entity,changeType);
    }
}
