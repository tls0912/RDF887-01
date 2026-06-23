package com.czkuo.rdf88701.application.listener;

import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttCommandService;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.application.service.reservation.ReservationOrchestrator;
import com.czkuo.rdf88701.application.service.task.CraneTaskLifecycleService;
import com.czkuo.rdf88701.application.service.transfer.CraneTaskTransferService;
import com.czkuo.rdf88701.common.dto.MqttSendResult;
import com.czkuo.rdf88701.common.dto.mqtt.ack.R008AckPayload;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.domain.repository.*;
import com.czkuo.rdf88701.infra.entity.*;
import com.czkuo.rdf88701.infra.event.model.plc.crane.CraneTaskCompletedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Crane 任務事件監聽器（完整版）
 *
 * 功能重點：
 * 1) 既有 FROM/TO 完成事件處理邏輯維持不變。
 * 2) 當「取（FROM）」完成且 retCode=0x01 時，
 *    - 讀取 container_attr.tray_thickness_mm（單片托盤厚度, mm）；
 *    - 以天車報回的高度（mm）估算層數；
 *    - 記錄日誌，並（可選）回填 container_data 的 estimated/verified。
 *
 * 設計原則：
 * - 厚度只從 DB（container_attr）讀，key：tray_thickness_mm。
 * - 若厚度缺失或格式不正確，不阻擋天車流程（只記警告，略過層數計算）。
 * - 縫隙/容忍常數在此類中定義；如後續要外部化，建議比照你在 Infrared 監聽器的模式。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CraneTaskEventListener {

    private final CraneTaskLifecycleService craneTaskLifecycleService;
    private final CraneTaskTransferService craneTaskTransferService;
    private final ContainerMainRepository containerMainRepository;
    private final ContainerDataRepository containerDataRepository;
    private final ContainerAttrRepository containerAttrRepository;
    private final LocationPointRepository locationPointRepository;
    private final ReservationOrchestrator reservationOrchestrator;
    private final CraneTaskRepository craneTaskRepository;

    private final MqttCommandService mqttCommandService;

    // === 為了回 R008 END ===
    private final RobotR008TaskRepository r008TaskRepository;
    private final MqttMessageEventPublisher eventPublisher;
    private final MqttMessageLogService logService;
    private final ObjectMapper objectMapper;

    @Value("${app.external.ase-system:ase}")
    private String aseSystem;

    // ===== 層數推估相關常數（mm）=====
    /** 每層縫隙 (mm) */
    private static final double GAP_PER_LAYER = 0.06;
    /** 層高容忍 (mm) —— 用於判斷高度是否落在某整數層的容許區間 */
    private static final double LAYER_TOLERANCE = 2.0;

    /** S020 的 目標系統固定先用 ase */
    private static final String s020TargetSystem = "ase";

    /** S020 的 TYPE 固定先用 STK（如需區分可改為從容器或儲位屬性判斷） */
    private static final String S020_TYPE = "STK";

    @EventListener
    public void onCraneTaskCompleted(CraneTaskCompletedEvent event) {
        CraneTask task = event.getTask();

        // 產品資訊：事件優先，沒有就用任務上的容器序號
        String productId = event.getProductId();
        if (StringUtils.isBlank(productId)) {
            productId = task.getContainerAliasCode();
        }

        // 天車回報的高度（單位約定：mm；若你的 PLC 是 1/100 mm，請在此換算）
        double heightRaw = event.getProductHeight();
        boolean isFrom = event.isFrom();
        int retCode = event.getRetCode();
        String description = event.getDescription();
        long taskId = task.getId();

        log.info("[EVENT] Crane 任務完成事件：任務#{} - {} 段完成 (code=0x{} {}) | ProductId='{}' Height={}mm",
                taskId, (isFrom ? "FROM" : "TO"),
                String.format("%02X", retCode), description,
                productId, heightRaw);

        // =========================
        //  在「取（FROM）」完成時估算層數
        // =========================
        if (isFrom && retCode == 0x01) {
            Long containerMainId = task.getContainerMainId();
            if (containerMainId == null) {
                log.warn("[LAYER] 任務#{} 缺少 container_main_id，無法估算層數（略過）", taskId);
            } else {
                Double trayThickness = resolveTrayThicknessSafe(containerMainId);
                if (trayThickness == null) {
                    // 僅記錄，避免影響天車主流程
                    log.warn("[LAYER] 任務#{} container#{} 缺少或格式錯誤之厚度 tray_thickness_mm，略過層數估算",
                            taskId, containerMainId);
                } else {
                    double heightMm = heightRaw; // 若高度不是 mm，請改為相應換算（例如 /100.0）
                    Integer layers = estimateLayersByHeight(heightMm, trayThickness, GAP_PER_LAYER, LAYER_TOLERANCE);

                    if (layers == null) {
                        log.info("[LAYER] 任務#{} 估算層數失敗：height={}mm, thickness={}mm, gap={}mm, tol={}mm",
                                taskId, heightMm, trayThickness, GAP_PER_LAYER, LAYER_TOLERANCE);
                    } else {
                        log.info("[LAYER] 任務#{} 估算層數成功：layers={}（height={}mm, thickness={}mm, gap={}mm, tol={}mm）",
                                taskId, layers, heightMm, trayThickness, GAP_PER_LAYER, LAYER_TOLERANCE);

                        // === 將估算層數回填到 container_data ===
                        try {
                            boolean upOk = containerDataRepository.upsertByContainerMainId(
                                    containerMainId,
                                    layers,   // estimated_quantity
                                    null,     // ocr_text 不動
                                    null,     // verified_quantity（若希望僅估算則可設為 null）
                                    null      // content_kind 不動
                            );
                            log.info("[LAYER] 任務#{} 回填 container_data {}：containerMainId={}, layers={}",
                                    taskId, (upOk ? "成功" : "無異動"), containerMainId, layers);
                        } catch (Exception e) {
                            // 回填資料不應影響天車流程
                            log.error("[LAYER] 任務#{} 回填層數至 container_data 失敗：containerMainId={}, err={}",
                                    taskId, containerMainId, e.getMessage(), e);
                        }
                    }
                }
            }
        }

        // =========================
        //  既有 FROM/TO 完成後處理（原樣）
        // =========================
        if (isFrom) {
            switch (retCode) {
                case 0x01 -> {
                    craneTaskTransferService.markFlowExitOnFromSuccess(task);
                    // FROM 成功：把可能存在的來源/目標預約 TTL 往後延（例如 10 分鐘）
                    try {
                        final long EXTEND_SECS = 600L; // 建議抽成設定
                        String taskType = StringUtils.upperCase(StringUtils.trimToEmpty(task.getTaskType()));

                        // - INBOUND：通常只有「目標位」先被預約 → 延長 tgt
                        // - OUTBOUND：通常預約「來源位」避免回退失位 → 延長 src
                        // - RELOCATE / 追補：可能預約了新目標位 → 延長 tgt
                        if ("OUTBOUND".equals(taskType)) {
                            // 出庫：保護「來源位」(若已預約就續命；沒有就補一筆短TTL)
                            Long srcId = task.getSourceLocationId();
                            if (srcId != null) {
                                reservationOrchestrator.reserveOrExtendOriginForOutbound(
                                        task.getContainerMainId(),
                                        srcId,
                                        EXTEND_SECS,
                                        "SYSTEM",
                                        "AFTER_FROM_OK"
                                );
                            }
                            // S020 上報，出庫時取成功就上報
                            sendS020OnPlacementIfNeeded(task);
                        } else {
                            // INBOUND / RELOCATE：保護「目標位」（只在有有效預約時續命）
                            Long tgtId = task.getTargetLocationId();
                            if (tgtId != null) {
                                reservationOrchestrator.extendIfActive(tgtId, EXTEND_SECS, "AFTER_FROM_OK");
                            }
                        }
                    } catch (Exception e) {
                        log.warn("[Reservation] extend TTL on FROM success failed: taskId={}", task.getId(), e);
                    }
                }
                case 0x06 -> { // 取料位位置為空-取消任務
                    // 1) 先取消「目標位」的預約（避免卡住）
                    try {
                        Long tgtId = task.getTargetLocationId();
                        if (tgtId != null) {
                            reservationOrchestrator.cancelIfExists(tgtId, "FROM failed 0x06");
                        }
                    } catch (Exception e) {
                        log.warn("[Reservation] cancel target on 0x60 failed: taskId={}", task.getId(), e);
                    }

                    // 2) 僅記錄 follow-up，交由監控器 CraneFollowUpTaskMonitor 建立 RELOCATE（回原位）
                    craneTaskTransferService.markTaskFailed(task, "FROM failed: 0x06 取料位為空");
                }
                case 0x0E -> craneTaskTransferService.markTaskFailed(task, "空取完成");
                default   -> craneTaskLifecycleService.markRetry(task);
            }
        } else {
            switch (retCode) {
                case 0x10 -> {
                    // 放成功 → 完成既有更新後，依 taskType 決定是否/如何上報 S020
                    craneTaskTransferService.updateFlowAndTrackingOnToSuccess(task);
                    craneTaskTransferService.markTaskCompleted(task);

                    // 1) 履約「目標位」的預約（把 reservation 標 fulfilled）
                    try {
                        Long tgtId = task.getTargetLocationId();
                        if (tgtId != null) {
                            reservationOrchestrator.fulfillIfExists(tgtId);
                        }
                    } catch (Exception e) {
                        log.warn("[Reservation] fulfill on TO success failed: taskId={}", task.getId(), e);
                    }

                    // 2) 來源位預留已無必要 → 取消來源位預約（若存在）
                    try {
                        Long srcId = task.getSourceLocationId();
                        if (srcId != null) {
                            reservationOrchestrator.cancelIfExists(srcId, "TO success");
                        }
                    } catch (Exception e) {
                        log.warn("[Reservation] cancel origin on TO success failed: taskId={}", task.getId(), e);
                    }

                    // 3) S020 上報（沿用原本），不是出庫時放成功才上報
                    String taskType = StringUtils.upperCase(StringUtils.trimToEmpty(task.getTaskType()));
                    if (!"OUTBOUND".equals(taskType))
                        sendS020OnPlacementIfNeeded(task);

                    // 4) 若存在進行中的 R008 且 carrierId 相符 → 回 ASE END 並更新任務為 END/COMPLETED
                    // tryFinalizeR008IfAny(task); // AMR 結束發，如若進倉才報完成再打開。
                }
                case 0x60 -> { // 放置點已有物 — 規則：放回原儲位
                    // 1) 先取消「目標位」的預約（避免卡住）
                    try {
                        Long tgtId = task.getTargetLocationId();
                        if (tgtId != null) {
                            reservationOrchestrator.cancelIfExists(tgtId, "TO failed 0x60");
                        }
                    } catch (Exception e) {
                        log.warn("[Reservation] cancel target on 0x60 failed: taskId={}", task.getId(), e);
                    }

                    // 2) 確保「來源位」仍被預留（若當時沒預留或已過期，就補一筆短 TTL 預留）
                    try {
                        Long srcId = task.getSourceLocationId();
                        if (srcId != null) {
                            reservationOrchestrator.reserveOriginForOutbound(
                                    task.getContainerMainId(),
                                    srcId,
                                    300,  // 建議 3~5 分鐘
                                    "SYSTEM",
                                    "ROLLBACK_ON_0x60"
                            );
                        }
                    } catch (Exception e) {
                        log.warn("[Reservation] reserve origin on 0x60 failed: taskId={}", task.getId(), e);
                    }

                    // 3) 僅記錄 follow-up，交由監控器 CraneFollowUpTaskMonitor 建立 RELOCATE（回原位）
                    craneTaskTransferService.recordFollowUpRequiredFailure(task, "0x60", "PLACE_OCCUPIED_ROLLBACK");
                    craneTaskTransferService.markTaskFailed(task, "TO failed: 0x60 放置已有物 → 回原位");
                }
                case 0xD0 -> { // 換另一個位置（走原本的「挑新儲位」補償流程）
                    // 1) 取消「目標位」的預約
                    try {
                        Long tgtId = task.getTargetLocationId();
                        if (tgtId != null) {
                            reservationOrchestrator.cancelIfExists(tgtId, "TO failed 0xD0");
                        }
                    } catch (Exception e) {
                        log.warn("[Reservation] cancel target on 0xD0 failed: taskId={}", task.getId(), e);
                    }

                    // 2) 僅記錄 follow-up，交由監控器 CraneFollowUpTaskMonitor 建立 RELOCATE（挑新位）
                    craneTaskTransferService.recordFollowUpRequiredFailure(task, "0xD0", "放置已有物，改派其他儲位");
                    craneTaskTransferService.markTaskFailed(task, "TO failed: 0xD0 放置已有物 → 換其他儲位");
                }
                default -> craneTaskLifecycleService.markRetry(task);
            }
        }
    }

    // ======================= 新增：R008 END 整合 =======================

    /** TO 成功後呼叫：以 carrierId 找進行中 R008 任務 → 發 ASE END 並更新 task 為 END/COMPLETED */
    private void tryFinalizeR008IfAny(CraneTask task) {
        Long cmId = task.getContainerMainId();
        if (cmId == null) return;

        // carrierId = container_main.alias_code
        String carrierId = containerMainRepository.findById(cmId)
                .map(ContainerMain::getAliasCode)
                .map(String::trim)
                .orElse(null);
        if (StringUtils.isBlank(carrierId)) return;

        // 找 open 的 R008 任務（QUEUED/PROCESSING 之類）再比對 carrierId
        RobotR008Task match = r008TaskRepository.findOpen().stream()
                .filter(t -> StringUtils.equalsIgnoreCase(carrierId, nz(t.getCarrierId())))
                .findFirst()
                .orElse(null);
        if (match == null) {
            //log.debug("[R008][END] 無需上報：找不到 open R008 與 carrierId={} 相符的任務", carrierId);
            return;
        }

        // 以 Crane Task 的 TO 目標位置名稱作為 WIPNAME
        String wipNameFromToLoc = resolveWipName(task.getTargetLocationId());

        // NUM
        String num = resolveQuantityString(cmId);

        // OLD Num
        String oldNum = match.getTrayNum().toString();

        if (!num.equals(oldNum)) {
            log.warn("[LAYER] 任務#{}  與推估值不一致：oldNum={} → newNUM={}", task.getId(), oldNum, num);
        }

        try {
            // 1) 發 ACK: R008 END → ASE（沿用 R008 任務 TID）
            R008AckPayload out = new R008AckPayload();
            out.setCmd("ROBOT");
            out.setCmdId("R008");
            out.setTid(match.getTid());
            out.setIdDesc("ROBOT_MOVE_EQP_TO_SCH");

            R008AckPayload.Message m = new R008AckPayload.Message();
            m.setLotId(match.getLotId());
            m.setCarrierId(match.getCarrierId());
            m.setWipName(wipNameFromToLoc);
            m.setDestLoc(match.getDestLoc());
            m.setEqpPort(match.getEqpPort());
            m.setTrayHigh(match.getTrayHigh());
            m.setTrayType(match.getTrayType());
            m.setBinType(match.getBinType());
            m.setTrayNum(Integer.valueOf(num));
            m.setDeviceName(match.getDeviceName());
            m.setMovePriority(match.getMovePriority());
            m.setMissionTrip(match.getMissionTrip());
            m.setOdo(match.getOdo());
            m.setAmrSpeed(match.getAmrSpeed());
            m.setAmrRobotSpeed(match.getAmrRobotSpeed());
            m.setPpkgBodySize(match.getPpkgBodySize());
            out.setMessage(m);

            out.setResult("END");
            out.setResultMessage("");

            JsonNode payload = objectMapper.valueToTree(out);
            logService.recordReturningId(
                    "ack/r008/auto-end",
                    logService.getLocalSystem(),
                    aseSystem,
                    payload,
                    MqttMessageType.ACK
            );

            eventPublisher.publish(
                    aseSystem,
                    objectMapper.writeValueAsString(out),
                    MqttMessageType.ACK,
                    out.getTid(),
                    out.getCmdId()
            );

            log.info("[R008][END→ASE] 已自動上報：tid={}, carrierId={}, receiver={}", out.getTid(), carrierId, aseSystem);

            // 2) 更新任務：external_last_result=END、internal_state=COMPLETED
            RobotR008Task patch = new RobotR008Task();
            patch.setLogId(match.getLogId());
            patch.setExternalLastResult("END");
            patch.setExternalLastTime(LocalDateTime.now());
            patch.setInternalState("COMPLETED");
            patch.setUpdatedTime(LocalDateTime.now());

            boolean ok = r008TaskRepository.updateByLogId(patch);
            if (!ok) {
                log.warn("[R008][END] 任務狀態更新失敗：logId={}", match.getLogId());
            } else {
                log.info("[R008][END] 任務已更新為 COMPLETED/END：logId={}", match.getLogId());
            }
        } catch (Exception e) {
            log.error("[R008][END] 自動上報或更新任務失敗：carrierId={}, err={}", carrierId, e.getMessage(), e);
        }
    }

    // =====================================================================
    //                           S020 相關私有方法
    // =====================================================================

    /**
     * 只在「放（TO）成功」之後呼叫：
     * - INBOUND  → S020-2001（入庫完成），WIPNAME=目標儲位（targetLocationId）
     * - OUTBOUND → S020-2002（出庫完成），WIPNAME=來源儲位（sourceLocationId）
     * - RELOCATE → 不上報
     */
    private void sendS020OnPlacementIfNeeded(CraneTask task) {
        String taskType = org.apache.commons.lang3.StringUtils.upperCase(
                org.apache.commons.lang3.StringUtils.trimToEmpty(task.getTaskType())
        );

        switch (taskType) {
            case "INBOUND" -> {
                // 入庫完成：WIPNAME 取 target（上到哪個儲位）
                sendS020StockInSafely(task);
            }
            case "OUTBOUND" -> {
                // 出庫完成：WIPNAME 取 source（從哪個儲位離開）
                sendS020StockOutSafely(task);
            }
            case "RELOCATE" -> {
                // 內部搬移不需上報（若將來要報，可在此補 CEID）
                sendS020StockInSafely(task);
                //log.debug("[S020] 任務#{} taskType=RELOCATE，不上報 S020。", task.getId());
            }
            default -> {
                log.warn("[S020] 任務#{} 未知 taskType='{}'，不上報 S020。", task.getId(), taskType);
            }
        }
    }

    /** 出庫完成：CEID=2002，WIPNAME=來源儲位 */
    private void sendS020StockOutSafely(CraneTask task) {
        try {
            Long cmId = task.getContainerMainId();
            if (cmId == null) {
                log.warn("[S020] 出庫完成略過：containerMainId 為 null（taskId={}）", task.getId());
                return;
            }

            ContainerMain cm = containerMainRepository.findById(cmId).orElse(null);
            if (cm == null) {
                log.warn("[S020] 出庫完成略過：找不到 containerMain（id={}）", cmId);
                return;
            }

            String oneDBarcode = nz(cm.getContainerCode()); // 1D_BARCODE
            String lotId       = nz(cm.getLotNo());         // LOT_ID
            String carrierId   = nz(cm.getAliasCode());     // CARRIERID

            String wipName = resolveWipName(task.getSourceLocationId()); // 來源儲位
            String num     = resolveQuantityString(cmId);                // NUM

            MqttSendResult r = mqttCommandService.sendS020_2002_StockOut(
                    s020TargetSystem, oneDBarcode, lotId, carrierId, S020_TYPE, wipName, num
            );

            if (r.isSuccess()) {
                log.info("[S020] 📤 出庫完成(2002) sent. receiver={}, lotId={}, carrierId={}, wip={}, num={}, TID={}",
                        s020TargetSystem, lotId, carrierId, wipName, num, r.getTid());
            } else {
                log.warn("[S020] 出庫完成(2002) send FAILED: {}", r.getMessage());
            }
        } catch (Exception e) {
            log.error("[S020] 出庫完成(2002) 發送例外：taskId={}", task.getId(), e);
        }
    }

    /** 入庫完成：CEID=2001，WIPNAME=目標儲位 */
    private void sendS020StockInSafely(CraneTask task) {
        try {
            Long cmId = task.getContainerMainId();
            if (cmId == null) {
                log.warn("[S020] 入庫完成略過：containerMainId 為 null（taskId={}）", task.getId());
                return;
            }

            ContainerMain cm = containerMainRepository.findById(cmId).orElse(null);
            if (cm == null) {
                log.warn("[S020] 入庫完成略過：找不到 containerMain（id={}）", cmId);
                return;
            }

            String oneDBarcode = nz(cm.getContainerCode()); // 1D_BARCODE
            String lotId       = nz(cm.getLotNo());         // LOT_ID
            String carrierId     = nz(cm.getAliasCode());   // CARRIERID

            String wipName = resolveWipName(task.getTargetLocationId()); // 目標儲位
            String num     = resolveQuantityString(cmId);                // NUM

            MqttSendResult r = mqttCommandService.sendS020_2001_StockIn(
                    s020TargetSystem, oneDBarcode, lotId, carrierId, S020_TYPE, wipName, num
            );

            if (r.isSuccess()) {
                log.info("[S020] 📤 入庫完成(2001) sent. receiver={}, lotId={}, carrierId={}, wip={}, num={}, TID={}",
                        s020TargetSystem, lotId, carrierId, wipName, num, r.getTid());
            } else {
                log.warn("[S020] 入庫完成(2001) send FAILED: {}", r.getMessage());
            }
        } catch (Exception e) {
            log.error("[S020] 入庫完成(2001) 發送例外：taskId={}", task.getId(), e);
        }
    }

    /** 用 LocationPoint 取得 WIPNAME；若具備 zoneCode+locationCode(6碼) → 走 WIP_{zone}_{xx}{yy}{zz} 命名。 */
    private String resolveWipName(Long locationId) {
        if (locationId == null) return "";
        try {
            Optional<LocationPoint> opt = locationPointRepository.findById(locationId);
            if (opt.isEmpty()) return String.valueOf(locationId);

            LocationPoint lp = opt.get();
            String locCode = lp.getCode();
            String name    = lp.getName();

            if (StringUtils.isNotBlank(name)) return name;

            // 次選：有 name 就用 name；再不行用 locationCode；最後退回 id 字串
            if (StringUtils.isNotBlank(locCode)) return locCode;
            return String.valueOf(locationId);
        } catch (Exception e) {
            log.error("[S020] 解析 WIPNAME 失敗：locationId={}", locationId, e);
            return String.valueOf(locationId);
        }
    }

    /** NUM 欄位：優先 verified_quantity，其次 estimated_quantity；都沒有則回 "0"。 */
    private String resolveQuantityString(Long containerMainId) {
        try {
            Optional<ContainerData> cd = containerDataRepository.findByContainerMainId(containerMainId);
            if (cd.isPresent()) {
                ContainerData d = cd.get();
                Integer v = d.getVerifiedQuantity();
                if (v == null || v < 0) v = d.getEstimatedQuantity();
                if (v != null && v >= 0) return String.valueOf(v);
            }
        } catch (Exception e) {
            log.warn("[S020] 取得 NUM 失敗：containerMainId={}, err={}", containerMainId, e.getMessage());
        }
        return "0";
    }

    // =====================================================================
    //                              其他私有工具
    // =====================================================================

    /** 讀取單片托盤厚度（mm）；來源 container_attr.key=tray_thickness_mm。格式寬鬆；錯誤回 null。 */
    private Double resolveTrayThicknessSafe(Long containerMainId) {
        try {
            Optional<ContainerAttr> opt = containerAttrRepository.findOne(containerMainId, "tray_thickness_mm");
            String raw = opt.map(ContainerAttr::getAttrValue).orElse(null);
            return parseDecimalPositive(raw);
        } catch (Exception e) {
            log.error("[LAYER] 讀取 tray_thickness_mm 例外：containerMainId={}, err={}", containerMainId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 由高度反推層數：
     * height(N) = N*thickness + (N-1)*gap（N=0 時為 0）；
     * 找符合 [height(N)-tol, height(N)+tol] 的整數 N，找不到回 null。
     */
    private Integer estimateLayersByHeight(double heightMm, double thickness, double gap, double tol) {
        if (thickness <= 0 || heightMm < 0) return null;
        double rawLayers = (heightMm + gap) / (thickness + gap);
        int maxLayer = Math.max(0, (int) Math.ceil(rawLayers) + 2);
        for (int layer = 0; layer <= maxLayer; layer++) {
            double h = (layer == 0) ? 0.0 : layer * thickness + (layer - 1) * gap;
            if (heightMm >= (h - tol) && heightMm <= (h + tol)) return layer;
        }
        return null;
    }

    /** 寬鬆數值解析並要求正數：允許 "5.62", "5,62", "5.62mm"；非正或格式不對回 null。 */
    private static Double parseDecimalPositive(String raw) {
        if (raw == null) return null;
        String n = raw.trim().replaceAll("[^0-9,\\.\\-]", "");
        if (n.isEmpty()) return null;
        if (n.contains(".") && n.contains(",")) {
            n = n.replace(",", "");
        } else if (n.contains(",") && !n.contains(".")) {
            n = n.replace(',', '.');
        }
        try {
            double v = Double.parseDouble(n);
            return v > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
