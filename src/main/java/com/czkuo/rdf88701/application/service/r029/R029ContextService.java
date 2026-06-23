package com.czkuo.rdf88701.application.service.r029;

import com.czkuo.rdf88701.common.constants.R029AttrKeys;
import com.czkuo.rdf88701.domain.repository.ContainerAttrRepository;
import com.czkuo.rdf88701.domain.repository.ContainerMainRepository;
import com.czkuo.rdf88701.domain.repository.RobotInR029LotRepository;
import com.czkuo.rdf88701.domain.repository.RobotInR029Repository;
import com.czkuo.rdf88701.infra.entity.ContainerAttr;
import com.czkuo.rdf88701.infra.entity.RobotInR029;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * R029ContextService
 *
 * 功能：
 *  - 在入站 R029（logId）解析完成後，將 R029 的上下文（count、logId、tid、cmd）掛回「來源容器」。
 *  - 來源容器以 lotId = carrierId = container_main.alias_code 命中。
 *
 * 注意：
 *  - 本實作不新增新資料表，僅使用既有的 container_attr（UK: (container_main_id, attr_key)）。
 *  - 避免 lambda 造成「Variable used in lambda expression should be final or effectively final」，
 *    迴圈內一律用 if/else，不用 ifPresent(...) / ifPresentOrElse(...)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class R029ContextService {

    private final RobotInR029Repository r029Repo;
    private final RobotInR029LotRepository r029LotRepo;
    private final ContainerMainRepository containerMainRepo;
    private final ContainerAttrRepository containerAttrRepo; // 使用 upsert(ContainerAttr)

    /**
     * 將 R029 (logId) 的上下文掛回來源容器：
     * - 來源容器以 lotId = carrierId = container_main.alias_code 命中
     * - 命中就寫入 container_attr：
     *      R029_LOG_ID = logId
     *      R029_COUNT  = count（= EXACT_GROUP）
     *      R029_TID    = tid（若呼叫方提供）
     *      R029_CMD_ID = "R029"
     * - 未命中就略過（之後在拆併新建帳時再「向下傳遞」這些屬性）
     */
    @Transactional
    public void attachContextToSourceContainers(long logId, String tidOpt) {
        RobotInR029 r = r029Repo.findById(logId).orElse(null);
        if (r == null) {
            log.warn("[R029-CTX] 找不到主檔 logId={}", logId);
            return;
        }

        Integer count = r.getCount();
        List<String> carriers = r029LotRepo.findCarrierIdsByLogId(logId); // 這裡就是 alias_code

        int hit = 0;
        int miss = 0;

        for (String rawAlias : carriers) {
            if (rawAlias == null) continue;
            final String alias = rawAlias.trim();
            if (alias.isEmpty()) continue;

            var cmOpt = containerMainRepo.findByAliasCode(alias);
            if (cmOpt.isPresent()) {
                Long cmId = cmOpt.get().getId();

                // 必填：logId、cmdId
                upsertAttr(cmId, R029AttrKeys.LOG_ID, String.valueOf(logId));
                upsertAttr(cmId, R029AttrKeys.CMD_ID, "R029");

                // 可選：count、tid
                if (count != null) {
                    upsertAttr(cmId, R029AttrKeys.COUNT, String.valueOf(count));
                }
                if (tidOpt != null && !tidOpt.isBlank()) {
                    upsertAttr(cmId, R029AttrKeys.TID, tidOpt);
                }

                hit++;
            } else {
                log.info("[R029-CTX] carrier(alias_code)='{}' 尚無容器可掛，略過", alias);
                miss++;
            }
        }

        log.info("[R029-CTX] logId={} count={} → 掛回來源容器：命中 {} 筆，未命中 {} 筆",
                logId, count, hit, miss);
    }

    // ===== 新增：查詢目前「被 R029 佔用」的容器 IDs =====
    @Transactional(readOnly = true)
    public Set<Long> findOccupiedContainerIds() {
        // 以是否存在 key=R029_LOG_ID 為是否被佔用的判定（最簡可靠）
        List<Long> ids = containerAttrRepo.findContainerIdsByAttrKey(R029AttrKeys.LOG_ID);
        return new HashSet<>(ids);
    }

    // 單顆檢查（給即時過濾用）
    @Transactional(readOnly = true)
    public boolean isOccupied(Long containerId) {
        if (containerId == null) return false;
        return containerAttrRepo.existsByContainerIdAndKey(containerId, R029AttrKeys.LOG_ID);
    }

    // ===== 新增：釋放某筆 R029（logId）掛載的上下文（完成/取消時呼叫） =====
    @Transactional
    public void detachContextByLogId(long logId) {
        // 作法 A（建議）：直接用 attr_key=LOG_ID 且 attr_value=logId 批次刪
        int n1 = containerAttrRepo.deleteByAttrKeyAndValue(R029AttrKeys.LOG_ID, String.valueOf(logId));

        // 作法 B（可選）：查出對應 cmIds，再把四個 key 全清（兼容舊資料）
        // 先找出有 LOG_ID=logId 的 container ids
        List<Long> cmIds = containerAttrRepo.findContainerIdsByAttrKeyAndValue(R029AttrKeys.LOG_ID, String.valueOf(logId));
        int n2 = 0;
        if (!cmIds.isEmpty()) {
            n2 = containerAttrRepo.deleteByContainerIdsAndKeys(
                    cmIds,
                    List.of(R029AttrKeys.LOG_ID, R029AttrKeys.CMD_ID, R029AttrKeys.COUNT, R029AttrKeys.TID)
            );
        }

        log.info("[R029-CTX] detach logId={} → 刪 LOG_ID={} 筆，清四鍵={} 筆（容器數={}）",
                logId, n1, n2, cmIds.size());
    }

    /**
     * 以你現有的 upsert(ContainerAttr) 包一層單筆寫入。
     * - 這裡不使用 Optional.ifPresent(lambda) 以避免 effectively-final 限制。
     * - 依賴 DB 端唯一鍵 (container_main_id, attr_key) 做去重。
     */
    private void upsertAttr(Long containerId, String key, String value) {
        if (containerId == null || key == null) return;
        try {
            ContainerAttr e = new ContainerAttr();
            e.setContainerMainId(containerId);
            e.setAttrKey(key);
            e.setAttrValue(value);
            // 你已經在 Mapper 實作了 upsert(ContainerAttr)
            containerAttrRepo.upsert(e);
        } catch (Exception e) {
            log.error("[R029-CTX] upsert attr 失敗 cm#{} {}={} : {}", containerId, key, value, e.getMessage(), e);
        }
    }
}
