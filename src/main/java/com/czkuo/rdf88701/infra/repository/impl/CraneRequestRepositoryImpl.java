package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.czkuo.rdf88701.application.dto.query.CraneRequestQuery;
import com.czkuo.rdf88701.application.service.History.CraneRequestHistoryInsertService;
import com.czkuo.rdf88701.common.dto.PageResult;
import com.czkuo.rdf88701.common.util.LambdaQueryHelper;
import com.czkuo.rdf88701.domain.repository.CraneRequestRepository;
import com.czkuo.rdf88701.infra.entity.CraneRequest;
import com.czkuo.rdf88701.infra.mapper.CraneRequestHistoryMapper;
import com.czkuo.rdf88701.infra.mapper.CraneRequestMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * CraneRequest 資料存取實作（含自動歷程記錄）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Repository
@RequiredArgsConstructor
public class CraneRequestRepositoryImpl implements CraneRequestRepository {

    private final CraneRequestMapper craneRequestMapper;
    private final CraneRequestHistoryMapper craneRequestHistoryMapper;
    private final CraneRequestHistoryInsertService craneRequestHistoryInsertService;

    /**
     * 根據 ID 查詢 CraneRequest
     */
    @Override
    public Optional<CraneRequest> findById(Long id) {
        return Optional.ofNullable(craneRequestMapper.selectById(id));
    }

    /**
     * 根據 requestKey 查詢唯一請求
     */
    @Override
    public Optional<CraneRequest> findByRequestKey(String requestKey) {
        return Optional.ofNullable(craneRequestMapper.selectOne(
                new LambdaQueryWrapper<CraneRequest>()
                        .eq(CraneRequest::getRequestKey, requestKey)
        ));
    }

    /**
     * 檢查指定 requestKey 是否存在
     */
    @Override
    public boolean existsByRequestKey(String requestKey) {
        return craneRequestMapper.selectCount(
                new LambdaQueryWrapper<CraneRequest>()
                        .eq(CraneRequest::getRequestKey, requestKey)
                        .last("LIMIT 1")
        ) > 0;
    }

    /**
     * 檢查指定容器是否已有被 accepted 的 request
     */
    @Override
    public boolean existsUnfinishedRequestForContainer(Long containerMainId) {
        return craneRequestMapper.selectCount(
                new LambdaQueryWrapper<CraneRequest>()
                        .eq(CraneRequest::getContainerMainId, containerMainId)
                        .eq(CraneRequest::getAccepted, "N")
                        .last("LIMIT 1")
        ) > 0;
    }

    /**
     * 查詢指定裝置是否仍有尚未接受的 CraneRequest（通常為 accepted='N'）
     * - 可依照實際表欄位補上 crane_id 或其他條件
     */
    public boolean existsUnfinishedRequestForDevice(Long deviceId) {
        QueryWrapper<CraneRequest> wrapper = new QueryWrapper<>();
        wrapper.eq("accepted", "N");
        return craneRequestMapper.selectCount(wrapper) > 0;
    }

    /**
     * 條件查詢 - 不分頁
     */
    @Override
    public List<CraneRequest> findByCondition(CraneRequestQuery query) {
        return craneRequestMapper.selectList(buildQueryWrapper(query).getWrapper());
    }

    /**
     * 條件查詢 - 分頁
     */
    @Override
    public PageResult<CraneRequest> findPageByCondition(CraneRequestQuery query) {
        Page<CraneRequest> page = new Page<>(query.getSafePageNum(), query.getSafePageSize());
        IPage<CraneRequest> result = craneRequestMapper.selectPage(page, buildQueryWrapper(query).getWrapper());
        return new PageResult<>(query.getSafePageNum(), query.getSafePageSize(), result.getTotal(), result.getRecords());
    }

    /**
     * 新增請求，並寫入歷程
     */
    @Override
    public boolean save(CraneRequest entity) {
        boolean success = craneRequestMapper.insert(entity) > 0;
        if (success) {
            insertHistory(entity, "INSERT");
        }
        return success;
    }

    /**
     * 更新請求，並寫入歷程（若已被接單則禁止）
     */
    @Override
    public boolean update(CraneRequest entity) {
        CraneRequest existing = craneRequestMapper.selectById(entity.getId());
        if (existing != null && existing.isLocked()) {
            throw new IllegalStateException("CraneRequest 已被接單 (accepted=Y)，禁止修改");
        }

        boolean success = craneRequestMapper.updateById(entity) > 0;
        if (success) {
            insertHistory(entity, "UPDATE");
        }
        return success;
    }

    /**
     * 刪除請求，並寫入歷程
     */
    @Override
    public boolean deleteById(Long id) {
        CraneRequest beforeDelete = craneRequestMapper.selectById(id);
        boolean success = craneRequestMapper.deleteById(id) > 0;
        if (success && beforeDelete != null) {
            insertHistory(beforeDelete, "DELETE");
        }
        return success;
    }

    /**
     * 查詢所有請求
     */
    @Override
    public List<CraneRequest> findAll() {
        return craneRequestMapper.selectList(null);
    }

    /**
     * 查詢所有尚未被接受的請求（accepted = 'N'）
     */
    @Override
    public List<CraneRequest> findUnacceptedRequests() {
        return craneRequestMapper.selectList(
                new LambdaQueryWrapper<CraneRequest>()
                        .eq(CraneRequest::getAccepted, "N")
        );
    }

    /**
     * 指定來源位是否已存在未接受的 CraneRequest（等於 crane 將要去「取」那個位置）
     */
    @Override
    public boolean existsUnfinishedRequestPickFromLocation(Long deviceId, Long sourceLocationId) {
        if (deviceId == null || sourceLocationId == null) return false;

        return craneRequestMapper.selectCount(
                new LambdaQueryHelper<CraneRequest>().getWrapper()
                        .eq(CraneRequest::getSourceLocationId, sourceLocationId)
                        .eq(CraneRequest::getAccepted, "N")
                        .last("LIMIT 1")
                // .eq(CraneRequest::getDeviceId, deviceId)
        ) > 0;
    }

    /**
     * 指定目標位是否已存在未接受的 CraneRequest（等於 crane 將要去「放」那個位置）
     */
    @Override
    public boolean existsUnfinishedRequestPlaceToLocation(Long deviceId, Long targetLocationId) {
        if (deviceId == null || targetLocationId == null) return false;

        return craneRequestMapper.selectCount(
                new LambdaQueryHelper<CraneRequest>().getWrapper()
                        .eq(CraneRequest::getTargetLocationId, targetLocationId)
                        .eq(CraneRequest::getAccepted, "N")
                        .last("LIMIT 1")
                // .eq(CraneRequest::getDeviceId, deviceId)
        ) > 0;
    }

    /**
     * 建立查詢條件封裝器
     */
    private LambdaQueryHelper<CraneRequest> buildQueryWrapper(CraneRequestQuery query) {
        return LambdaQueryHelper.<CraneRequest>of()
                .eqIfPresent(CraneRequest::getRequestType, query::getRequestType)
                .eqIfPresent(CraneRequest::getRequestSource, query::getRequestSource)
                .eqIfPresent(CraneRequest::getAccepted, query::getAccepted)
                .geIfPresent(CraneRequest::getRequestTime, query::getRequestAfter)
                .leIfPresent(CraneRequest::getRequestTime, query::getRequestBefore);
    }

    /**
     * 寫入歷程表（共用方法）
     */
    private void insertHistory(CraneRequest entity, String changeType) {
        craneRequestHistoryInsertService.offer(entity,changeType);
    }
}
