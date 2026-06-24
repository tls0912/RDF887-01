package com.czkuo.rdf88701.application.assembler;

import com.czkuo.rdf88701.application.service.query.ContainerQueryService;
import com.czkuo.rdf88701.application.service.query.LocationQueryService;
import com.czkuo.rdf88701.domain.repository.ContainerMainRepository;
import com.czkuo.rdf88701.domain.repository.CraneTaskFollowUpRecordRepository;
import com.czkuo.rdf88701.domain.repository.CraneTaskRepository;
import com.czkuo.rdf88701.infra.adapter.plc.dto.PlcCraneWordCommand;
import com.czkuo.rdf88701.infra.entity.ContainerMain;
import com.czkuo.rdf88701.infra.entity.CraneTask;
import com.czkuo.rdf88701.infra.entity.CraneTaskFollowUpRecord;
import com.czkuo.rdf88701.infra.entity.LocationPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * 專責將 CraneTask 組裝為 PlcCraneWordCommand 的組裝器
 * FROM/TO 資料可分段組裝
 *
 * 2026-06-24 狀態：已修改，註解已依現有實作校正。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CraneWordCommandAssembler {

    private static final int EXCHANGE_WH_TARGET_ID = 199;
    private static final int MAX_FOLLOW_UP_HOPS = 16;      // 防循環

    private final ContainerQueryService containerQueryService;
    private final LocationQueryService locationQueryService;
    private final ContainerMainRepository containerMainRepository;
    private final CraneTaskRepository craneTaskRepository;
    private final CraneTaskFollowUpRecordRepository craneTaskFollowUpRecordRepository;

    public PlcCraneWordCommand assembleFromSection(CraneTask task) {
        ContainerMain container = requireContainer(task.getContainerMainId());
        LocationPoint source = requireLocation(task.getSourceLocationId());

        boolean isExchangeWarehouse = isExchangeWarehouse(task.getTargetLocationId());
        String cstId = resolveCstId(container, isExchangeWarehouse);

        return PlcCraneWordCommand.builder()
                .fromCommandType(2) // From
                .fromCstType(1)     // Normal
                .fromBcrFlag(0)     // 如需掃碼校驗，這裡可依情況改 1
                .fromTransferNo(safeInt(task.getId()))
                .fromCstId(cstId)
                .fromLocationType(parseLocationType(source.getLocationType()))
                .fromBank(defaultIfNull(source.getBank()))
                .fromBay(defaultIfNull(source.getBay()))
                .fromLevel(defaultIfNull(source.getLevel()))
                .build();
    }

    public PlcCraneWordCommand assembleToSection(CraneTask task) {
        ContainerMain container = requireContainer(task.getContainerMainId());
        LocationPoint target = requireLocation(task.getTargetLocationId());

        // 1) 目標就是交換倉 → 必須用 containerCode
        boolean toIsExchange = isExchangeWarehouse(task.getTargetLocationId());

        // 2) 非交換倉，但此任務是從「曾經進交換倉」的任務鏈延伸而來（follow-up） → 仍需用 containerCode
        boolean lineageTouchedExchange = toIsExchange || lineageHasExchangeTarget(task);

        // 這裡用 lineageTouchedExchange 來決定是否強制使用 containerCode
        String cstId = resolveCstId(container, lineageTouchedExchange);

        return PlcCraneWordCommand.builder()
                .toCommandType(3) // To
                .toCstType(1)     // Normal
                .toTransferNo(safeInt(task.getId()))
                .toCstId(cstId)
                .toLocationType(parseLocationType(target.getLocationType()))
                .toBank(defaultIfNull(target.getBank()))
                .toBay(defaultIfNull(target.getBay()))
                .toLevel(defaultIfNull(target.getLevel()))
                .build();
    }

    // ---------- 決策：是否於 follow-up 鏈上有任務的 TO 指向交換倉 ----------
    private boolean lineageHasExchangeTarget(CraneTask current) {
        // 如果目前任務的 TO 就是交換倉，直接 true（呼應 assembleToSection 的 toIsExchange 短路，但保險再判一次）
        if (isExchangeWarehouse(current.getTargetLocationId())) return true;

        // 由目前任務 id 往回找：follow_up_task_id = current.id -> original_task_id
        Long cursorTaskId = current.getId();
        Set<Long> visited = new HashSet<>();
        int hops = 0;

        while (cursorTaskId != null && hops < MAX_FOLLOW_UP_HOPS) {
            if (!visited.add(cursorTaskId)) {
                log.warn("Detected cycle in follow-up chain, taskId={}", cursorTaskId);
                return false;
            }

            Optional<CraneTaskFollowUpRecord> linkOpt =
                    craneTaskFollowUpRecordRepository.findByFollowUpTaskId(cursorTaskId);
            if (linkOpt.isEmpty()) {
                // 沒有上游：到頭了
                return false;
            }

            CraneTaskFollowUpRecord link = linkOpt.get();
            Long upstreamTaskId = link.getOriginalTaskId();
            if (upstreamTaskId == null || upstreamTaskId <= 0) {
                return false;
            }

            // 讀取上游任務
            CraneTask upstream = craneTaskRepository.findById(upstreamTaskId)
                    .orElse(null);
            if (upstream == null) {
                log.warn("Follow-up link points to missing task, originalTaskId={}", upstreamTaskId);
                return false;
            }

            // 只要任一上游的 TO 是交換倉，就視為整條鏈都要用 containerCode
            if (isExchangeWarehouse(upstream.getTargetLocationId())) {
                return true;
            }

            // 繼續往更上游走（因為可能多層 follow-up）
            cursorTaskId = upstreamTaskId;
            hops++;
        }

        if (hops >= MAX_FOLLOW_UP_HOPS) {
            log.warn("Follow-up chain too deep (>{}), taskId={}", MAX_FOLLOW_UP_HOPS, current.getId());
        }
        return false;
    }

    // ---------- 共用決策：依是否為交換倉決定使用哪個 ID，並在需要時「安全補植 + 寫回」 ----------
    private String resolveCstId(ContainerMain container, boolean isExchangeWarehouse) {
        String aliasCode = nvl(container.getAliasCode()).trim();
        String containerCode = nvl(container.getContainerCode()).trim();

        if (isExchangeWarehouse) {
            if (!containerCode.isEmpty()) return containerCode;
            // 目前實作：產生一組 SW 條碼，寫回 ContainerMain 後回傳。
            String ensured = genSwBarcode();
            log.warn("ContainerCode missing for exchange warehouse, auto-generated: {}", ensured);

            container.setContainerCode(ensured);
            containerMainRepository.update(container);
            return ensured;
        }
        return aliasCode;
    }

    private static boolean isExchangeWarehouse(Long targetLocationId) {
        return targetLocationId != null && targetLocationId == EXCHANGE_WH_TARGET_ID;
    }

    // ---------- 小工具 ----------
    private ContainerMain requireContainer(Long id) {
        ContainerMain cm = containerQueryService.getMainById(id);
        if (cm == null) throw new IllegalArgumentException("ContainerMain not found: id=" + id);
        return cm;
    }

    private LocationPoint requireLocation(Long id) {
        LocationPoint lp = locationQueryService.getById(id);
        if (lp == null) throw new IllegalArgumentException("LocationPoint not found: id=" + id);
        return lp;
    }

    private static String nvl(String s) { return s == null ? "" : s; }
    private static int defaultIfNull(Integer v) { return v == null ? 0 : v; }

    private static int safeInt(Long v) {
        if (v == null) return 0;
        if (v > Integer.MAX_VALUE || v < Integer.MIN_VALUE) {
            throw new IllegalArgumentException("transferNo out of int range: " + v);
        }
        return v.intValue();
    }

    private int parseLocationType(String type) {
        return switch (type) {
            case "AUTO_PORT" -> 2;
            case "MGV_PORT"  -> 3;
            case "EQ_PORT"   -> 4;
            case "T_PORT"    -> 7;
            case "BUFFER"    -> 12;
            default          -> 0;
        };
    }

    // ---------- 條碼產生器：SW + 4位數字 + 2位大寫英文 ----------
    private static final SecureRandom RAND = new SecureRandom();
    private String genSwBarcode() {
        int num = RAND.nextInt(10_000);             // 0000..9999
        String digits = String.format("%04d", num);
        char c1 = (char) ('A' + RAND.nextInt(26));
        char c2 = (char) ('A' + RAND.nextInt(26));
        return "SW" + digits + c1 + c2;
    }
}

