package com.czkuo.rdf88701.presentation.web.controller;

import com.czkuo.rdf88701.application.service.query.ContainerQueryService;
import com.czkuo.rdf88701.domain.repository.ContainerAttrRepository;
import com.czkuo.rdf88701.domain.repository.ContainerDataRepository;
import com.czkuo.rdf88701.domain.repository.ContainerMainRepository;
import com.czkuo.rdf88701.infra.entity.ContainerAttr;
import com.czkuo.rdf88701.infra.entity.ContainerData;
import com.czkuo.rdf88701.infra.entity.ContainerMain;
import com.czkuo.rdf88701.presentation.web.dto.ContainerDataRequest;
import com.czkuo.rdf88701.presentation.web.dto.ContainerMainDto;
import com.czkuo.rdf88701.presentation.web.dto.CreateContainerRequest;
import com.czkuo.rdf88701.presentation.web.dto.UpdateContainerRequest;
import com.czkuo.rdf88701.presentation.web.mapper.ContainerMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.*;

/**
 * 容器 REST API Controller。
 *
 * <p>提供容器查詢、建立、更新與刪除，並聚合 container_main、最新
 * container_data 與 tray_thickness_mm attr。列表查詢目前只回傳仍在
 * location_tracking 內的容器。</p>
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@RestController
@RequestMapping("/api/containers")
@RequiredArgsConstructor
@Validated
public class ContainerController {

    private static final String ATTR_TRAY_THICKNESS = "tray_thickness_mm";

    private final ContainerMainRepository containerMainRepository;
    private final ContainerDataRepository containerDataRepository;
    private final ContainerAttrRepository containerAttrRepository;
    private final ContainerQueryService   queryService;

    // --------------------------- 查詢 ---------------------------
    @GetMapping
    public Map<String, Object> list(
            @RequestParam(name = "query", required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size
    ) {
        final int p0 = Math.max(0, page);
        final int s  = Math.max(1, size);
        final long mpPage = p0 + 1L;

        String q = StringUtils.hasText(query) ? query.trim() : null;

//        var records = containerMainRepository.findPageByQuery(q, mpPage, s);
//        long total  = containerMainRepository.countByQuery(q);
//
//        List<ContainerMainDto> content = new ArrayList<>(records.size());
//        for (ContainerMain m : records) {
//            ContainerData data = queryService.getDataOfMain(m.getId()).orElse(null);
//            content.add(mapWithThickness(m, data));
//        }

        // 只撈「在 tracking 內」的容器
        var records = containerMainRepository.findTrackedPageByQuery(q, mpPage, s);
        long total  = containerMainRepository.countTrackedByQuery(q);

        // 帶回最新 data（沿用你原本做法）
        List<ContainerMainDto> content = new ArrayList<>(records.size());
        for (ContainerMain m : records) {
            ContainerData data = queryService.getDataOfMain(m.getId()).orElse(null);
            content.add(mapWithThickness(m, data));
        }

        //log.debug("[Container][LIST] query='{}' page={} size={} -> {} rows / total={}", q, page, size, content.size(), total);
        return Map.of("content", content, "total", total);
    }

    // --------------------------- 讀取單筆 ---------------------------
    @GetMapping("/{carrierId}")
    public ResponseEntity<ContainerMainDto> getByCarrierId(@PathVariable @NotBlank String carrierId) {
        var agg = queryService.getAggregateByCarrierId(carrierId.trim());
        //log.debug("[Container][GET] carrierId={}", carrierId);
        return ResponseEntity.ok(mapWithThickness(agg.main(), agg.latestData()));
    }

    @GetMapping("/id/{id}")
    public ResponseEntity<ContainerMainDto> getById(@PathVariable Long id) {
        var agg = queryService.getAggregateById(id);
        //log.debug("[Container][GET] id={}", id);
        return ResponseEntity.ok(mapWithThickness(agg.main(), agg.latestData()));
    }

    // --------------------------- 建立 ---------------------------
    @PostMapping
    @Transactional
    public ResponseEntity<ContainerMainDto> create(@Valid @RequestBody CreateContainerRequest req) {
        String carrierId = req.carrierId().trim();

        if (containerMainRepository.findByAliasCode(carrierId).isPresent()) {
            log.warn("[Container][CREATE] carrierId={} already exists -> 409", carrierId);
            return ResponseEntity.status(409).build();
        }

        var main = new ContainerMain();
        main.setAliasCode(carrierId);
        main.setContainerType(up(req.containerType()));
        main.setContainerCode(n(req.containerCode()));
        main.setLotNo(n(req.lotNo()));
        main.setPartNo(n(req.partNo()));
        containerMainRepository.save(main); // 回填 id

        log.info("[Container][CREATE] id={} carrierId={} type={} code={} lot={} part={}",
                main.getId(), main.getAliasCode(), main.getContainerType(), main.getContainerCode(), main.getLotNo(), main.getPartNo());

        // optional data
        ContainerData data = null;
        if (req.data() != null) {
            upsertDataInternal(main.getId(), req.data());
            data = queryService.getDataOfMain(main.getId()).orElse(null);
        }

        // optional thickness（>0 才寫入）
        if (req.trayThicknessMm() != null) {
            Double t = req.trayThicknessMm();
            if (t != null && t > 0) {
                String normalized = upsertTrayThickness(main.getId(), t.toString());
                log.info("[Container][CREATE] id={} thickness(mm): new={}", main.getId(), normalized);
            } else {
                log.warn("[Container][CREATE] id={} ignore invalid thickness={}", main.getId(), t);
            }
        }

        var dto = mapWithThickness(main, data);
        return ResponseEntity.created(URI.create("/api/containers/" + dto.carrierId())).body(dto);
    }

    // --------------------------- 更新 ---------------------------
    @PutMapping("/{carrierId}")
    @Transactional
    public ResponseEntity<ContainerMainDto> update(@PathVariable String carrierId,
                                                   @Valid @RequestBody UpdateContainerRequest req) {
        var main = containerMainRepository.findByAliasCode(carrierId.trim())
                .orElseThrow(() -> new IllegalArgumentException("ContainerMain not found: carrierId=" + carrierId));

        // 記錄 main 欄位 before
        String beforeType = main.getContainerType();
        String beforeCode = main.getContainerCode();
        String beforeLot  = main.getLotNo();
        String beforePart = main.getPartNo();

        // 更新 main 欄位
        if (req.containerType() != null) main.setContainerType(up(req.containerType()));
        if (req.containerCode() != null) main.setContainerCode(n(req.containerCode()));
        if (req.lotNo() != null)        main.setLotNo(n(req.lotNo()));
        if (req.partNo() != null)       main.setPartNo(n(req.partNo()));
        containerMainRepository.update(main);

        // 主檔差異紀錄
        logIfChanged(main.getId(), "containerType", beforeType, main.getContainerType());
        logIfChanged(main.getId(), "containerCode", beforeCode, main.getContainerCode());
        logIfChanged(main.getId(), "lotNo",         beforeLot,  main.getLotNo());
        logIfChanged(main.getId(), "partNo",        beforePart, main.getPartNo());

        // data upsert + 差異紀錄
        if (req.data() != null) {
            upsertDataInternal(main.getId(), req.data());
        }

        // 厚度：null=不變；<=0=刪除；>0=upsert
        if (req.trayThicknessMm() != null) {
            String beforeT = containerAttrRepository.findOne(main.getId(), ATTR_TRAY_THICKNESS)
                    .map(ContainerAttr::getAttrValue)
                    .orElse(null);

            Double t = req.trayThicknessMm();
            if (t != null && t <= 0) {
                boolean deleted = containerAttrRepository.deleteOne(main.getId(), ATTR_TRAY_THICKNESS);
                if (deleted) {
                    log.info("[Container][UPDATE] id={} thickness(mm): {} -> (deleted)", main.getId(), beforeT);
                } else {
                    log.info("[Container][UPDATE] id={} thickness(mm): no-op delete (no attr)", main.getId());
                }
            } else if (t != null && t > 0) {
                String normalized = upsertTrayThickness(main.getId(), t.toString());
                String afterT = containerAttrRepository.findOne(main.getId(), ATTR_TRAY_THICKNESS)
                        .map(ContainerAttr::getAttrValue)
                        .orElse(null);
                logIfChanged(main.getId(), "thickness(mm)", beforeT, afterT);
                if (normalized == null) {
                    log.warn("[Container][UPDATE] id={} thickness invalid raw={}, kept={}", main.getId(), t, beforeT);
                }
            }
        }

        ContainerData data = queryService.getDataOfMain(main.getId()).orElse(null);
        return ResponseEntity.ok(mapWithThickness(main, data));
    }

    // --------------------------- 刪除 ---------------------------
    @DeleteMapping("/{carrierId}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable String carrierId) {
        var main = containerMainRepository.findByAliasCode(carrierId.trim()).orElse(null);
        if (main == null) {
            log.warn("[Container][DELETE] carrierId={} not found -> 404", carrierId);
            return ResponseEntity.notFound().build();
        }

        // 先刪從屬資料
        containerDataRepository.deleteByContainerMainId(main.getId());
        // 刪除所有 attr（包含厚度）
        containerAttrRepository.deleteByContainerMainId(main.getId());

        // 再刪主檔
        containerMainRepository.deleteById(main.getId());
        log.info("[Container][DELETE] id={} carrierId={} deleted (main+data+attr)", main.getId(), main.getAliasCode());
        return ResponseEntity.noContent().build();
    }

    // --------------------------- 小工具 ---------------------------
    private static String n(String s) { return s == null ? null : s.trim(); }
    private static String up(String s) { return s == null ? null : s.trim().toUpperCase(Locale.ROOT); }

    /** 以 attr 讀取厚度並交給 Mapper（Mapper 會一併帶出上蓋層數、一般片層數） */
    private ContainerMainDto mapWithThickness(ContainerMain m, ContainerData data) {
        String rawThickness = containerAttrRepository.findOne(m.getId(), ATTR_TRAY_THICKNESS)
                .map(ContainerAttr::getAttrValue)
                .orElse(null);
        return ContainerMapper.toDtoWithThickness(m, data, rawThickness);
    }

    /** upsert container_data（含分路層數一致性處理與差異 log） */
    private void upsertDataInternal(Long mainId, ContainerDataRequest req) {
        if (req == null) return;

        // 先抓 before（可能為 null）
        ContainerData before = containerDataRepository.findByContainerMainId(mainId).orElse(null);
        int beforeWC  = (before != null && before.getWorkCoverLayers() != null) ? before.getWorkCoverLayers() : 0;
        Integer beforeCov   = (before == null) ? null : before.getCoverLayers();
        Integer beforeProd  = (before == null) ? null : before.getProductLayers();
        Integer beforeEst   = (before == null) ? null : before.getEstimatedQuantity();
        Integer beforeVer   = (before == null) ? null : before.getVerifiedQuantity();
        String  beforeOCR1  = (before == null) ? null : before.getOcrText1();
        String  beforeOCR2  = (before == null) ? null : before.getOcrText2();
        String  beforeKind  = (before == null) ? null : before.getContentKind();

        Integer cover   = req.coverLayers();
        Integer product = req.productLayers();
        Integer estimated = req.estimatedQuantity();

        // 若前端有帶任一層數 → 依「工蓋(現況)+上蓋(新/舊)+一般(新/舊)」重算 estimated，確保一致
        boolean recomputeEst = (cover != null || product != null);
        if (recomputeEst) {
            int cov  = (cover   != null) ? Math.max(0, cover)   : (beforeCov  == null ? 0 : beforeCov);
            int prod = (product != null) ? Math.max(0, product) : (beforeProd == null ? 0 : beforeProd);
            estimated = beforeWC + cov + prod;
            log.info("[Container][DATA] id={} recompute estimated={} (workCover={} + cover={} + product={})",
                    mainId, estimated, beforeWC, cov, prod);
        }

        // 先 upsert 主要欄位（確保有資料列）
        if (StringUtils.hasText(req.contentKind())) {
            containerDataRepository.upsertByContainerMainId(
                    mainId,
                    estimated,
                    n(req.ocrText1()),
                    n(req.ocrText2()),
                    req.verifiedQuantity(),
                    up(req.contentKind())
            );
        } else {
            containerDataRepository.upsertByContainerMainId(
                    mainId,
                    estimated,
                    n(req.ocrText1()),
                    n(req.ocrText2()),
                    req.verifiedQuantity()
            );
        }

        // 若有帶層數 → 再把分路層數與 contentKind 校正回 DB
        if (cover != null || product != null) {
            ContainerData cd = containerDataRepository.findByContainerMainId(mainId).orElseGet(() -> {
                ContainerData n = new ContainerData();
                n.setContainerMainId(mainId);
                containerDataRepository.save(n);
                return containerDataRepository.findByContainerMainId(mainId).orElse(n);
            });

            if (cover   != null) cd.setCoverLayers(Math.max(0, cover));
            if (product != null) cd.setProductLayers(Math.max(0, product));

            // 若前端沒指定 contentKind，依層數自動推導
            if (!StringUtils.hasText(req.contentKind())) {
                int cov  = cd.getCoverLayers()   == null ? 0 : cd.getCoverLayers();
                int prod = cd.getProductLayers() == null ? 0 : cd.getProductLayers();
                String kind;
                if (cov > 0 && prod == 0)      kind = "ALL_COVER";
                else if (cov > 0 && prod > 0)  kind = "NORMAL_WITH_COVER";
                else if (cov == 0 && prod > 0) kind = "NORMAL_NO_COVER";
                else                            kind = "UNKNOWN";
                cd.setContentKind(kind);
            }

            // estimated 以重算值為準（一定與分項總和一致）
            if (estimated != null) cd.setEstimatedQuantity(estimated);

            containerDataRepository.update(cd);
        }

        // 抓 after 並記錄差異
        ContainerData after = containerDataRepository.findByContainerMainId(mainId).orElse(null);
        if (after != null) {
            logIfChanged(mainId, "data.verifiedQuantity", beforeVer, after.getVerifiedQuantity());
            logIfChanged(mainId, "data.estimatedQuantity", beforeEst, after.getEstimatedQuantity());
            logIfChanged(mainId, "data.ocrText1",          beforeOCR1, after.getOcrText1());
            logIfChanged(mainId, "data.ocrText2",          beforeOCR2, after.getOcrText2());
            logIfChanged(mainId, "data.contentKind",       beforeKind, after.getContentKind());
            logIfChanged(mainId, "data.coverLayers",       beforeCov,  after.getCoverLayers());
            logIfChanged(mainId, "data.productLayers",     beforeProd, after.getProductLayers());
        }
    }

    /** 正規化 + upsert 厚度屬性（成功回傳 normalized，失敗回傳 null） */
    private String upsertTrayThickness(Long mainId, String rawThickness) {
        String normalized = normalizeThickness(rawThickness);
        if (normalized == null) return null; // 不寫入無效值
        ContainerAttr attr = new ContainerAttr();
        attr.setContainerMainId(mainId);
        attr.setAttrKey(ATTR_TRAY_THICKNESS);
        attr.setAttrValue(normalized);
        containerAttrRepository.upsert(attr);
        return normalized;
    }

    /** 將厚度字串正規化成正數小數，失敗傳回 null */
    private String normalizeThickness(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        String n = raw.trim().replaceAll("[^0-9,\\.\\-]", "");
        if (n.isEmpty()) return null;
        if (n.contains(".") && n.contains(",")) {
            n = n.replace(",", "");
        } else if (n.contains(",") && !n.contains(".")) {
            n = n.replace(',', '.');
        }
        try {
            double v = Double.parseDouble(n);
            if (v <= 0) return null;
            // 以原生格式存（避免科學記號），保留最多 3 位小數即可
            return String.valueOf(Math.round(v * 1000.0) / 1000.0);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 差異紀錄（null 安全） */
    private void logIfChanged(Long id, String field, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            log.info("[Container][CHANGE] id={} {}: {} -> {}", id, field, before, after);
        }
    }
}
