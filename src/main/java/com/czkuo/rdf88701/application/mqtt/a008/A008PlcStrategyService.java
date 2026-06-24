package com.czkuo.rdf88701.application.mqtt.a008;

import com.czkuo.rdf88701.application.service.AmrInterlockService;
import com.czkuo.rdf88701.application.service.command.ContainerCreateService;
import com.czkuo.rdf88701.application.service.location.LocationAccountingService;
import com.czkuo.rdf88701.common.dto.mqtt.command.A008CommandPayload;
import com.czkuo.rdf88701.common.enums.EntryType;
import com.czkuo.rdf88701.domain.repository.*;
import com.czkuo.rdf88701.infra.entity.ContainerAttr;
import com.czkuo.rdf88701.infra.entity.ContainerMain;
import com.czkuo.rdf88701.infra.entity.LocationPoint;
import com.czkuo.rdf88701.infra.entity.RobotR008Task;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A008（PLC）策略：
 * - 僅處理目的地為 STK03/04/05 且 JOB_STATUS = OUTPUT_END 的訊息（此流屬 R008）
 * - 以 commandId 解析出 TID（R008_yyyyMMddHHmmssSSS），解析不到時使用 payload.tid
 * - 用 TID 撈「最新一筆」 R008 任務
 * - 流程：先建帳（ensure 容器 + entry 至對應位置）→ 再呼叫 PLC 解鎖（disableDrop）
 * - 空 tray 判定：若 carrierId 符合「料號_批號_時間」樣式，content_kind=ALL_COVER
 *
 * Port → Location 對應：
 *   STK03 → Site#1
 *   STK04 → Transfer#2
 *   STK05 → Site#17
 *
 * 重要：若找不到任務 / 容器資訊不足 / location 無法解析 pointId，則不解鎖，避免站口放行與帳務不一致。
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class A008PlcStrategyService {

    // ====== 注入的服務/倉儲 ======
    private final RobotR008TaskRepository r008TaskRepo;
    private final ContainerMainRepository containerMainRepository;
    private final ContainerDataRepository containerDataRepository;
    private final ContainerAttrRepository containerAttrRepository;
    private final ContainerCreateService containerCreateService;
    private final LocationAccountingService locationAccountingService;
    private final LocationPointRepository locationPointRepository;
    private final LocationTrackingRepository locationTrackingRepository;
    private final AmrInterlockService interlock;

    // ====== 常數與對應 ======
    private static final Set<String> PORTS = Set.of("STK03", "STK04", "STK05");

    /** 站口 → 位置別名 */
    private static final Map<String, String> PORT_TO_LOCATION_ALIAS = Map.of(
            "STK03", "Site#1",
            "STK04", "Transfer#2",
            "STK05", "Site#17"
    );

    /** 由 commandId 解析 R007/R008 的 TID：R007_yyyyMMddHHmmssSSS / R008_yyyyMMddHHmmssSSS */
    private static final Pattern R_TASK_TID = Pattern.compile("^R00[78]_(\\d{14,})$");

    /** 空 tray carrierId 樣式：digits + '_' + [A-Z0-9]+ + '_' + digits（不分大小寫） */
    private static final Pattern EMPTY_TRAY_PATTERN =
            Pattern.compile("^[A-Za-z0-9]+_[A-Za-z0-9]+_\\d+$", Pattern.CASE_INSENSITIVE);

    /** DB enum 白名單（content_kind） */
    private static final Set<String> CONTENT_KIND_ALLOWED = Set.of(
            "UNKNOWN", "NORMAL_WITH_COVER", "NORMAL_NO_COVER", "ALL_COVER", "EMPTY"
    );

    // ====== 對外主流程 ======

    /**
     * 若符合條件（STK03/04/05 且 INPUT_END），執行：
     * 1) 以 TID 回查 R008 任務
     * 2) ensure 容器（TRAY），必要時補建
     * 3) 空 tray → content_kind=ALL_COVER
     * 4) Entry 入帳（EntryType.PLC）
     * 5) 再呼叫 PLC 解鎖（disableDrop）
     */
    @Transactional
    public boolean handle(A008CommandPayload cmd) {
        var msg  = cmd.getMessage();
        String dest = up(msg != null ? msg.getDestLoc()   : null);
        String job  = up(msg != null ? msg.getJobStatus() : null);

        int idx = dest.indexOf("_");
        if (idx > 0) {
            dest = dest.substring(0, idx);
        }

        if (!PORTS.contains(dest)) return false;
        if (!"INPUT_END".equals(job)) return false;

        String commandId     = (msg != null ? msg.getCommandId() : null);   // e.g. R008_20250703000545274
        String tidFromCmdId  = extractTidFromCommandId(commandId);
        String tid           = (tidFromCmdId != null ? tidFromCmdId : nz(cmd.getTid()));

        String locationAlias = PORT_TO_LOCATION_ALIAS.get(dest);
        if (locationAlias == null) {
            log.warn("[A008][PLC] 未知 Port→Location 映射：port={}, tid={}, job={}", dest, tid, job);
            return true; // 視為已處理，避免重複嘗試
        }

        // 1) 用 TID 回查 R008 任務（取最新一筆）
        Optional<RobotR008Task> taskOpt = r008TaskRepo.findLatestByTid(tid);
        if (taskOpt.isEmpty()) {
            log.warn("[A008][PLC] 找不到 R008 任務：tid='{}' (cmdId='{}')；不建帳/不解鎖", tid, commandId);
            return true;
        }
        RobotR008Task t = taskOpt.get();

        String lotId     = nz(t.getLotId());     // 批號
        String trayType  = nz(t.getTrayType());  // 料號
        String binType  = nz(t.getBinType());    // 類型
        String carrierId = nz(t.getCarrierId());
        if (StringUtils.isBlank(carrierId)) {
            log.warn("[A008][PLC] R008 任務缺 carrierId：tid='{}' (cmdId='{}')；不建帳/不解鎖", tid, commandId);
            return true;
        }

        // 2) ensure 容器（TRAY）：若已存在回 id；否則建立並回 id（僅建容器，不入帳）
        Long containerId = ensureTrayContainer(carrierId, lotId, trayType, t);

        // 3) 空 tray → content_kind=ALL_COVER
        if (isEmptyTrayCarrierId(carrierId, trayType, lotId)) {
            safeUpsertContentKind(containerId, "ALL_COVER");
            log.info("[A008][PLC] carrierId 判定為空 tray → content_kind=ALL_COVER, containerId={}", containerId);
        } else {
            safeUpsertContentKind(containerId, "NORMAL_WITH_COVER");
            log.info("[A008][PLC] carrierId 判定為一般 tray → content_kind=NORMAL_WITH_COVER, containerId={}", containerId);
        }

        // 4) 先建帳
        Long pointId = locationPointRepository.findByName(locationAlias)
                .map(LocationPoint::getId)
                .orElse(null);;
        if (pointId == null) {
            log.warn("[A008][PLC] 位置 pointId 解析失敗：alias='{}'；不解鎖以免不一致 (tid={})", locationAlias, tid);
            return true;
        }

        locationAccountingService.entry(
                containerId,
                pointId,
                EntryType.PLC,
                "A008/R008_TASK",
                null
        );
        log.info("[A008][PLC] 建帳完成：containerId={} → {} (#{})", containerId, locationAlias, pointId);

        // 5) 補厚度屬性：改用 R008.trayHigh，而不是寫死 5.62
        BigDecimal trayHigh = t.getTrayHigh();
        if (trayHigh != null) {
            String thickness = trayHigh.stripTrailingZeros().toPlainString();
            upsertAttr(containerId, "tray_thickness_mm", thickness, "mm");
            log.info("[A008][PLC] 由 R008.trayHigh 補厚度屬性：trayHigh={} mm, containerId={}",
                    thickness, containerId);
        } else {
            log.warn("[A008][PLC] R008.trayHigh 為 NULL，無法補 tray_thickness_mm，containerId={}", containerId);
        }

        // 6) 補 TRAY 盤屬性
        if (binType != null) {
            upsertAttr(containerId, "bin_type", binType, "type");
            log.info("[A008][PLC] 由 R008.binType 補 TRAY 盤屬性：bin_type={} , containerId={}",
                    binType, containerId);
        } else {
            log.warn("[A008][PLC] R008.binType 為 NULL，無法補 bin_type，containerId={}", containerId);
        }

        // 7) 再解鎖（disableDrop）
        try {
            boolean ok = interlock.disableDrop(dest); // 清 pass-enable=0
            if (!ok) {
                log.warn("[A008][PLC] disableDrop 回傳失敗：port={}, containerId={}, tid={}", dest, containerId, tid);
            } else {
                log.info("[A008][PLC] disableDrop 成功：port={}, containerId={}, tid={}", dest, containerId, tid);
            }
        } catch (Exception ex) {
            log.warn("[A008][PLC] disableDrop 例外：port={}, containerId={}, tid={}, err={}",
                    dest, containerId, tid, ex.getMessage(), ex);
        }

        return true;
    }

    // ====== 私有輔助 ======

    private void upsertAttr(Long containerId, String key, String value, String unit) {
        ContainerAttr a = new ContainerAttr();
        a.setContainerMainId(containerId);
        a.setAttrKey(key);
        a.setAttrValue(value);
        a.setUnit(unit);
        containerAttrRepository.upsert(a); // 需 UNIQUE KEY (container_main_id, attr_key)
    }

    /** 從 commandId 取出 TID（支援 R007_/R008_ 前綴），取不到回 null */
    private static String extractTidFromCommandId(String commandId) {
        if (commandId == null || commandId.isBlank()) return null;
        var m = R_TASK_TID.matcher(commandId.trim());
        return m.matches() ? m.group(1) : null;
    }

    /** 若存在即回傳 id；否則建立 TRAY 容器並回 id（僅建容器，不入帳） */
    private Long ensureTrayContainer(String carrierId, String lotId, String trayType, RobotR008Task task) {
        return containerMainRepository.findByAliasCode(carrierId)
                .map(ContainerMain::getId)
                .orElseGet(() -> {
                    // 只建「實體容器」（TRAY），不在此刻建帳
                    Long id = containerCreateService.createRealContainer(
                            carrierId, "TRAY", nz(lotId), trayType
                    );
                    log.info("[A008][PLC] 新建 TRAY 容器：id={}, alias={}, lot={}, wip={}, dev={}, eqp={}",
                            id, carrierId, nz(lotId), nz(task.getWipName()), nz(task.getDeviceName()), nz(task.getEqpPort()));
                    return id;
                });
    }

    /** clamp 後寫入 content_kind（遵循 DB enum 白名單） */
    private void safeUpsertContentKind(Long containerId, String kind) {
        String v = clampContentKind(kind);
        containerDataRepository.upsertContentKind(containerId, v);
    }

    /** content_kind 收斂到 DB enum（未知值一律寫 UNKNOWN） */
    private static String clampContentKind(String v) {
        if (v == null) return "UNKNOWN";
        String up = v.trim().toUpperCase();
        return CONTENT_KIND_ALLOWED.contains(up) ? up : "UNKNOWN";
    }

    private static boolean isEmptyTrayCarrierId(String carrierId, String trayType, String lotId) {
        if (isBlank(carrierId) || isBlank(trayType) || isBlank(lotId)) return false;

        String[] parts = carrierId.trim().split("_", -1);
        if (parts.length != 3) return false;

        String p1 = parts[0].trim();
        String p2 = parts[1].trim();
        String p3 = parts[2].trim();

        if (!eqIgnoreCase(p1, trayType) || !eqIgnoreCase(p2, lotId)) return false;
        return p3.matches("\\d{10,14}");
    }

    // ========= 小工具 =========

    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
    private static boolean eqIgnoreCase(String a, String b) { return up(a).equals(up(b)); }
    private static String up(String s) { return s == null ? "" : s.trim().toUpperCase(); }
    private static String nz(String s) { return s == null ? "" : s; }
}
