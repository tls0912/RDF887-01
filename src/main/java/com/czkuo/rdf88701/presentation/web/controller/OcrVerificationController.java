package com.czkuo.rdf88701.presentation.web.controller;

import com.czkuo.rdf88701.application.service.ocr.OcrImageService;
import com.czkuo.rdf88701.application.service.ocr.OcrImageService.OcrImagesBundle;
import com.czkuo.rdf88701.domain.repository.OcrVerificationRepository;
import com.czkuo.rdf88701.infra.entity.OcrVerification;
import com.czkuo.rdf88701.presentation.web.dto.OcrManualDecisionRequest;
import com.czkuo.rdf88701.presentation.web.dto.OcrVerificationDetailDto;
import com.czkuo.rdf88701.presentation.web.dto.OcrVerificationSummaryDto;
import com.czkuo.rdf88701.common.util.ImageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Slf4j
@RestController
@RequestMapping("/api/ocr-verifications")
@RequiredArgsConstructor
public class OcrVerificationController {

    private final OcrVerificationRepository ocrVerificationRepository;
    private final OcrImageService ocrImageService;

    /** 1) 列出待人工判定清單（PENDING） */
    @GetMapping("/pending")
    public List<OcrVerificationSummaryDto> listPending(
            @RequestParam(defaultValue = "100") int limit) {

        List<OcrVerification> list = ocrVerificationRepository
                .findPendingManualDecisions(limit);

        return list.stream()
                .map(this::toSummaryDto)
                .collect(Collectors.toList());
    }

    /**
     * 2) 取得單筆詳細內容（含 OCR 與圖片）
     *
     * 圖片策略：
     *   - 優先使用 ocr_verification 裡存的檔案路徑（*_path）
     *   - 若路徑都為空，則回退使用「當下最新」影像（OcrImageService）
     */
    @GetMapping("/{id}")
    public OcrVerificationDetailDto getDetail(@PathVariable Long id) {
        OcrVerification v = ocrVerificationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("OcrVerification not found: " + id));

        OcrVerificationDetailDto dto = toDetailDto(v);

        // 1) 先嘗試用 snapshot 的 path → data URL
        List<String> trayImages = buildImagesFromPaths(
                v.getCurrBackOneLightPath(),
                v.getCurrBackThreeLightPath(),
                v.getCurrFrontOneLightPath(),
                v.getCurrFrontThreeLightPath()
        );

        List<String> coverImages = buildImagesFromPaths(
                v.getRefBackOneLightPath(),
                v.getRefBackThreeLightPath(),
                v.getRefFrontOneLightPath(),
                v.getRefFrontThreeLightPath()
        );

        // 2) 若 snapshot 都是空的，再 fallback 用「當下最新」
        if (trayImages.isEmpty()) {
            trayImages = ocrImageService
                    .getLatestImagesForContainer(v.getContainerMainId())
                    .map(OcrImagesBundle::getDataUrls)
                    .orElse(Collections.emptyList());
        }

        if (coverImages.isEmpty()) {
            if (v.getRefContainerId() != null) {
                coverImages = ocrImageService
                        .getLatestImagesForContainer(v.getRefContainerId())
                        .map(OcrImagesBundle::getDataUrls)
                        .orElse(Collections.emptyList());
            } else if (v.getRefSite() != null) {
                coverImages = ocrImageService
                        .getLatestImagesForLocation(v.getRefSite())
                        .map(OcrImagesBundle::getDataUrls)
                        .orElse(Collections.emptyList());
            }
        }

        dto.setTrayImages(trayImages);
        dto.setCoverImages(coverImages);
        return dto;
    }

    /** 3) 人工按 ALLOW / BLOCK */
    @PostMapping("/{id}/decision")
    public void manualDecision(@PathVariable Long id,
                               @RequestBody OcrManualDecisionRequest req) {
        OcrVerification v = ocrVerificationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("OcrVerification not found: " + id));

        String decision = req.getDecision();
        if (decision == null) {
            throw new IllegalArgumentException("decision is required");
        }
        String d = decision.trim().toUpperCase(Locale.ROOT);
        if (!"ALLOW".equals(d) && !"BLOCK".equals(d) && !"RETRY".equals(d)) {
            throw new IllegalArgumentException("decision must be ALLOW or BLOCK or RETRY");
        }

        // 已經有 final_result 的就不讓重複判
        // if (v.getFinalResult() != null && !v.getFinalResult().isBlank()) {
        //     log.warn("[OCR-MANUAL] id={} 已有 final_result={}，忽略新的判定 {}",
        //             v.getId(), v.getFinalResult(), d);
        //     return;
        // }

        v.setManualDecision(d);
        v.setManualBy(req.getUser());
        v.setManualTime(LocalDateTime.now());

        if ("BLOCK".equals(d)) {
            // 人工 BLOCK 最高優先權
            v.setFinalResult("BLOCK");
        }
        // 若要保存 remark：請先在 DB 加 manual_remark 欄位
        // v.setManualRemark(req.getRemark());

        v.setUpdatedTime(LocalDateTime.now());
        ocrVerificationRepository.update(v);

        log.info("[OCR-MANUAL] id={} cmId={} decision={} by={}",
                v.getId(), v.getContainerMainId(), d, req.getUser());
    }

    // ==================== Mapping functions ====================

    private OcrVerificationSummaryDto toSummaryDto(OcrVerification v) {
        OcrVerificationSummaryDto dto = new OcrVerificationSummaryDto();
        dto.setId(v.getId());
        dto.setContainerMainId(v.getContainerMainId());
        dto.setCarrierId(v.getCarrierId());
        dto.setLotId(v.getLotId());
        dto.setTrayType(v.getTrayType());

        dto.setLocalPass("Y".equalsIgnoreCase(v.getLocalPass()));
        dto.setBadOcr("Y".equalsIgnoreCase(v.getBadOcr()));
        dto.setPartMatch("Y".equalsIgnoreCase(v.getPartMatch()));
        dto.setOcr1Match("Y".equalsIgnoreCase(v.getOcr1Match()));
        dto.setOcr2Match("Y".equalsIgnoreCase(v.getOcr2Match()));

        dto.setS073Status(v.getS073Status());
        dto.setManualDecision(v.getManualDecision());
        dto.setFinalResult(v.getFinalResult());
        dto.setCreatedTime(v.getCreatedTime());
        return dto;
    }

    private OcrVerificationDetailDto toDetailDto(OcrVerification v) {
        OcrVerificationDetailDto dto = new OcrVerificationDetailDto();
        dto.setId(v.getId());
        dto.setContainerMainId(v.getContainerMainId());
        dto.setCarrierId(v.getCarrierId());
        dto.setLotId(v.getLotId());
        dto.setTrayType(v.getTrayType());
        dto.setRefSite(v.getRefSite());
        dto.setRefContainerId(v.getRefContainerId());

        dto.setCurrOcrText1(v.getCurrOcrText1());
        dto.setCurrOcrText2(v.getCurrOcrText2());
        dto.setRefOcrText1(v.getRefOcrText1());
        dto.setRefOcrText2(v.getRefOcrText2());

        dto.setLocalPass("Y".equalsIgnoreCase(v.getLocalPass()));
        dto.setBadOcr("Y".equalsIgnoreCase(v.getBadOcr()));
        dto.setPartMatch("Y".equalsIgnoreCase(v.getPartMatch()));
        dto.setOcr1Match("Y".equalsIgnoreCase(v.getOcr1Match()));
        dto.setOcr2Match("Y".equalsIgnoreCase(v.getOcr2Match()));

        dto.setS073Tid(v.getS073Tid());
        dto.setS073Status(v.getS073Status());
        dto.setS073ResultCode(v.getS073ResultCode());
        dto.setManualDecision(v.getManualDecision());
        dto.setManualBy(v.getManualBy());
        dto.setManualTime(v.getManualTime());
        dto.setFinalResult(v.getFinalResult());

        dto.setCreatedTime(v.getCreatedTime());
        dto.setUpdatedTime(v.getUpdatedTime());
        return dto;
    }

    // ==================== 圖片 helper ====================

    /**
     * 把 DB 裡的檔案路徑（*_path）轉成前端可用的 data URL。
     * 目前順序：backOne, backThree, frontOne, frontThree。
     */
    private List<String> buildImagesFromPaths(String backOnePath,
                                              String backThreePath,
                                              String frontOnePath,
                                              String frontThreePath) {

        List<String> paths = new ArrayList<>();
        if (notBlank(backOnePath))    paths.add(backOnePath);
        if (notBlank(backThreePath))  paths.add(backThreePath);
        if (notBlank(frontOnePath))   paths.add(frontOnePath);
        if (notBlank(frontThreePath)) paths.add(frontThreePath);

        if (paths.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();
        for (String p : paths) {
            try {
                Path path = Paths.get(p);
                String dataUrl = ImageUtils.fileToDataUrl(path);
                result.add(dataUrl);
            } catch (Exception e) {
                log.warn("[OCR-IMG] 讀取圖片失敗 path={} : {}", p, e.toString());
            }
        }
        return result;
    }

    private boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
