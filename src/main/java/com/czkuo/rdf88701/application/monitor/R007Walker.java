package com.czkuo.rdf88701.application.monitor;

import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.mqtt.util.BaseMqttHandlerUtils;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.application.service.zip.ZipStockerCommandService;
import com.czkuo.rdf88701.common.dto.mqtt.ack.R007AckPayload;
import com.czkuo.rdf88701.common.dto.mqtt.command.R007CommandPayload;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.enums.ZipTarget;
import com.czkuo.rdf88701.domain.dto.zip.DispatchOrder.DispatchOrderSecondaryBody;
import com.czkuo.rdf88701.domain.dto.zip.StatusQuery.StatusQueryPrimaryBody;
import com.czkuo.rdf88701.domain.dto.zip.StatusQuery.StatusQuerySecondaryBody;
import com.czkuo.rdf88701.domain.dto.zip.common.Root;
import com.czkuo.rdf88701.domain.repository.MqttInboxRepository;
import com.czkuo.rdf88701.domain.repository.RobotInR007Repository;
import com.czkuo.rdf88701.domain.repository.RobotR007TaskRepository;
import com.czkuo.rdf88701.infra.entity.MqttInbox;
import com.czkuo.rdf88701.infra.entity.RobotInR007;
import com.czkuo.rdf88701.infra.entity.RobotR007Task;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;


/**
 * R007Walker（ZIPA 成功接單後，沿用原 TID 轉發 R007 給 SEEC，並補上 STK_PORT）
 * <p>
 * ============================================================
 * 核心目標
 * ============================================================
 * 1) ZIPA 端：在「ZIPA 倉儲」派單之前，先確保 carrier 狀態正確（必須上架 33）。
 * 2) STK_PORT 決策：決定要從 STK01 還是 STK02 出料（對應 OP 口 STK01-OP / STK02-OP 空位）。
 * 3) AMR 端：ZIPA 接單成功後，轉發 R007 給 SEEC（COMMAND），並回 ACK(START) 給來源系統。
 * <p>
 * ============================================================
 * 特別新增規則：dest_loc 綁定
 * ============================================================
 * - 情境：系統內可能同時有多筆 R007 指向相同的 DEST_LOC（同一台目的設備/Port）。
 * - 目的：避免同一個 DEST_LOC 的多筆任務各自挑不同 OP 口，造成出料口混亂/互搶。
 * <p>
 * 規則（最重要）：
 * A) 只要「本筆任務 task.stkPort 為空」，
 * B) 且 DB 內存在「相同 dest_loc 的未終結任務」並且那筆已有 stkPort，
 * C) 則本筆 stkPort 必須沿用那筆 stkPort（直接壓成同一個），
 * 不允許重新挑空 OP 口。
 * <p>
 * 注意：
 * - 這個 dest_loc 綁定規則「不依賴 ZIPA 是否有 Status=62」，
 * 也不需要 ZIPA 正在執行中才套用；只要 DB 內有 open 任務即可壓。
 * <p>
 * ============================================================
 * 決策順序（只查 ZIPA）
 * ============================================================
 * 0) （新增）dest_loc 綁定：若 task.stkPort 空 → 嘗試用 DB 內同 dest_loc 的 open 任務 stkPort 來補齊
 * 1) type=5（name="*"）：只要有任何 Status=62（有命令且執行中）→ 先不派單（requeue）
 * 2) type=2（name=carrierId）：carrier 必須在架上（status=33），否則 requeue
 * 3) type=6（name="*"）：一次全拿，用 Message[1]=CarrierID 判斷 STK01-OP / STK02-OP 是否空位
 * - 但只有在 stkPort 仍為空且沒有被 dest_loc 綁定補齊時，才允許走「挑空口」
 * 4) DispatchOrder（ZIPA）成功後 → 轉發「同 TID 的 R007（含 STK_PORT）」給 SEEC（COMMAND）
 * 5) 回 ACK：START；更新 robot_r007_task；inbox DONE（等 ACK handler 完結）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已修改，// 註解已依現有實作校正。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class R007Walker {

    private final MqttInboxRepository inboxRepo;
    private final RobotInR007Repository r007Repo;
    private final RobotR007TaskRepository taskRepo;
    private final ZipStockerCommandService zipService;
    private final MqttMessageEventPublisher publisher;
    private final MqttMessageLogService logService;
    private final ObjectMapper objectMapper;

    @Value("${app.worker.r007.enabled:true}")
    private boolean enabled;

    @Value("${app.worker.r007.lock-ttl-seconds:120}")
    private int lockTtlSeconds;

    @Value("${app.worker.r007.backoff-seconds:30}")
    private int backoffSeconds;

    @Value("${app.worker.r007.interval-ms:500}")
    private long intervalMs;

    @Value("${spring.application.name:r007-worker}")
    private String workerId;

    @Value("${app.worker.r007.amr-timeout-seconds:30}")
    private int amrTimeoutSeconds;     // 單次發送後等待 ACK 的逾時門檻

    @Value("${app.worker.r007.amr-max-attempts:3}")
    private int amrMaxAttempts;        // 最多嘗試次數（含第一次送）

    @Value("${app.worker.r007.retry-backoff-seconds:10}")
    private int retryBackoffSeconds;   // 等待 ACK 或計畫重送時的 requeue 退避

    @Value("${app.worker.r007.amr-gate-min-wait-seconds:5}")
    private int amrGateMinWaitSeconds; // Gate 不成立後最短等待秒數

    @Value("${app.worker.r007.amr-gate-max-wait-seconds:10}")
    private int amrGateMaxWaitSeconds; // Gate 不成立後最長等待秒數（與 min 組成 5~10 秒）

    @Value("${app.worker.r007.amr-scan-limit:50}")
    private int amrScanLimit;          // AMR worker 每輪最多掃描任務筆數（用 findOpenLimited）

    /**
     * 常數：STK group 需要檢查的點位 suffix
     */
    private static final List<String> STK_GROUP_SUFFIXES =
            List.of("Buffer", "Car", "FP", "OP", "AMR");

    /**
     * 每 interval-ms 嘗試處理一筆
     */
    @Scheduled(fixedDelayString = "${app.worker.r007.interval-ms:700}")
    public void tickZip() {
        if (!enabled) return;
        processZipOnce();
    }

    /**
     * ZIP worker：單次處理一筆 inbox
     */
    public void processZipOnce() {
        Optional<MqttInbox> opt = inboxRepo.pickOneForProcessingByCmdNoNextAttemptTime(
                "R007", workerId, Duration.ofSeconds(lockTtlSeconds));
        if (opt.isEmpty()) return;

        MqttInbox inbox = opt.get();
        Long inboxId = inbox.getId();
        Long logId = inbox.getLogId();

        try {
            // ------------------------------------------------------------
            // 基本檢查：必須是 R007
            // ------------------------------------------------------------
            if (!"R007".equalsIgnoreCase(inbox.getCmdId())) {
                log.warn("[R007-ZIP] 非 R007，inboxId={}, cmdId={}", inboxId, inbox.getCmdId());
                inboxRepo.requeue(inboxId, Duration.ofSeconds(1));
                return;
            }

            // ------------------------------------------------------------
            // 讀入站明細 & 任務主表
            // ------------------------------------------------------------

            // ===== [早退-1] RobotInR007 必須存在（入站 R007 的解析結果）=====
            Optional<RobotInR007> mOpt = r007Repo.findById(logId);
            if (mOpt.isEmpty()) {
                String reason = "robot_in_r007 not found, logId=" + logId;
                // 若 task 已存在，順手把任務標 FAILED（以利監控）
                taskRepo.findByLogId(logId).ifPresent(t -> {
                    try {
                        RobotR007Task patch = new RobotR007Task();
                        patch.setLogId(logId);
                        patch.setInternalState("FAILED");
                        patch.setFailReason(reason);
                        patch.setUpdatedTime(LocalDateTime.now());
                        taskRepo.updateByLogId(patch);
                    } catch (Exception ignore) {
                    }
                });
                inboxRepo.markRejected(inboxId, reason);
                log.error("[R007-ZIP] {}；inboxId={} → REJECTED", reason, inboxId);
                return;
            }
            RobotInR007 m = mOpt.get();

            // ===== [早退-2] RobotR007Task 必須存在（你這套設計：任務表先由 handler 建好）=====
            Optional<RobotR007Task> tOpt = taskRepo.findByLogId(logId);
            if (tOpt.isEmpty()) {
                String reason = "robot_r007_task not found, logId=" + logId;
                inboxRepo.markRejected(inboxId, reason);
                log.error("[R007-ZIP] {}；inboxId={} → REJECTED", reason, inboxId);
                return;
            }
            RobotR007Task task = tOpt.get();

            // ------------------------------------------------------------
            // carrierId 必須存在
            // ------------------------------------------------------------
            final String carrierId = m.getCarrierId();
            if (carrierId == null || carrierId.isBlank()) {
                log.warn("[R007-ZIP] 缺少 carrierId，requeue inboxId={}", inboxId);
                inboxRepo.requeue(inboxId, Duration.ofSeconds(backoffSeconds));
                return;
            }

            // ------------------------------------------------------------
            // 若 ZIP 已 ACCEPTED：ZIP worker 的工作已完成
            //  - 以前會在這裡做 AMR；現在拆出去，所以直接 inbox DONE，釋放吞吐量
            // ------------------------------------------------------------
            boolean zipAccepted = "ACCEPTED".equalsIgnoreCase(task.getZipState())
                    || task.getZipAcceptTime() != null;

            if (zipAccepted) {
                // ZIP 已接單，不需要再 requeue 卡住；直接 DONE
                inboxRepo.markDone(inboxId, "R007", null);
                log.info("[R007-ZIP] ZIP 已 ACCEPTED，inbox DONE；logId={}, carrier={}", logId, carrierId);
                return;
            }

            // ------------------------------------------------------------
            // 目前 task 上既有 stkPort（可能早已寫入）
            // ------------------------------------------------------------
            String stkPort = task.getStkPort();

            // ------------------------------------------------------------
            // (0) dest_loc 綁定
            // ------------------------------------------------------------
            // 若本筆 stkPort 尚未決定，先嘗試用「同 dest_loc 的未終結任務」來補齊。
            // 這樣可以保證同一個目的設備（dest_loc）在同一時段內，出料口一致。
            if (stkPort == null || stkPort.isBlank()) {
                String destLoc = nvl(task.getDestLoc(), m.getDestLoc());
                if (destLoc != null && !destLoc.isBlank()) {
                    taskRepo.findLatestOpenWithStkPortByDestLoc(destLoc)
                            .map(RobotR007Task::getStkPort)
                            .filter(p -> p != null && !p.isBlank())
                            .ifPresent(p -> {
                                // 只在本筆空的時候壓同一個
                                log.info("[R007-ZIP] dest_loc 綁定：destLoc={} 已有 open 任務 stkPort={}，本筆沿用（logId={}）",
                                        destLoc, p, logId);
                                // 同步寫回 task（避免下次 tick 又重新進來挑空口）
                                try {
                                    RobotR007Task patch = new RobotR007Task();
                                    patch.setLogId(logId);
                                    patch.setStkPort(p);
                                    patch.setUpdatedTime(LocalDateTime.now());
                                    taskRepo.updateByLogId(patch);
                                } catch (Exception e) {
                                    log.warn("[R007-ZIP] dest_loc 綁定寫回 task 失敗：logId={}, err={}", logId, e.getMessage());
                                }
                            });
                    // 重新讀一次 task.stkPort（避免 lambda effectively final 問題）
                    try {
                        stkPort = taskRepo.findByLogId(logId).map(RobotR007Task::getStkPort).orElse(stkPort);
                    } catch (Exception ignore) {
                    }
                }
            }

            // ------------------------------------------------------------
            // (1) ZIP 執行中保護（type=5, status=62）
            // ------------------------------------------------------------
            // 現行策略：只要 ZIPA 有任何執行中派單（62），這筆先不派（requeue）。
            // 注意：dest_loc 綁定規則已在前面做完（即便 requeue，也已確保 stkPort 一致性）。
            if (zipHasAnyExecutingOrder(ZipTarget.ZIPA)) {
                log.info("[R007-ZIP] ZIPA 有任務執行中(Status=62)，暫不派；requeue inboxId={}", inboxId);
                inboxRepo.requeue(inboxId, Duration.ofSeconds(backoffSeconds));
                return;
            }

            // ------------------------------------------------------------
            // (2) carrier 狀態需=33（上架）
            // ------------------------------------------------------------
            Integer status2 = queryType2Status(ZipTarget.ZIPA, carrierId);
            if (status2 == null) {
                log.info("[R007-ZIP] ZIPA 未找到產品；requeue inboxId={}", inboxId);
                inboxRepo.requeue(inboxId, Duration.ofSeconds(backoffSeconds));
                return;
            }
            if (status2 != 33) {
                log.info("[R007-ZIP] ZIPA 產品狀態非上架(33)目前={}；requeue inboxId={}", status2, inboxId);
                inboxRepo.requeue(inboxId, Duration.ofSeconds(backoffSeconds));
                return;
            }

            // ------------------------------------------------------------
            // (3) 挑 OP 口（只有在 stkPort 仍為空時才允許挑）
            // ------------------------------------------------------------
            // 這裡同時修正你原本的坑：
            // - inventory name 可能有 "ZIPA_" 前綴
            // - 找不到時不能回傳 "NOTFOUND..." 這種字串污染判斷
            if (stkPort == null || stkPort.isBlank()) {
                Root<StatusQuerySecondaryBody> inv = zipService.queryInventory(ZipTarget.ZIPA);
                stkPort = pickEmptyOpPortFromInventory(inv);
                if (stkPort == null) {
                    log.info("[R007-ZIP] 無可用 OP 口，requeue inboxId={}", inboxId);
                    inboxRepo.requeue(inboxId, Duration.ofSeconds(backoffSeconds));
                    return;
                }
            }

            // ------------------------------------------------------------
            // (4) ZIP 派單（記 attempt→送出→依結果標記）
            // ------------------------------------------------------------
            taskRepo.zipMarkAttempt(logId, safeToJson(
                    Map.of("carrier", carrierId, "stkPort", stkPort, "target", "ZIPA")));

            Root<DispatchOrderSecondaryBody> resp =
                    zipService.sendDispatchOrder(ZipTarget.ZIPA, List.of(carrierId), stkPort);

            boolean zipOk = resp != null
                    && resp.getBody() != null
                    && resp.getBody().getResultInfos() != null
                    && !resp.getBody().getResultInfos().isEmpty()
                    && toInt(resp.getBody().getResultInfos().get(0).getResult()) == 0;

            String zipRespJson = safeToJson(resp);
            if (!zipOk) {
                String code = (resp == null || resp.getBody() == null
                        || resp.getBody().getResultInfos() == null || resp.getBody().getResultInfos().isEmpty())
                        ? "NO_RESULT"
                        : String.valueOf(toInt(resp.getBody().getResultInfos().get(0).getResult()));
                taskRepo.zipMarkRejected(logId, code, "ZIPA DispatchOrder 失敗", zipRespJson);
                log.warn("[R007-ZIP] ZIP 派單失敗 code={}；requeue inboxId={}", code, inboxId);
                inboxRepo.requeue(inboxId, Duration.ofSeconds(backoffSeconds));
                return;
            }

            // ------------------------------------------------------------
            // (5) ZIP 接單成功：標記 + 寫入 STK_PORT（internal_state=PROCESSING）
            // ------------------------------------------------------------
            taskRepo.zipMarkAccepted(logId, zipRespJson, "OK");
            upsertTaskAssignZIP(inbox, m, stkPort);

            // ------------------------------------------------------------
            // 核心改造：ZIP 接單成功後「立刻」把 inbox DONE
            // ------------------------------------------------------------
            // 這樣可以確保：即使 AMR Gate 不成立，ZIP worker 也不會被同一筆 inbox 反覆卡住。
            // AMR 轉發交給 tickAmr() 以 task 驅動方式處理。
            inboxRepo.markDone(inboxId, "R007", null);
            log.info("[R007-ZIP] ZIP accepted → inbox DONE（不在此送 SEEC）；logId={}, carrier={}, stkPort={}",
                    logId, carrierId, stkPort);

        } catch (Exception e) {
            log.error("[R007-ZIP] 例外，requeue inboxId={}, err={}", inboxId, e.getMessage(), e);
            try {
                taskRepo.zipMarkError(logId, e.getMessage());
            } catch (Exception ignore) {
            }
            inboxRepo.requeue(inboxId, Duration.ofSeconds(backoffSeconds));
        }
    }

    /**
     * AMR worker：固定延遲掃描任務，負責：
     * - 在 ZIP 已 ACCEPTED 後，依 Gate 規則決定是否可轉發給 SEEC
     * - timeout 後重送（SENT + 超過 amrTimeoutSeconds + attempts < amrMaxAttempts）
     * <p>
     * Gate 不成立：本任務冷卻 5~10 秒再看（不動表，使用 amr_last_attempt_time 來當 gate check time）
     */
    @Scheduled(fixedDelayString = "${app.worker.r007.amr-interval-ms:700}")
    public void tickAmr() {
        if (!enabled) return;
        processAmrOnce();
    }

    /**
     * AMR worker：單次最多處理一筆任務（避免一次掃描太久）
     */
    public void processAmrOnce() {
        try {
            // ------------------------------------------------------------
            // 1) 從 DB 取一批 open 任務（你 repository 已提供 findOpenLimited）
            // ------------------------------------------------------------
            List<RobotR007Task> candidates = taskRepo.findOpenLimited(amrScanLimit);
            if (candidates == null || candidates.isEmpty()) return;

            // ------------------------------------------------------------
            // 2) 找出第一筆符合「可處理」的任務
            //
            //   條件：
            //   - ZIP 必須 ACCEPTED（否則還在 ZIP 流程或被拒）
            //   - amr_required = true（若未要求 AMR，直接忽略）
            //   - amr_state 必須是 PENDING 或 SENT（SENT 代表已送出，可能需要 timeout 重送）
            //   - Gate 冷卻：若上次 gate check 未滿 5~10 秒則跳過
            // ------------------------------------------------------------
            RobotR007Task task = null;
            for (RobotR007Task t : candidates) {
                if (t == null) return;

                // ZIP 必須 ACCEPTED
                if (!isZipAccepted(t)) return;

                // 不需要 AMR → 不處理（視為可由其他流程結案）
                if (t.getAmrRequired() != null && !t.getAmrRequired()) return;

                // 只處理 PENDING / SENT
                String amrState = nvl(t.getAmrState(), "");
                if (!"PENDING".equalsIgnoreCase(amrState) && !"SENT".equalsIgnoreCase(amrState)) continue;

                // stkPort 必須存在（ZIP accepted 後理論上一定有）
                String stkPort = toText(t.getStkPort());
                if (stkPort == null) return;

                // Gate 冷卻：5~10 秒（依 task.id 做 deterministic jitter）
                int waitSec = calcGateWaitSeconds(t.getId());
                if (!isGateCooldownElapsed(t.getAmrLastAttemptTime(), waitSec)) {
                    return;
                }

                task = t;
                break;
            }

            //if (task == null) return;

            // ------------------------------------------------------------
            // 3) 取任務關鍵欄位
            // ------------------------------------------------------------
            Long logId = task.getLogId();
            String carrierId = toText(task.getCarrierId());
            String stkPort = toText(task.getStkPort()); // "STK01" or "STK02"

            if (logId == null || carrierId == null || stkPort == null) return;

            // ------------------------------------------------------------
            // 4) 若任務已收到 final ACK（OK/START/END/FAIL/CANCEL）
            //    這裡用 amr_state 判斷即可（由 ACK handler 寫入）
            // ------------------------------------------------------------
            if (hasAnyAmrAck(task)) {
                // AMR 已終結，本 worker 不再處理
                // （內部狀態 internal_state 可由 ACK handler 或別的 worker 來收斂）
                return;
            }

            // ------------------------------------------------------------
            // 5) Gate：查 Inventory（ZIPA）
            // ------------------------------------------------------------
            // Gate 規則（你最新需求）：
            // - ZIP 接單成功後，不一定立刻送 SEEC；必須先看 AMR 位置是否有產品
            // - 且只針對 STKxx-Buffer/Car/FP/OP/AMR 這些位置「有產品」的 carrier 發送
            //
            // 以單一任務的角度，意思是：
            //   A) STKxx-AMR 有產品（gate 開）
            //   B) 本 task.carrier_id 必須出現在上述 5 個點位其中之一（否則不發）
            //
            Root<StatusQuerySecondaryBody> inv = zipService.queryInventory(ZipTarget.ZIPA);
            Map<String, String> locCarrier = inventoryToLocCarrierMap(inv);

            boolean gateOpen = isStkAmrHasProduct(locCarrier, stkPort);
            boolean carrierInGroup = isCarrierInStkGroup(locCarrier, stkPort, carrierId);

            if (!gateOpen || !carrierInGroup) {
                // --------------------------------------------------------
                // Gate 不成立：冷卻 5~10 秒再看
                // - 不要增加 amr_attempts
                // - 不要把 amr_state 改成 SENT
                // - 只更新 amr_last_attempt_time 作為「上次 gate check」時間
                // - 可順便寫一個提示訊息到 amr_result_message（可選）
                // --------------------------------------------------------
                String msg = (!gateOpen)
                        ? ("WAIT_GATE: " + stkPort + "-AMR empty")
                        : ("WAIT_GATE: carrier not in " + stkPort + " group");

                tryTouchAmrGateCheck(logId, msg);

                log.info("[R007-AMR] Gate 不成立 → 冷卻再看；logId={}, carrier={}, stkPort={}, gateOpen={}, carrierInGroup={}",
                        logId, carrierId, stkPort, gateOpen, carrierInGroup);
                return;
            }

            // ------------------------------------------------------------
            // 6) Gate 成立後：判斷要「首次送」或「重送」
            // ------------------------------------------------------------
            int attempts = (task.getAmrAttempts() == null ? 0 : task.getAmrAttempts());
            boolean alreadySent = "SENT".equalsIgnoreCase(task.getAmrState());
            boolean timeoutElapsed = isTimeout(task.getAmrLastAttemptTime(), amrTimeoutSeconds);
            boolean canRetry = attempts < amrMaxAttempts;

            // 決定這次要用的 AMR TID：
            // - 第一次送：沿用 inbound tid（task.tid）
            // - 重送：新 tid（避免 SEEC 端用 tid 去重而忽略）
            final String amrTidToUse = (!alreadySent)
                    ? task.getTid()
                    : BaseMqttHandlerUtils.generateUniqueTid();

            if (!alreadySent || (timeoutElapsed && canRetry)) {

                // --------------------------------------------------------
                // 6-1) 取得 inbound 模板（RobotInR007）
                // --------------------------------------------------------
                // 你原本 buildForward 會用 RobotInR007 補值；AMR worker 沒有 inbox，所以從 repo 取即可。
                Optional<RobotInR007> mOpt = r007Repo.findById(logId);
                if (mOpt.isEmpty()) {
                    // 這種狀況理論上不該發生（ZIP worker 已經要求 RobotInR007 存在）
                    // 但為了安全：標 FAILED，避免一直掃到它
                    String reason = "robot_in_r007 not found for AMR forward, logId=" + logId;
                    try {
                        RobotR007Task patch = new RobotR007Task();
                        patch.setLogId(logId);
                        patch.setInternalState("FAILED");
                        patch.setFailReason(reason);
                        patch.setUpdatedTime(LocalDateTime.now());
                        taskRepo.updateByLogId(patch);
                    } catch (Exception ignore) {
                    }
                    log.error("[R007-AMR] {} → 任務標 FAILED", reason);
                    return;
                }
                RobotInR007 m = mOpt.get();

                // --------------------------------------------------------
                // 6-2) 建立 forward payload（同你原本邏輯：以 task 為主，補齊欄位，覆蓋 stkPort）
                // --------------------------------------------------------
                R007CommandPayload forward = buildForwardR007FromTask(task, amrTidToUse, m, stkPort);

                // --------------------------------------------------------
                // 6-3) 記錄 log，再 publish SEEC
                // --------------------------------------------------------
                Long fwdLogId = logService.recordReturningId(
                        "cmd/r007/forward",
                        workerId, "seec",
                        objectMapper.valueToTree(forward),
                        MqttMessageType.COMMAND
                );

                publisher.publish("seec",
                        objectMapper.writeValueAsString(forward),
                        MqttMessageType.COMMAND,
                        forward.getTid(),
                        forward.getCmdId());

                // --------------------------------------------------------
                // 6-4) 更新 task：amr_state=SENT、amr_tid、attempts++、last_attempt_time...
                // --------------------------------------------------------
                taskRepo.amrMarkSent(
                        logId,
                        forward.getTid(),
                        fwdLogId,
                        safeToJson(forward.getMessage())
                );

                // --------------------------------------------------------
                // 6-5) 回 ACK START 給來源系統？
                //
                // 你原本是在「第一次送 SEEC」時回 START ACK 給 inbox.sender。
                // 但現在 ZIP worker 已經把 inbox DONE，AMR worker 沒有 inbox.sender。
                //
                // 結論：
                // - 如果你仍需要回 START ACK 給來源系統，
                //   你必須從 task 或 log 中取 sender。
                // - 目前 RobotR007Task 表沒有 sender 欄位，這裡「不回 ACK START」是最安全的最小改動。
                //
                // 如果你真的需要回 START：
                // 目前未查 sender 來源，維持既有 START 轉傳流程。
                // --------------------------------------------------------

                log.info("[R007-AMR] 發送/重送 SEEC：logId={}, carrier={}, stkPort={}, amrTid={}, attempts={}",
                        logId, carrierId, stkPort, forward.getTid(), attempts + 1);
                return;
            }

            // ------------------------------------------------------------
            // 7) 已 SENT 未逾時 / 或已達重送上限 → 不做事
            // ------------------------------------------------------------
            if (alreadySent && !timeoutElapsed) {
                // 還在等待 ACK
                return;
            }

            if (alreadySent && timeoutElapsed && !canRetry) {
                // 已達重送上限：標記 FAILED（可選），避免無限循環
                String reason = "AMR timeout and max attempts reached";
                try {
                    RobotR007Task patch = new RobotR007Task();
                    patch.setLogId(logId);
                    patch.setInternalState("FAILED");
                    patch.setFailReason(reason);
                    patch.setUpdatedTime(LocalDateTime.now());
                    taskRepo.updateByLogId(patch);
                } catch (Exception ignore) {
                }
                log.warn("[R007-AMR] {}：logId={}, carrier={}, stkPort={}", reason, logId, carrierId, stkPort);
            }

        } catch (Exception e) {
            log.error("[R007-AMR] 例外：{}", e.getMessage() + Arrays.toString(e.getStackTrace()), e);
        }
    }

    // ================= ZIPA 檢查邏輯 =================

    /**
     * type=5（*）：是否存在任何 Status=62（有命令、執行中）
     */
    private boolean zipHasAnyExecutingOrder(ZipTarget target) {
        try {
            Root<StatusQuerySecondaryBody> resp = zipService.queryDispatchStatus(target);
            if (resp == null || resp.getBody() == null || resp.getBody().getStatusInfos() == null) return false;

            for (StatusQuerySecondaryBody.StatusInfo s : resp.getBody().getStatusInfos()) {
                if (s == null) continue;
                Integer type = toInt(s.getType());
                if (type == null || type != 5) continue;

                Integer st = toInt(s.getStatus());
                if (st != null && st == 62) {
                    String name = toText(s.getName());
                    log.info("[R007] ZIPA 發現執行中任務（type=5, name={}, status=62）", name);
                    return true;
                }
            }
        } catch (Exception e) {
            log.warn("[R007] 查 ZIP type=5 失敗：{}", e.getMessage());
        }
        return false;
    }

    /**
     * type=2：取得指定 carrierId 的狀態碼（找不到回 null）
     */
    private Integer queryType2Status(ZipTarget target, String carrierId) {
        try {
            StatusQueryPrimaryBody.QueryInfo qi = new StatusQueryPrimaryBody.QueryInfo();
            qi.setType(2);
            qi.setName(carrierId);

            Root<StatusQuerySecondaryBody> resp = zipService.sendStatusQuery(target, qi);
            if (resp == null || resp.getBody() == null || resp.getBody().getStatusInfos() == null) return null;

            for (StatusQuerySecondaryBody.StatusInfo s : resp.getBody().getStatusInfos()) {
                if (s == null) continue;
                Integer type = toInt(s.getType());
                if (type == null || type != 2) continue;

                String name = toText(s.getName());
                if (name != null && name.equalsIgnoreCase(carrierId)) {
                    return toInt(s.getStatus()); // 31/32/33/34/35/38/39
                }
            }
        } catch (Exception e) {
            log.warn("[R007] 查 ZIP type=2 失敗：{}", e.getMessage());
        }
        return null;
    }

    /**
     * 從一次性的 type=6 inventory 回覆挑空的 OP 口（Message[1] = CarrierID；null/空字串/"null" = 空位）
     */
    private String pickEmptyOpPortFromInventory(Root<StatusQuerySecondaryBody> inv) {
        if (inv == null || inv.getBody() == null || inv.getBody().getStatusInfos() == null) return null;

        String op1Carrier = getCarrierIdFromInventory(inv, "STK01-OP");
        String op2Carrier = getCarrierIdFromInventory(inv, "STK02-OP");

        boolean op1Empty = isEmptyCarrier(op1Carrier);
        boolean op2Empty = isEmptyCarrier(op2Carrier);

        if (op1Empty) return "STK01";
        if (op2Empty) return "STK02";
        return null;
    }

    /**
     * 把 ZIP inventory（type=6）轉成 Map：key=站點名（STK01-OP...） value=CarrierId（null 表示空）
     * <p>
     * 注意：
     * - Message[1] = CarrierID；字串 "null" 視為空
     * - 不回傳 "NOTFOUND/MSG_ERROR" 這種字串，避免污染空判斷
     */
    private Map<String, String> inventoryToLocCarrierMap(Root<StatusQuerySecondaryBody> inv) {
        Map<String, String> map = new HashMap<>();
        if (inv == null || inv.getBody() == null || inv.getBody().getStatusInfos() == null) return map;

        for (StatusQuerySecondaryBody.StatusInfo s : inv.getBody().getStatusInfos()) {
            if (s == null) continue;

            Integer type = toInt(s.getType());
            if (type == null || type != 6) continue;

            String name = toText(s.getName());
            if (name == null) continue;

            List<?> msg = s.getMessage();
            String carrier = null;
            if (msg != null && msg.size() >= 2) {
                carrier = toText(msg.get(1));
                if (carrier != null && "null".equalsIgnoreCase(carrier)) carrier = null;
            }

            map.put(name, carrier);
        }
        return map;
    }

    /**
     * 從 type=6 inventory 找到指定 Name 的 Message[1]（CarrierID）；找不到回 null
     */
    private String getCarrierIdFromInventory(Root<StatusQuerySecondaryBody> inv, String nameWanted) {
        for (StatusQuerySecondaryBody.StatusInfo s : inv.getBody().getStatusInfos()) {
            if (s == null) continue;
            Integer type = toInt(s.getType());
            if (type == null || type != 6) continue;

            String name = toText(s.getName());
            // 範例是 "ZIPA_STK01-OP"；若實際沒有 "ZIPA_" 前綴，把比對改成 equalsIgnoreCase(nameWanted)
            if (name == null || !name.equalsIgnoreCase(nameWanted)) continue;

            List<?> msg = s.getMessage(); // Message[0]=Barcode, Message[1]=CarrierID
            if (msg == null || msg.size() < 2) return "MSG_ERROR";

            String carrier = toText(msg.get(1));
            if ("null".equalsIgnoreCase(carrier)) return null; // 字串 "null" 視為空
            return carrier;
        }
        return "NOTFOUND；" + nameWanted;
    }

    /**
     * Gate #1：STKxx-AMR 是否有產品（只要有任一 carrier 即算 gate 開）
     */
    private boolean isStkAmrHasProduct(Map<String, String> locCarrier, String stkPort) {
        String amrCarrier = locCarrier.get(stkPort + "-AMR");
        if (isEmptyCarrier(amrCarrier))
            amrCarrier = locCarrier.get(stkPort + "-OP");
        if (isEmptyCarrier(amrCarrier))
            amrCarrier = locCarrier.get(stkPort + "-CCD");
        return !isEmptyCarrier(amrCarrier);
    }

    /**
     * Gate #2：本任務 carrier 是否位於 STKxx-Buffer/Car/FP/OP/AMR 其中之一
     * <p>
     * 這是你「只針對這些有產品的發」的單任務版本：
     * - 只有當該任務的 carrier 出現在這些點位之一，才會送 SEEC
     */
    private boolean isCarrierInStkGroup(Map<String, String> locCarrier, String stkPort, String carrierId) {
        if (carrierId == null || carrierId.isBlank()) return false;

        for (String suf : STK_GROUP_SUFFIXES) {
            String c = locCarrier.get(stkPort + "-" + suf);
            if (c != null && c.equalsIgnoreCase(carrierId)) return true;
        }
        return false;
    }

    /**
     * Gate 冷卻秒數（5~10 秒）：
     * - 為了避免所有任務同時醒來一直打 inventory
     * - 以 task.id 做 deterministic jitter（不需要存下一次時間）
     */
    private int calcGateWaitSeconds(Long taskId) {
        int min = Math.max(1, amrGateMinWaitSeconds);
        int max = Math.max(min, amrGateMaxWaitSeconds);
        int range = (max - min + 1); // 例如 5~10 -> range=6
        if (taskId == null) return min;
        return min + (int) (Math.abs(taskId) % range);
    }

    /**
     * 冷卻時間是否已到：
     * - last == null → 視為可立刻檢查
     * - last + waitSec <= now → 可檢查
     */
    private boolean isGateCooldownElapsed(LocalDateTime last, int waitSec) {
        if (last == null) return true;
        return last.plusSeconds(waitSec).isBefore(LocalDateTime.now());
    }

    /**
     * Gate 不成立時 touch：只更新 amr_last_attempt_time / amr_result_message
     * <p>
     * 重要：
     * - 不增加 attempts
     * - 不把 amr_state 改 SENT
     * <p>
     * 這樣就能用「現有欄位」達成 5~10 秒冷卻，且不用動表。
     */
    private void tryTouchAmrGateCheck(Long logId, String msg) {
        try {
            RobotR007Task patch = new RobotR007Task();
            patch.setLogId(logId);
            patch.setAmrLastAttemptTime(LocalDateTime.now());
            patch.setAmrResultMessage(msg);
            patch.setUpdatedTime(LocalDateTime.now());
            taskRepo.updateByLogId(patch);
        } catch (Exception e) {
            log.warn("[R007-AMR] touch gate check 失敗：logId={}, err={}", logId, e.getMessage());
        }
    }

    private static boolean isEmptyCarrier(String carrierId) {
        return carrierId == null || carrierId.isBlank();
    }

    // ================= Task 更新 =================

    /**
     * ZIP 接單成功後：寫入 STK_PORT、把 task 標記 PROCESSING
     */
    private void upsertTaskAssignZIP(MqttInbox inbox, RobotInR007 m, String stkPort) {
        try {
            RobotR007Task task = taskRepo.findByLogId(inbox.getLogId())
                    .orElseGet(RobotR007Task::new);

            task.setLogId(inbox.getLogId());
            task.setInboxId(inbox.getId());
            task.setTid(inbox.getTid());

            // 基本資料備齊（若之前 handler 已寫入，這裡覆蓋也一致）
            task.setLotId(m.getLotId());
            task.setCarrierId(m.getCarrierId());
            task.setWipName(m.getWipName());
            task.setDestLoc(m.getDestLoc());
            task.setEqpPort(m.getEqpPort());
            task.setDeviceName(m.getDeviceName());

            // ZIP 決策出的出料 Port
            task.setStkPort(stkPort);

            // 狀態更新：PROCESSING
            task.setInternalState("PROCESSING");
            task.setUpdatedTime(LocalDateTime.now());
            if (task.getCreatedTime() == null) {
                task.setCreatedTime(LocalDateTime.now());
            }

            if (task.getId() == null) taskRepo.save(task);
            else taskRepo.update(task);

        } catch (Exception e) {
            log.warn("[R007][TASK] upsert ASSIGN 失敗：logId={}, err={}", inbox.getLogId(), e.getMessage(), e);
        }
    }

    // ================= AMR 相關邏輯 =================

    /**
     * 用 robot_r007_task 為主來源組 R007 forward：
     * - 若 task.rawMessageJson 存在：以它反序列化為模板，覆蓋 tid/idDesc/result/… 與各關鍵 MESSAGE 欄位，再補 STK_PORT
     * - 若不存在：用 task（必要時以 m 補值）組出最低保證欄位
     */
    private R007CommandPayload buildForwardR007FromTask(RobotR007Task task,
                                                        String amrTid,
                                                        RobotInR007 m,
                                                        String stkPort) throws Exception {
        // 嘗試從 task.rawMessageJson 當作模板
        R007CommandPayload forward = null;
        if (task.getRawMessageJson() != null && !task.getRawMessageJson().isBlank()) {
            try {
                // rawMessageJson 是 MESSAGE 區塊；這裡包成完整 payload 再反序列化，或直接組 Message 也可以
                R007CommandPayload.Message tmplMsg =
                        objectMapper.readValue(task.getRawMessageJson(), R007CommandPayload.Message.class);

                forward = new R007CommandPayload();
                forward.setMessage(tmplMsg);
            } catch (Exception ignore) {
                // 解析失敗就走 fallback
            }
        }

        if (forward == null) {
            forward = new R007CommandPayload();
            forward.setMessage(new R007CommandPayload.Message());
        }

        // ---- 共通頭 ----
        forward.setCmd("ROBOT");
        forward.setCmdId("R007");
        forward.setTid(amrTid);
        forward.setIdDesc("ROBOT_MOVE_SCH_TO_EQP");
        forward.setResult("");         // 轉發時清空
        forward.setResultMessage("");

        // ---- MESSAGE 覆蓋關鍵欄位（以 task 為主，必要時用 m 補）----
        R007CommandPayload.Message mm = forward.getMessage();
        if (mm == null) mm = new R007CommandPayload.Message();

        mm.setLotId(nvl(task.getLotId(), m.getLotId()));
        mm.setCarrierId(nvl(task.getCarrierId(), m.getCarrierId()));
        mm.setWipName(nvl(task.getWipName(), m.getWipName()));
        mm.setDestLoc(nvl(task.getDestLoc(), m.getDestLoc()));
        mm.setEqpPort(nvl(task.getEqpPort(), m.getEqpPort()));
        mm.setDeviceName(nvl(task.getDeviceName(), m.getDeviceName()));

        // 其他非必填欄位（若 task 有就帶過去，避免丟失上下游擴充欄位）
        mm.setTrayHigh(task.getTrayHigh());
        mm.setTrayType(task.getTrayType());
        mm.setTrayNum(task.getTrayNum());
        mm.setMovePriority(task.getMovePriority());
        mm.setMissionTrip(task.getMissionTrip());
        mm.setOdo(task.getOdo());
        mm.setAmrSpeed(task.getAmrSpeed());
        mm.setAmrRobotSpeed(task.getAmrRobotSpeed());
        mm.setPpkgBodySize(task.getPpkgBodySize());
        mm.setFlip(task.getFlip());

        // 這次 ZIP 決策 / dest_loc 綁定後的 stkPort 一定要覆蓋
        mm.setStkPort(stkPort);

        forward.setMessage(mm);
        return forward;
    }

    // ================= ACK =================

    private void sendAckStart(String targetSystem, String tid, RobotInR007 m) throws Exception {
        sendAck(targetSystem, tid, m, "START", "");
    }

    private void sendAck(String targetSystem, String tid, RobotInR007 m, String result, String resultMessage) throws Exception {
        R007AckPayload ack = new R007AckPayload();
        ack.setCmd("ROBOT");
        ack.setCmdId("R007");
        ack.setTid(tid);
        ack.setIdDesc("ROBOT_MOVE_SCH_TO_EQP");

        R007AckPayload.Message msg = new R007AckPayload.Message();
        msg.setLotId(m.getLotId());
        msg.setCarrierId(m.getCarrierId());
        msg.setWipName(m.getWipName());
        msg.setDestLoc(m.getDestLoc());
        msg.setEqpPort(m.getEqpPort());
        msg.setDeviceName(m.getDeviceName());
        ack.setMessage(msg);

        ack.setResult(result);
        ack.setResultMessage(resultMessage == null ? "" : resultMessage);

        // 記 log，再發 MQTT
        logService.recordReturningId(
                "ack/r007", workerId, targetSystem,
                objectMapper.valueToTree(ack),
                MqttMessageType.ACK
        );

        publisher.publish(targetSystem,
                objectMapper.writeValueAsString(ack),
                MqttMessageType.ACK,
                ack.getTid(),
                ack.getCmdId());

        //log.debug("[R007][ACK] sent: result={}, tid={}, target={}", result, tid, targetSystem);
    }

    // ================= 安全轉換 =================

    private static String toText(Object o) {
        if (o == null) return null;
        String s = o.toString();
        if (s == null) return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    private static Integer toInt(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(o.toString().trim());
        } catch (Exception ignore) {
            return null;
        }
    }

    private static <T> T nvl(T a, T b) {
        return (a != null ? a : b);
    }

    // ================= 小工具 =================

    private boolean isTimeout(LocalDateTime lastAttempt, int timeoutSec) {
        if (lastAttempt == null) return true; // 沒送過視為應該送
        return lastAttempt.plusSeconds(timeoutSec).isBefore(LocalDateTime.now());
    }

    private boolean hasAnyAmrAck(RobotR007Task t) {
        String s = t.getAmrState();
        if (s == null) return false;
        return "OK".equalsIgnoreCase(s)
                || "START".equalsIgnoreCase(s)
                || "END".equalsIgnoreCase(s)
                || "FAIL".equalsIgnoreCase(s)
                || "CANCEL".equalsIgnoreCase(s);
    }

    /**
     * ZIP 是否已 ACCEPTED
     */
    private boolean isZipAccepted(RobotR007Task t) {
        String s = t.getZipState();
        if (s != null && "ACCEPTED".equalsIgnoreCase(s)) return true;
        return t.getZipAcceptTime() != null;
    }

    private String safeToJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return null;
        }
    }

}
