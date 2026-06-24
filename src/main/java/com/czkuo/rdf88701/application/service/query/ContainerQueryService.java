package com.czkuo.rdf88701.application.service.query;

import com.czkuo.rdf88701.domain.repository.ContainerDataRepository;
import com.czkuo.rdf88701.domain.repository.ContainerMainRepository;
import com.czkuo.rdf88701.infra.entity.ContainerData;
import com.czkuo.rdf88701.infra.entity.ContainerMain;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Container 查詢服務
 * - 提供容器主資料查詢
 * - 可支援聚合 ContainerData 查詢
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContainerQueryService {

    private final ContainerMainRepository containerMainRepository;
    private final ContainerDataRepository containerDataRepository;

    /**
     * 查主容器（依 DB id）
     */
    public ContainerMain getMainById(Long id) {
        return containerMainRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ContainerMain not found: id=" + id));
    }

    /**
     * 查主容器（依 carrierId = alias_code）
     */
    public ContainerMain getMainByCarrierId(String carrierId) {
        return containerMainRepository.findByAliasCode(carrierId)
                .orElseThrow(() -> new IllegalArgumentException("ContainerMain not found: carrierId=" + carrierId));
    }

    /**
     * 查主容器 + 最新一筆 ContainerData（依 DB id）
     */
    public ContainerAggregate getAggregateById(Long id) {
        var main = getMainById(id);
        var latest = getDataOfMain(main.getId()).orElse(null);
        return new ContainerAggregate(main, latest);
    }

    /**
     * 查主容器 + 最新一筆 ContainerData（依 carrierId）
     */
    public ContainerAggregate getAggregateByCarrierId(String carrierId) {
        var main = getMainByCarrierId(carrierId);
        var latest = getDataOfMain(main.getId()).orElse(null);
        return new ContainerAggregate(main, latest);
    }

    /**
     * Optional 聚合查詢（依 DB id）
     * - 需要延遲處理錯誤或進一步判斷時可用
     */
    public Optional<ContainerAggregate> findAggregateById(Long id) {
        return containerMainRepository.findById(id)
                .map(m -> new ContainerAggregate(m,
                        getDataOfMain(m.getId()).orElse(null)));
    }

    /**
     * 取某主容器的最新 ContainerData
     * - 以 created_time DESC, id DESC 排序
     */
    public Optional<ContainerData> getDataOfMain(Long containerMainId) {
        return containerDataRepository
                .findByContainerMainId(containerMainId);
    }

    /**
     * 聚合輸出：主容器 + 最新資料（可能為 null）
     */
    public record ContainerAggregate(
            ContainerMain main,
            @Nullable ContainerData latestData
    ) {}
}
