package com.czkuo.rdf88701.application.service;

import com.czkuo.rdf88701.application.mqtt.context.SystemContext;
import com.czkuo.rdf88701.application.mqtt.util.BaseMqttHandlerUtils;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.application.service.r029.R029ContextService;
import com.czkuo.rdf88701.common.dto.mqtt.command.R031CommandPayload;
import com.czkuo.rdf88701.common.enums.ExitType;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.domain.repository.*;
import com.czkuo.rdf88701.infra.entity.*;
import com.czkuo.rdf88701.presentation.web.dto.BindRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.springframework.http.HttpStatus.*;

/**
 * Location 操作服務（鎖/解、預約/取消、建帳/清帳）。
 *
 * 注意：
 * 1) Repository 以 MyBatis-Plus 風格為假設：
 *    - save(...) / update(...) / deleteById(...) 回傳 boolean
 *    - insert(...) 亦回傳 boolean
 * 2) 若你的 Repository 方法名不同（如 deleteById → removeById），請對應替換即可。
 * 3) 進/出帳會寫入 location_flow：
 *    - bind(): 先把該 container 任何未結束的 flow 設定離開時間，再插入一筆新的進帳 flow（entryType 預設 MANUAL）
 *    - clear(): 將未離開的 flow 補上 left_time 與 exitType=MANUAL、exitOperator=system（可依需求調整）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocationOpsService {

    private final LocationPointRepository locationPointRepository;
    private final LocationTrackingRepository locationTrackingRepository;
    private final ContainerMainRepository containerRepo;
    private final LocationFlowRepository locationFlowRepository; // 操作時寫 flow

    private final RobotInR031Repository r031Repository;
    private final RobotR031TaskRepository r031TaskRepository;
    private final MqttInboxRepository inboxRepository;
    private final R029ContextService r029ContextService;
    private final MqttMessageLogService logService;
    private final SystemContext systemContext;
    private final ObjectMapper objectMapper;
    // 預設的操作者（沒有使用者脈絡時）
    private static final String DEFAULT_OPERATOR = "system";

    // ---------------- Lock / Unlock ----------------

    @Transactional
    public void lock(Long locationId, String reason) {
        var p = getPoint(locationId);
        p.setIsLocked("Y");
        p.setLockReason(reason);
        p.setUpdatedTime(LocalDateTime.now());
        locationPointRepository.update(p);
        // 鎖/解不寫 flow（純點位狀態），如需稽核可在此補寫一張操作日誌表
    }

    @Transactional
    public void unlock(Long locationId) {
        var p = getPoint(locationId);
        p.setIsLocked("N");
        p.setLockReason(null);
        p.setUpdatedTime(LocalDateTime.now());
        locationPointRepository.update(p);
    }

    // ---------------- Reserve / Unreserve ----------------

    @Transactional
    public void reserve(Long locationId, String reason) {
        var p = getPoint(locationId);

        // 基本商規（示意，可依實務補強）
        if (!Objects.equals(p.getEnabled(), "Y")) {
            throw new ResponseStatusException(BAD_REQUEST, "Location disabled.");
        }
        if (Objects.equals(p.getIsOccupied(), "Y")) {
            throw new ResponseStatusException(BAD_REQUEST, "Location is occupied.");
        }

        p.setIsReserved("Y");
        // 如需記錄預約原因，暫借 lockReason 欄位；或另增 reserved_reason 欄位
        if (reason != null && !reason.isBlank()) {
            p.setLockReason(reason);
        }
        p.setUpdatedTime(LocalDateTime.now());
        locationPointRepository.update(p);
    }

    @Transactional
    public void unreserve(Long locationId) {
        var p = getPoint(locationId);
        p.setIsReserved("N");
        p.setUpdatedTime(LocalDateTime.now());
        locationPointRepository.update(p);
    }

    // ---------------- Bind / Clear ----------------

    /**
     * 建帳（綁定 container 至 location）：
     * 1) 驗點位可用（可視商規限制 locked）
     * 2) 解析或建立 ContainerMain
     * 3) 關閉該 container 任何未離開的 flow（避免同一 container 同時多處「未離開」）
     * 4) 新增一筆 location_flow（entryType 預設 MANUAL）
     * 5) upsert tracking（location_point_id 唯一）
     * 6) 將 location_point 標記為佔用、清掉預約
     */
    @Transactional
    public void bind(Long locationId, BindRequest req) {
        // 1) 取點位並檢查是否可用
        var p = getPoint(locationId);

        if (!Objects.equals(p.getEnabled(), "Y")) {
            throw new ResponseStatusException(BAD_REQUEST, "Location disabled.");
        }

        // 若業務上鎖住不可建帳
        if (Objects.equals(p.getIsLocked(), "Y")) {
            throw new ResponseStatusException(BAD_REQUEST, "Location locked.");
        }

        // 嚴格：目標位置不可已佔用
        if (Objects.equals(p.getIsOccupied(), "Y")) {
            throw new ResponseStatusException(BAD_REQUEST, "Location is already occupied.");
        }

        // 有預約也禁止綁定
        if (Objects.equals(p.getIsReserved(), "Y")) {
            throw new ResponseStatusException(BAD_REQUEST, "Location is reserved.");
        }

        // 2) 解析或建立 ContainerMain（嚴格一致，不覆蓋既有資料）
        var cm = resolveOrCreateContainerMain(req);

        // 3) 關鍵：禁止移位
        // 只要這顆 container 已經有 tracking，就直接拒絕（不可把它移到新位置）
        var trByContainer = locationTrackingRepository.findByContainerMainId(cm.getId());
        if (trByContainer.isPresent()) {
            Long boundLocId = trByContainer.get().getLocationPointId();

            // Idempotent：如果本來就綁在同一個位置，視為成功無動作
            if (Objects.equals(boundLocId, p.getId())) {
                return; // 200 OK no-op
            }

            // 取位置代碼（可選）
            String boundCode = locationPointRepository.findById(boundLocId)
                    .map(LocationPoint::getCode).orElse(String.valueOf(boundLocId));

            // 更精準的 409，並帶上 machine-friendly fields
            var ex = new ResponseStatusException(CONFLICT,
                    "Container already bound at " + boundCode + "; relocation is not allowed.");
            // 如果你有自訂錯誤格式（建議用 @ControllerAdvice 統一轉換），
            // 可以改丟自訂的 BusinessException("CONTAINER_ALREADY_BOUND", ... , Map.of("locationId", boundLocId, "locationCode", boundCode))
            throw ex;
        }

        // 同樣嚴格：目標位置也不可已有其他 container 的 tracking
        var trAtLoc = locationTrackingRepository.findByLocationPointId(p.getId());
        if (trAtLoc.isPresent()) {
            throw new ResponseStatusException(
                    BAD_REQUEST, "Location already tracked by another container."
            );
        }

        // 4) 不關閉任何前序 flow（因為我們不允許移位）
        //    直接記一筆新的進帳 flow
        var flow = new LocationFlow();
        flow.setContainerMainId(cm.getId());
        flow.setLocationPointId(p.getId());
        flow.setEntryType("MANUAL");
        flow.setArrivedTime(LocalDateTime.now());
        flow.setEntryOperator(DEFAULT_OPERATOR);
        locationFlowRepository.insert(flow);

        // 5) 新增 tracking（因為前面已保證這顆 container 沒有 tracking）
        var tr = new LocationTracking();
        tr.setLocationPointId(p.getId());
        tr.setContainerMainId(cm.getId());
        tr.setArrivedTime(LocalDateTime.now());
        tr.setLastVerifiedTime(LocalDateTime.now());
        locationTrackingRepository.save(tr);   // 若你的方法叫 insert(...) → 改成 insert

        // 6) 更新點位狀態：佔用；（若允許建帳時自動清預約，保留下面兩行）
        p.setIsOccupied("Y");
        p.setIsReserved("N");
        p.setUpdatedTime(LocalDateTime.now());
        locationPointRepository.update(p);     // 若你的方法叫 save(...) → 改成 save
    }

    /**
     * 清帳（解除 container 與 location 的綁定）：
     * 1) 如果該儲位有 tracking → 先將 location_flow 的最後一筆「未離開」補上 left_time 與 exitType/exitOperator
     * 2) 刪該 tracking
     * 3) location_point 改為未佔用
     */
    @Transactional
    public void clear(Long locationId) {
        var p = getPoint(locationId);

        // 先找 Tracking（找得到才有 containerMainId 可補 flow 離開）
        var trackingOpt = locationTrackingRepository.findByLocationPointId(p.getId());
        trackingOpt.ifPresent(tr -> {

            Long cmId = tr.getContainerMainId();

            // 將該 container 在此 location 的最後未離開紀錄標示離開
            locationFlowRepository.markExit(
                    tr.getContainerMainId(),
                    p.getId(),
                    LocalDateTime.now(),
                    ExitType.MANUAL,          // 預設人工出帳；自動出帳可改 NORMAL/PLC...
                    DEFAULT_OPERATOR          // 可換登入者帳號
            );

            // 刪除 tracking（MyBatis-Plus 常見：deleteById）
            locationTrackingRepository.deleteById(tr.getId());

            // 容器狀態變更：ABORTED（移除/清帳）
            tryAbortContainerIfNeeded(cmId, "LocationOps.clear locationId=" + locationId);

            log.info(
                    "[LocationOps] clear locationId={} locationCode={} containerMainId={}",
                    locationId,
                    p.getCode(),
                    cmId
            );
        });

        // 點位改為未佔用
        p.setIsOccupied("N");
        p.setUpdatedTime(LocalDateTime.now());
        locationPointRepository.update(p);

        // 如需對 ContainerMain 做結案/狀態變更，請在此補上
    }

    // ---------------- Helpers ----------------

    private LocationPoint getPoint(Long locationId) {
        return locationPointRepository.findById(locationId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Location not found: " + locationId));
    }

    /**
     * 依 BindRequest 解析/建立 ContainerMain
     * 規則：
     * 1) 若有 containerMainId → 直接取；找不到則 404
     * 2) 否則至少帶 carrierId 或 containerCode 任一，用來新建一筆 ContainerMain
     */
    private ContainerMain resolveOrCreateContainerMain(BindRequest req) {
        // --- 取用輸入並正規化 ---
        Long   id     = req.getContainerMainId();
        String alias  = req.getCarrierId()     == null ? null : req.getCarrierId().trim();
        String code   = req.getContainerCode() == null ? null : req.getContainerCode().trim();
        String lot    = req.getLotNo();
        String part   = req.getPartNo();

        // --- case 1: 直接用 id 取 ---
        if (id != null) {
            ContainerMain cm = containerRepo.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(
                            NOT_FOUND, "ContainerMain not found: " + id));

            // 有提供其他鍵值 → 必須與資料庫一致（不允許覆蓋/補值）
            if (alias != null && !Objects.equals(alias, cm.getAliasCode())) {
                throw new ResponseStatusException(
                        BAD_REQUEST, "Provided carrierId does not match the containerMainId.");
            }
            if (code != null && !Objects.equals(code, cm.getContainerCode())) {
                throw new ResponseStatusException(
                        BAD_REQUEST, "Provided containerCode does not match the containerMainId.");
            }
            // lot/part 如你也要嚴格比對（強烈建議至少在提供時必須一致）
            if (lot != null && cm.getLotNo() != null && !Objects.equals(lot, cm.getLotNo())) {
                throw new ResponseStatusException(
                        BAD_REQUEST, "Provided lotNo is different from existing data.");
            }
            if (part != null && cm.getPartNo() != null && !Objects.equals(part, cm.getPartNo())) {
                throw new ResponseStatusException(
                        BAD_REQUEST, "Provided partNo is different from existing data.");
            }
            // 注意：不做任何 update（避免補值/覆蓋）
            return cm;
        }

        // --- case 2: 未指定 id，必須至少提供一個 key ---
        if ((alias == null || alias.isBlank()) && (code == null || code.isBlank())) {
            throw new ResponseStatusException(
                    BAD_REQUEST, "Either containerMainId or (carrierId/containerCode) is required.");
        }

        // 先查現有資料（不做任何覆蓋）
        Optional<ContainerMain> byAlias = (alias == null || alias.isBlank())
                ? Optional.empty()
                : containerRepo.findByAliasCode(alias);
        Optional<ContainerMain> byCode = (code == null || code.isBlank())
                ? Optional.empty()
                : containerRepo.findByContainerCode(code);

        // 兩把 key 都有帶且查到不同筆 → 明確矛盾，拒絕
        if (byAlias.isPresent() && byCode.isPresent()
                && !Objects.equals(byAlias.get().getId(), byCode.get().getId())) {
            throw new ResponseStatusException(
                    BAD_REQUEST, "carrierId and containerCode map to different containers.");
        }

        // 有找到既有資料 → 僅作一致性校驗，不更新任何欄位
        if (byAlias.isPresent() || byCode.isPresent()) {
            ContainerMain cm = byAlias.orElseGet(byCode::get);

            // 若另一個 key 有帶，必須完全一致（不允許把空白補進資料庫）
            if (alias != null && !Objects.equals(alias, cm.getAliasCode())) {
                // 這裡包含：資料庫是 null、你有帶值；或資料庫有值但不同
                throw new ResponseStatusException(
                        BAD_REQUEST, "Provided carrierId conflicts with existing container.");
            }
            if (code != null && !Objects.equals(code, cm.getContainerCode())) {
                throw new ResponseStatusException(
                        BAD_REQUEST, "Provided containerCode conflicts with existing container.");
            }
            // lot/part：若你要嚴格一致，就在「雙方都有值」且不同時拒絕
            if (lot != null && cm.getLotNo() != null && !Objects.equals(lot, cm.getLotNo())) {
                throw new ResponseStatusException(
                        BAD_REQUEST, "Provided lotNo is different from existing data.");
            }
            if (part != null && cm.getPartNo() != null && !Objects.equals(part, cm.getPartNo())) {
                throw new ResponseStatusException(
                        BAD_REQUEST, "Provided partNo is different from existing data.");
            }

            // 通過檢查 → 直接使用既有資料，不做更新
            return cm;
        }

        // 都查不到 → 嘗試新建（不會補既有資料）
        ContainerMain cm = new ContainerMain();
        cm.setAliasCode(alias);
        cm.setContainerCode(code);
        cm.setLotNo(lot);
        cm.setPartNo(part);

        try {
            containerRepo.save(cm); // or insert(cm)
            return cm;
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            // 可能併發：別的交易剛好插入了同 alias/code
            Optional<ContainerMain> reselect =
                    (alias != null && !alias.isBlank())
                            ? containerRepo.findByAliasCode(alias)
                            : containerRepo.findByContainerCode(code);

            if (reselect.isPresent()) {
                ContainerMain exist = reselect.get();
                // 新查回來的資料也要和你帶的 key/lot/part 嚴格比對
                if (alias != null && !Objects.equals(alias, exist.getAliasCode())) {
                    throw new ResponseStatusException(CONFLICT, "Concurrent create: carrierId mismatch.");
                }
                if (code != null && !Objects.equals(code, exist.getContainerCode())) {
                    throw new ResponseStatusException(CONFLICT, "Concurrent create: containerCode mismatch.");
                }
                if (lot != null && exist.getLotNo() != null && !Objects.equals(lot, exist.getLotNo())) {
                    throw new ResponseStatusException(CONFLICT, "Concurrent create: lotNo mismatch.");
                }
                if (part != null && exist.getPartNo() != null && !Objects.equals(part, exist.getPartNo())) {
                    throw new ResponseStatusException(CONFLICT, "Concurrent create: partNo mismatch.");
                }
                // 確認無矛盾 → 允許沿用這筆（不更新）
                return exist;
            }
            // 仍找不到就把原例外丟回去
            throw ex;
        }
    }

    private void tryAbortContainerIfNeeded(Long containerMainId, String reason) {
        if (containerMainId == null) return;

        containerRepo.findById(containerMainId).ifPresent(cm -> {
            String st = cm.getState();

            // 已終結就不動，也不吵
            if ("CLOSED".equalsIgnoreCase(st) || "ABORTED".equalsIgnoreCase(st)) return;

            boolean ok = containerRepo.abort(containerMainId);
            if (ok) {
                log.info("[LocationOps] abort containerMainId={} prevState={} ({})",
                        containerMainId, st, reason);
            } else {
                log.warn("[LocationOps] abort FAILED containerMainId={} prevState={} ({})",
                        containerMainId, st, reason);
            }
        });
    }


    public void manualStockOut(String carrierId) {
        if (carrierId == null || carrierId.isBlank()) {
            return;
        }
        ContainerMain cm = containerRepo.findByAliasCode(carrierId).orElse(null);
        if (cm == null)
            return;
        LocationTracking lt = locationTrackingRepository.findByContainerMainId(cm.getId()).orElse(null);
        if (lt == null)
            return;
        LocationPoint lp = locationPointRepository.findById(lt.getLocationPointId()).orElse(null);
        if (lp == null)
            return;

        // 2) 排除：已被任務/請求鎖定
        Set<Long> blockedIds = containerRepo.findContainerIdsWithUnfinishedTasksOrUnacceptedRequests();
        if (blockedIds.contains(cm.getId()))
            return;
        // 3) 排除：R029 佔用
        try {
            Set<Long> r029Occupied = r029ContextService.findOccupiedContainerIds();
            if (r029Occupied == null || r029Occupied.contains(cm.getId()))
                return;
        } catch (Exception e) {
            log.error("[AutoR031] 取得 R029 佔用清單失敗：{}", e.getMessage(), e);
            return;
        }

        // 9) 構造一筆「系統自產」的 R031 命令 payload（供日後追溯）
        R031CommandPayload payload = new R031CommandPayload();
        payload.setCmd("ROBOT");
        payload.setCmdId("R031");
        payload.setTid(BaseMqttHandlerUtils.generateTid());
        R031CommandPayload.Message m = new R031CommandPayload.Message();
        m.setCarrierId(cm.getAliasCode());
        m.setLotId(cm.getLotNo());
        m.setWipName(lp.getName()); // 標示來源
        payload.setMessage(m);

        // 10) 記錄到 mqtt_message_log（COMMAND）取得 logId
        Long logId = logService.recordReturningId(
                "auto://r031",                 // topic（自定）
                systemContext.getSystemCode(),       // sender：本系統
                systemContext.getSystemCode(),       // receiver：本系統
                objectMapper.valueToTree(payload),
                MqttMessageType.COMMAND
        );

        // 11) 寫入 robot_in_R031 主檔/明細
        RobotInR031 main = new RobotInR031();
        main.setLogId(logId);
        main.setCarrierId(cm.getAliasCode());
        main.setLotId(cm.getLotNo());
        main.setWipName(lp.getName());
        if (r031Repository.findById(logId).isPresent()) {
            r031Repository.update(main);
        } else {
            r031Repository.save(main);
        }

        // 13) 建立任務主檔 robot_R031_task
        try {
            if (r031TaskRepository.findByLogId(logId).isEmpty()) {
                LocalDateTime now = LocalDateTime.now();
                RobotR031Task t = new RobotR031Task();
                t.setLogId(logId);
                t.setTid(payload.getTid());
                t.setCarrierId(cm.getAliasCode());
                t.setLotId(cm.getLotNo());
                t.setWipName(lp.getName());
                t.setInternalState("QUEUED");
                t.setExternalLastResult("OK"); // 收單即對外結果 OK（對方收到 ACK=OK）
                t.setExternalLastTime(now);
                t.setCreatedTime(now);
                t.setUpdatedTime(now);
                // 原始 MESSAGE 快照
                t.setRawMessageJson(objectMapper.writeValueAsString(payload));
                r031TaskRepository.save(t);
                log.info("[R031] 任務建立完成：task(logId={}) READY", logId);
            }
        } catch (Exception e) {
            // 不阻斷收單，但務必記錄
            log.error("[R031] 建立 robot_R031_task 失敗（不阻斷）：logId={}, err={}", logId, e.getMessage(), e);
        }

        // 14) 匯入 mqtt_inbox，交由你的既有處理器消化（這邊不建任何 Crane 請求）
        inboxRepository.enqueueFromInbound(
                logId,
                payload.getTid(),
                payload.getCmdId(),
                systemContext.getSystemCode(), // sender
                systemContext.getSystemCode(), // receiver
                "auto://r031",
                LocalDateTime.now(),
                5 // priority
        );

        log.info("[AutoR031] 新增 R031：logId={}, CarrierID={}, LotID={}, WipName={}",
                logId, cm.getAliasCode(), cm.getLotNo(), lp.getName());
    }


}
