package com.czkuo.rdf88701.application.service.zip;

import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.mqtt.util.BaseMqttHandlerUtils;
import com.czkuo.rdf88701.application.service.camera.HikCameraSnapService;
import com.czkuo.rdf88701.application.service.mqtt.MqttCommandService;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.application.service.r029.R029OutputCaptureService;
import com.czkuo.rdf88701.common.dto.MqttSendResult;
import com.czkuo.rdf88701.common.dto.mqtt.ack.L005AckPayload;
import com.czkuo.rdf88701.common.dto.mqtt.ack.R007AckPayload;
import com.czkuo.rdf88701.common.dto.mqtt.ack.R031AckPayload;
import com.czkuo.rdf88701.common.dto.mqtt.ack.S010AckPayload;
import com.czkuo.rdf88701.common.dto.mqtt.command.S072CommandPayload;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.domain.dto.zip.CCDPlatformInput.CCDPlatformInputPrimaryBody;
import com.czkuo.rdf88701.domain.dto.zip.CCDPlatformInput.CCDPlatformInputSecondaryBody;
import com.czkuo.rdf88701.domain.dto.zip.CardReader.CardReaderPrimaryBody;
import com.czkuo.rdf88701.domain.dto.zip.CardReader.CardReaderSecondaryBody;
import com.czkuo.rdf88701.domain.dto.zip.CarrierFlip.CarrierFlipPrimaryBody;
import com.czkuo.rdf88701.domain.dto.zip.CarrierFlip.CarrierFlipSecondaryBody;
import com.czkuo.rdf88701.domain.dto.zip.StatusReport.StatusReportPrimaryBody;
import com.czkuo.rdf88701.domain.dto.zip.StatusReport.StatusReportSecondaryBody;
import com.czkuo.rdf88701.domain.dto.zip.StockerInput.StockerInputPrimaryBody;
import com.czkuo.rdf88701.domain.dto.zip.StockerInput.StockerInputSecondaryBody;
import com.czkuo.rdf88701.domain.dto.zip.StockerOutput.StockerOutputPrimaryBody;
import com.czkuo.rdf88701.domain.dto.zip.StockerOutput.StockerOutputSecondaryBody;
import com.czkuo.rdf88701.domain.dto.zip.common.Header;
import com.czkuo.rdf88701.domain.repository.*;
import com.czkuo.rdf88701.infra.entity.*;
import com.czkuo.rdf88701.presentation.web.controller.ZipStockerApiController;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ZipStockerEventService
 * ------------------------------------------------------------
 * MCS 端處理來自 ZIP Stocker 的事件（Primary → Secondary）。
 * 本 Service 被 {@link ZipStockerApiController} 呼叫，負責：
 * - 接收 ZIP 的 Primary 資料
 * - 觸發對應業務流程（例如發送 MQTT 命令、寫資料庫、狀態更新）
 * - 回覆 Secondary 給 ZIP
 * <p>
 * 已實作事件：
 * 1) 入庫上報   onStockerInput
 * 2) 出庫上報   onStockerOutput
 * 3) 狀態回報   onStatusReport
 * 4) 讀卡事件   onCardReader（本版已將卡號正規化為十進位再送 S010）
 * <p>
 * 設定說明（application.yml 可覆寫）：
 * zip:
 * mqtt:
 * target-system: ase                  # 目標外部系統（預設 ase）
 * l005-ack-timeout-ms: 5000          # 等 L005 ACK 逾時（毫秒）
 * l005-ack-poll-interval-ms: 100     # 等 L005 ACK 輪詢間隔（毫秒）
 * card:
 * reverse: false                     # 是否對 hex 做兩兩倒序（小端→大端）
 * uppercase: true                    # 是否先轉大寫再處理
 * always-hex: false                  # 是否無條件當 hex 解析（跳過偵測）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ZipStockerEventService {

    // ===== 相依服務 =====
    private final ContainerMainRepository containerMainRepository;
    private final ContainerAttrRepository containerAttrRepository;
    private final LocationTrackingRepository locationTrackingRepository;
    private final MqttCommandService mqttCommandService;
    private final MqttMessageEventPublisher publisher;
    private final MqttMessageLogRepository mqttMessageLogRepository;
    private final ObjectMapper objectMapper;
    private final HmiDisplayTaskRepository repo;
    private final L005SessionRepository l005SessionRepository;

    private final S072SessionRepository s072SessionRepository;
    private final HikCameraSnapService camera;

    private final RobotR007TaskRepository r007TaskRepository;

    // === 為了回 R029 END ===
    private final R029OutputCaptureService r029OutputCaptureService;

    // === 為了回 R031 END ===
    private final RobotR031TaskRepository r031TaskRepository;
    private final MqttMessageEventPublisher eventPublisher;
    private final MqttMessageLogService logService;

    // ===== 外部設定（MQTT 相關）=====
    /**
     * 目標系統（例如 ase / seec），預設 ase
     */
    @Value("${zip.mqtt.target-system:ase}")
    private String targetSystem;

    /**
     * 等 L005/S010 ACK 的逾時（毫秒）
     */
    @Value("${zip.mqtt.l005-ack-timeout-ms:30000}")
    private long ackTimeoutMs;

    /**
     * 等 L005/S010 ACK 的輪詢間隔（毫秒）
     */
    @Value("${zip.mqtt.l005-ack-poll-interval-ms:100}")
    private long ackPollIntervalMs;

    // ===== 讀卡正規化設定 =====
    /**
     * 是否對 hex 字串做兩兩倒序（例如 "12345678" → "78563412"），常見於小端序卡機
     */
    @Value("${zip.card.reverse:false}")
    private boolean cardReverse;

    /**
     * 是否先將 hex 轉為大寫再處理
     */
    @Value("${zip.card.uppercase:true}")
    private boolean cardUppercase;

    /**
     * 若為 true，無條件當成 hex 解析（省略 A-F 偵測）
     */
    @Value("${zip.card.always-hex:false}")
    private boolean cardAlwaysHex;

    @Value("${camera.jpg-quality:85}")
    private int cameraJpgQuality;

    // ===== 間隙檢設定 =====
    @Value("${camera.save-root:/data/snaps}")
    private String cameraSaveRoot;

    // ===== 間隙檢切換：device / file =====
    @Value("${camera.mode:device}")          // device=用相機；file=用檔案
    private String cameraMode;

    @Value("${camera.mock.base-dir:/data/mock_snaps}")       // file 模式時，放假圖的資料夾
    private String cameraMockBaseDir;

    @Value("${app.external.ase-system:ase}")
    private String aseSystem;

    // ===== 翻轉設定 =====
    /**
     * 若為 true，無條件一定要翻轉
     */
    @Value("${zip.flip.test:false}")
    private boolean testFlip;

    // ===== 正規化/解析用的 Pattern =====
    private static final Pattern NON_HEX = Pattern.compile("[^0-9A-Fa-f]"); // 非十六進位字元
    private static final Pattern DIGITS = Pattern.compile("\\d+");         // 純數字（十進位 fallback）
    private static final Pattern HEX_ALPHA = Pattern.compile("[A-Fa-f]");     // 是否含 A-F（判斷像 hex）

    // =====================================================================
    // 1) 入庫上報
    // =====================================================================

    /**
     * 處理 ZIP → MCS 入庫上報事件（嚴格版）。
     * 規則：
     * - 僅使用對方傳入的 barcode / carried，**不自動生成**。
     * - 若 barcode 缺失或空白 → 直接回覆失敗（result=1, resultMessage=BARCODE_REQUIRED），且**不發 L005**。
     * - 若 barcode 存在 → 發 L005 並等待 ACK，依 ACK 結果回填 Secondary。
     */
    public StockerInputSecondaryBody onStockerInput(Header header, StockerInputPrimaryBody body) {
        log.info("[MCS] 收到入庫上報：{}", body);

        // 取得 Sender
        final String sender = (header != null && header.getSender() != null)
                ? header.getSender().trim()
                : "";

        // 取得對方原始資料（不做自動生成）
        final String inBarcode = (body != null && body.getMessage() != null)
                ? body.getMessage().getBarcode()
                : null;
        final String inCarried = (body != null && body.getMessage() != null)
                ? body.getMessage().getCarried()
                : null;

        // ===== 預設回覆骨架（先放入對方原樣，方便稽核）=====
        StockerInputSecondaryBody resp = new StockerInputSecondaryBody();
        StockerInputSecondaryBody.Message msg = new StockerInputSecondaryBody.Message();
        msg.setBarcode(inBarcode);
        msg.setCarried(inCarried);

        StockerInputSecondaryBody.ResultInfo resultInfo = new StockerInputSecondaryBody.ResultInfo();

        // ============================================================
        // 分流：FSK-7004A → 從 DB 補值後直接 PASS（不發 L005）
        // ============================================================
        if ("FSK-7004A".equalsIgnoreCase(sender)) {
            try {
                InputResolvedFields f = resolveInputFromDb(inBarcode, inCarried);

                // 只有 DB 有值才覆蓋，避免把對方原樣洗掉
                if (notBlank(f.barcode)) msg.setBarcode(f.barcode);
                if (notBlank(f.carried)) msg.setCarried(f.carried);
                if (notBlank(f.lotId)) msg.setLotId(f.lotId);
                if (notBlank(f.trayHigh)) msg.setTrayHigh(f.trayHigh);
                if (notBlank(f.trayType)) msg.setTrayType(f.trayType);
                if (notBlank(f.messageType)) msg.setMessageType(f.messageType);

                resultInfo.setResult(0);
                resultInfo.setResultMessage("PASS");
                log.info("[MCS] 入庫上報 sender={} 走 DB 補值：{}", sender, f);

                // 入庫詢問成功（回 PASS）→ 標記 R029 載具狀態為 INQUIRY
                try {
                    // 取可用的 CarrierID：優先用回覆要帶回去的 msg.carried，其次 inCarried，再次 f.carried
                    String carrierForMark =
                            (msg.getCarried() != null && !msg.getCarried().isBlank()) ? msg.getCarried().trim()
                                    : (inCarried != null && !inCarried.isBlank()) ? inCarried.trim()
                                    : (f.carried != null && !f.carried.isBlank()) ? f.carried.trim()
                                    : null;

                    if (carrierForMark != null && !carrierForMark.isBlank()) {
                        r029OutputCaptureService.markInquiryByCarrierId(carrierForMark);
                        log.info("[MCS] ZIPB 入庫詢問 → 已標記 R029: INQUIRY, carrierId={}", carrierForMark);

                        if (notBlank(f.binType)) {
                            resultInfo.setResult(1);
                            resultInfo.setResultMessage("BIN_TYPE is B(BAD).");
                            log.info("[MCS] ZIPB BIN_TYPE is B(BAD), carried={}", inBarcode);
                        } else if (isInspectDeltaNonZero(f.inspectPieces)) {
                            resultInfo.setResult(1);
                            resultInfo.setResultMessage("Inspection pieces delta detected: " + f.inspectPieces);
                            log.info("[MCS] ZIPB inspect pieces isn't zero, carried={}, INSPECT_PIECES_DELTA={}", inBarcode, f.inspectPieces);
                        }

                    } else {
                        resultInfo.setResult(1);
                        resultInfo.setResultMessage("can't find correct carrierId");
                        log.warn("[MCS] ZIPB 入庫詢問 → 無法標記 R029: INQUIRY（carrierId 皆為空，barcode={}）", inBarcode);
                    }
                } catch (Exception markEx) {
                    log.warn("[MCS] ZIPB 入庫詢問 → 標記 R029: INQUIRY 失敗：barcode={}, carried={}, err={}",
                            inBarcode, inCarried, markEx.getMessage(), markEx);
                }
            } catch (Exception ex) {
                // 即使補值失敗，回傳 FAIL
                log.warn("[MCS] FSK-7004A DB 補值失敗，使用對方原樣回覆 FAIL：barcode={}, carried={}, err={}",
                        inBarcode, inCarried, ex.getMessage(), ex);
                resultInfo.setResult(1);
                resultInfo.setResultMessage("FAIL");
            }

            resp.setMessage(msg);
            resp.setResultInfo(resultInfo);
            return resp;
        }

        // ============================================================
        // ZIPA（FSK-7003A）→（需 carried，回流 ZIPA）
        // ============================================================
        if (inCarried != null && !inCarried.trim().isEmpty()) {
            try {

                String realBarcode = inCarried;
                String realCarrierId = null;

                Optional<Long> cOpt = locationTrackingRepository.findContainerAtLocationName("Site#16");
                if (cOpt.isPresent()) {
                    Optional<ContainerMain> cmOpt = containerMainRepository.findById(cOpt.get());
                    if (cmOpt.isPresent()) {
                        ContainerMain cm = cmOpt.get();
                        if (realBarcode.equals(cm.getContainerCode())) {
                            realCarrierId = cm.getAliasCode();
                            log.warn("[MCS] Site#16 PC 有帳，進行自動補帳，CarrierId: {}", realCarrierId);
                        }
                    }
                }

                InputResolvedFields f = resolveInputFromDb(realBarcode, realCarrierId);

                // 只有 DB 有值才覆蓋，避免把對方原樣洗掉
                if (notBlank(f.barcode)) msg.setBarcode(f.barcode);
                if (notBlank(f.carried)) msg.setCarried(f.carried);
                if (notBlank(f.lotId)) msg.setLotId(f.lotId);
                if (notBlank(f.trayHigh)) msg.setTrayHigh(f.trayHigh);
                if (notBlank(f.trayType)) msg.setTrayType(f.trayType);
                if (notBlank(f.messageType)) msg.setMessageType(f.messageType);

                resultInfo.setResult(1);
                resultInfo.setResultMessage("FAIL");
                log.info("[MCS] 入庫上報 sender={} 走 DB 補值：{}", sender, f);
            } catch (Exception ex) {
                // 即使補值失敗，回傳 FAIL
                log.warn("[MCS] FSK-7003A DB 補值失敗，使用對方原樣回覆 FAIL：barcode={}, carried={}, err={}",
                        inBarcode, inCarried, ex.getMessage(), ex);
                resultInfo.setResult(1);
                resultInfo.setResultMessage("FAIL");
            }

            resp.setMessage(msg);
            resp.setResultInfo(resultInfo);
            return resp;
        }

        // ============================================================
        // ZIPA（FSK-7003A）→（需 barcode，發 L005、等 ACK）
        // ============================================================

        // 嚴格檢查：barcode 必填
        if (inBarcode == null || inBarcode.trim().isEmpty()) {
            // 直接拒絕、不發 L005
            resultInfo.setResult(1);
            resultInfo.setResultMessage("BARCODE_REQUIRED");
            resp.setMessage(msg);
            resp.setResultInfo(resultInfo);
            log.warn("[MCS] 入庫上報拒絕：sender={} 缺少 barcode（carried={}）", sender, inCarried);
            return resp;
        }

        final String barcode = inBarcode.trim();

        // ===== 0) 先建立/切換 Session（避免 ACK 比 DB 快）=====
        final String tid = BaseMqttHandlerUtils.generateTid();

        try {
            // 0-1) 先把同條碼現役會話失效（避免多條現役）
            l005SessionRepository.invalidateAllActiveByBarcode(barcode, tid);

            // 0-2) 建立新的現役 Session
            L005Session session = new L005Session();
            session.setBarcode(barcode);
            session.setTid(tid);
            session.setIsValid(true);
            session.setInternalState("INIT");
            session.setExternalLastResult(null);
            session.setCreatedAt(LocalDateTime.now());
            session.setUpdatedAt(LocalDateTime.now());
            l005SessionRepository.save(session);

        } catch (Exception se) {
            log.error("[MCS] 建立/切換 L005 Session 失敗：sender={}, barcode={}, tid={}, err={}",
                    sender, barcode, tid, se.getMessage(), se);
            resultInfo.setResult(1);
            resultInfo.setResultMessage("SESSION_OPEN_ERROR");
            resp.setMessage(msg);
            resp.setResultInfo(resultInfo);
            return resp;
        }

        // ===== 1) 發 L005（我帶 TID 或自動帶 TID）=====
        try {
            // 1-1) 發送入料詢問
            MqttSendResult l005 = mqttCommandService.sendL005WithTid(targetSystem, tid, barcode);
            log.info("[MCS] 已發 L005：sender={}, barcode={}, tid={}, dispatchResult={}", sender, barcode, tid, l005);

            // 1-2) 標記內部狀態：SENT（已發送、等待 ACK）
            l005SessionRepository.updateInternalStateByTid(tid, "SENT", null);

        } catch (Exception e) {
            log.error("[MCS] 發送 L005 失敗：sender={}, barcode={}, tid={}, err={}", sender, barcode, tid, e.getMessage(), e);

            // 回寫狀態：FAILED
            l005SessionRepository.updateInternalStateByTid(tid, "FAILED", "SEND_L005_ERROR");

            resultInfo.setResult(1);
            resultInfo.setResultMessage("SEND_L005_ERROR");
            resp.setMessage(msg);
            resp.setResultInfo(resultInfo);
            return resp;
        }

        // 預設：若沒拿到 ACK 就用 FAIL
        resultInfo.setResult(1);
        resultInfo.setResultMessage("FAIL");

        // ===== 2) 等 L005 Session 狀態 =====
        if (notBlank(tid)) {
            final long start = System.currentTimeMillis();
            boolean decided = false;
            boolean progressReplied = false; // 確保只回一次 NG 等

            while (System.currentTimeMillis() - start < ackTimeoutMs) {
                try {
                    Optional<L005Session> sOpt = l005SessionRepository.findByTid(tid);
                    if (sOpt.isPresent()) {
                        L005Session s = sOpt.get();
                        String st = s.getInternalState() == null ? "" : s.getInternalState().trim().toUpperCase();

                        // 回填 Secondary 欄位（僅有值才覆蓋）
                        if (notBlank(s.getPeerLotId())) msg.setLotId(s.getPeerLotId());
                        if (notBlank(s.getPeerCarrierId())) msg.setCarried(s.getPeerCarrierId());
                        if (notBlank(s.getPeerTrayHigh())) msg.setTrayHigh(s.getPeerTrayHigh());
                        if (notBlank(s.getPeerTrayType())) msg.setTrayType(s.getPeerTrayType());
                        if (notBlank(s.getPeerMsgType())) msg.setMessageType(s.getPeerMsgType());
                        msg.setBarcode(barcode);

                        if ("COMPLETED".equals(st)) {
                            // 我方判定成功 → 回 0/PASS（不另行回 ACK；對方已有 PASS ACK）
                            resultInfo.setResult(0);
                            resultInfo.setResultMessage(notBlank(s.getPeerResultMsg()) ? s.getPeerResultMsg() : "PASS");
                            decided = true;
                            log.info("[MCS] L005 Session 完成：tid={}, sender={}, barcode={}", tid, sender, barcode);
                            break;
                        } else if ("FAILED".equals(st)) {
                            // 我方判定失敗（例如對方 PASS 但我方驗證失敗）
                            String failMsg = notBlank(s.getFailReason())
                                    ? s.getFailReason()
                                    : (notBlank(s.getPeerResultMsg()) ? s.getPeerResultMsg() : "FAIL");

                            // 先回 Secondary 給 ZIP（FAIL）
                            resultInfo.setResult(1);
                            resultInfo.setResultMessage(failMsg);

                            // 再補回同 TID 的 L005「ACK 形式」→ RESULT=NG，帶完整 MESSAGE 欄位
                            if (!progressReplied) {
                                try {
                                    publishL005AckSameTid(
                                            tid,
                                            barcode,
                                            s.getPeerCarrierId(),
                                            s.getPeerLotId(),
                                            s.getPeerTrayHigh(),
                                            s.getPeerTrayType(),
                                            s.getPeerMsgType(),
                                            "NG",
                                            failMsg
                                    );
                                    progressReplied = true;
                                    log.info("[MCS] 已補送 L005 ACK（NG）：tid={}, barcode={}, msg={}", tid, barcode, failMsg);
                                } catch (Exception pubEx) {
                                    log.warn("[MCS] 補送 L005 ACK(NG) 失敗：tid={}, err={}", tid, pubEx.getMessage(), pubEx);
                                }
                            }

                            decided = true;
                            log.warn("[MCS] L005 Session 失敗：tid={}, reason={}", tid, failMsg);
                            break;
                        }
                    }

                    // —— 正常輪詢休眠 —— //
                    try {
                        Thread.sleep(Math.max(10L, ackPollIntervalMs));
                    } catch (InterruptedException ie) {
                        // 恢復中斷旗標並結束等待
                        Thread.currentThread().interrupt();
                        log.warn("[MCS] 等待 L005 Session 遭中斷：tid={}", tid);
                        break;
                    }
                } catch (Exception ex) {
                    log.warn("[MCS] 查詢 L005Session 例外（tid={}）：{}", tid, ex.getMessage(), ex);
                    try {
                        Thread.sleep(Math.max(10L, ackPollIntervalMs));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("[MCS] 等待 L005 Session（錯誤重試）遭中斷：tid={}", tid);
                        break;
                    }
                }
            }

            if (!decided) {
                // 逾時：標記 FAILED，Secondary 回 ACK_TIMEOUT，並補送 L005 ACK = NG/ACK_TIMEOUT（帶最後快照）
                try {
                    l005SessionRepository.updateInternalStateByTid(tid, "FAILED", "ACK_TIMEOUT");
                } catch (Exception ignore) { /* 忽略 DB 例外 */ }

                // 回 Secondary
                resultInfo.setResult(1);
                resultInfo.setResultMessage("ACK_TIMEOUT");

                // 取一份最新快照用於 ACK MESSAGE 欄位（可為空）
                String snapCarrierId = null, snapLotId = null, snapTrayHigh = null, snapTrayType = null, snapMsgType = null;
                try {
                    Optional<L005Session> snapOpt = l005SessionRepository.findByTid(tid);
                    if (snapOpt.isPresent()) {
                        L005Session s = snapOpt.get();
                        snapCarrierId = s.getPeerCarrierId();
                        snapLotId = s.getPeerLotId();
                        snapTrayHigh = s.getPeerTrayHigh();
                        snapTrayType = s.getPeerTrayType();
                        snapMsgType = s.getPeerMsgType();
                    }
                } catch (Exception ignore) {
                }

                // 補送同 TID 的 L005 ACK（NG/ACK_TIMEOUT）→ 帶完整欄位
                try {
                    publishL005AckSameTid(
                            tid,
                            barcode,
                            snapCarrierId,
                            snapLotId,
                            snapTrayHigh,
                            snapTrayType,
                            snapMsgType,
                            "NG",
                            "ACK_TIMEOUT"
                    );
                    log.info("[MCS] 逾時補送 L005 ACK（NG/ACK_TIMEOUT）：tid={}, barcode={}", tid, barcode);
                } catch (Exception pubEx) {
                    log.warn("[MCS] 逾時補送 L005 ACK 失敗：tid={}, err={}", tid, pubEx.getMessage(), pubEx);
                }

                log.warn("[MCS] 等待 L005 Session 結果逾時（tid={}，timeout={}ms，sender={}, barcode={}）",
                        tid, ackTimeoutMs, sender, barcode);
            }
        }

        // ===== 3) 組回覆 =====
        resp.setMessage(msg);
        resp.setResultInfo(resultInfo);
        return resp;
    }

    // =====================================================================
    // 2) 出庫上報（目前示意：固定 PASS）
    // =====================================================================

    public StockerOutputSecondaryBody onStockerOutput(StockerOutputPrimaryBody body) {
        log.info("[MCS] 收到出庫上報：{}", body);

        StockerOutputSecondaryBody resp = new StockerOutputSecondaryBody();

        StockerOutputSecondaryBody.ResultInfo resultInfo = new StockerOutputSecondaryBody.ResultInfo();
        resultInfo.setResult(0); // 0 表示成功
        resultInfo.setResultMessage("PASS");

        resp.setResultInfo(resultInfo);
        return resp;
    }

    // =====================================================================
    // 3) 狀態回報（僅確認每筆回 0/PASS）
    // =====================================================================

    public StatusReportSecondaryBody onStatusReport(Header header, StatusReportPrimaryBody body) {
        log.info("[MCS] 收到狀態回報：{}", body);

        // 取得 Sender
        final String sender = (header != null && header.getSender() != null)
                ? header.getSender().trim()
                : "";

        StatusReportSecondaryBody resp = new StatusReportSecondaryBody();
        List<StatusReportSecondaryBody.ResultInfo> results = new ArrayList<>();

        if (body == null || body.getStatusInfos() == null || body.getStatusInfos().isEmpty()) {
            StatusReportSecondaryBody.ResultInfo ok = new StatusReportSecondaryBody.ResultInfo();
            ok.setResult(0);
            ok.setResultMessage("");
            results.add(ok);
            resp.setResultInfos(results);
            return resp;
        }

        for (var si : body.getStatusInfos()) {
            // 預設 ACK 回覆：0/PASS
            StatusReportSecondaryBody.ResultInfo ok = new StatusReportSecondaryBody.ResultInfo();
            ok.setResult(0);
            ok.setResultMessage("");
            results.add(ok);

            try {
                int type = si.getType();
                int status = si.getStatus();
                String name = si.getName(); // Name = Barcode (備援) 限定 ZIPA
                List<String> msgList = si.getMessage();

                if (type == 2) {
                    // 解析 [0]=Barcode [1]=CarrierID [2]=Lot [3]=Qty [4]=當前位置
                    ParsedFields f = parseType2Fields(msgList);

                    // ---- ZIPA（FSK-7003A）行為 ----
                    if ("FSK-7003A".equalsIgnoreCase(sender)) {
                        if (status == 38) {
                            // 38：入倉輸送中 → 若有「已完成的 L005 會話」，則以同 TID 補送 ACK: START
                            try {
                                Optional<L005Session> sOpt = l005SessionRepository.findActiveByBarcode(f.barcode);
                                if (sOpt.isPresent()) {
                                    L005Session s = sOpt.get();
                                    String st = s.getInternalState() == null ? "" : s.getInternalState().trim().toUpperCase();
                                    String last = s.getExternalLastResult() == null ? "" : s.getExternalLastResult().trim().toUpperCase();

                                    if ("COMPLETED".equals(st) && !"START".equals(last)) {
                                        publishL005AckSameTid(
                                                s.getTid(),
                                                nvl(f.barcode),
                                                nvl(f.carrierId),
                                                nvl(f.lotId),
                                                nvl(s.getPeerTrayHigh()),    // 沒有就帶 session 快照或空字串
                                                nvl(s.getPeerTrayType()),
                                                nvl(s.getPeerMsgType()),
                                                "START",
                                                "ZIPA stock-in start"
                                        );
                                        l005SessionRepository.updateExternalResultByTid(s.getTid(), "START", "ZIPA stock-in start");
                                        log.info("[MCS] Type=2/38 → 以同 TID 補送 L005 ACK: START，barcode={}, tid={}", f.barcode, s.getTid());
                                    } else {
                                        //log.debug("[MCS] Type=2/38 略過補送 START：barcode={} (state={}, last={})", f.barcode, st, last);
                                    }
                                } else {
                                    //log.debug("[MCS] Type=2/38 找不到現役 L005Session：barcode={}", f.barcode);
                                }
                            } catch (Exception e) {
                                log.warn("[MCS] Type=2/38 處理 L005 START 例外：barcode={}, err={}", f.barcode, e.getMessage(), e);
                            }

                        } else if (status == 33) {
                            // 33：上架完成 → 發 S020-2001；若 L005 會話完成，補送 ACK: END 並更新外部狀態
                            mqttCommandService.sendS020_2001_StockIn(
                                    targetSystem,
                                    nvl(f.barcode),
                                    nvl(f.lotId),
                                    nvl(f.carrierId),
                                    "ZIPA",
                                    nvl(f.wipnameOut),
                                    nvl(f.qty)
                            );
                            log.info("[MCS] StatusReport Type=2/33 上架 → S020-2001：{}", f);

                            try {
                                Optional<L005Session> sOpt = l005SessionRepository.findActiveByBarcode(f.barcode);
                                if (sOpt.isPresent()) {
                                    L005Session s = sOpt.get();
                                    String st = s.getInternalState() == null ? "" : s.getInternalState().trim().toUpperCase();
                                    String last = s.getExternalLastResult() == null ? "" : s.getExternalLastResult().trim().toUpperCase();

                                    if ("COMPLETED".equals(st) && !"END".equals(last)) {
                                        publishL005AckSameTid(
                                                s.getTid(),
                                                nvl(f.barcode),
                                                nvl(f.carrierId),
                                                nvl(f.lotId),
                                                nvl(s.getPeerTrayHigh()),
                                                nvl(s.getPeerTrayType()),
                                                nvl(s.getPeerMsgType()),
                                                "END",
                                                "ZIPA stock-in completed"
                                        );
                                        l005SessionRepository.updateExternalResultByTid(s.getTid(), "END", "ZIPA stock-in completed");
                                        log.info("[MCS] Type=2/33 → 以同 TID 補送 L005 ACK: END，barcode={}, tid={}", f.barcode, s.getTid());
                                    } else {
                                        //log.debug("[MCS] Type=2/33 略過補送 END：barcode={} (state={}, last={})", f.barcode, st, last);
                                    }
                                } else {
                                    //log.debug("[MCS] Type=2/33 找不到現役 L005Session：barcode={}", f.barcode);
                                }
                            } catch (Exception e) {
                                log.warn("[MCS] Type=2/33 處理 L005 END 例外：barcode={}, err={}", f.barcode, e.getMessage(), e);
                            }

                        } else if (status == 32) {
                            // 出倉 → 出庫完成（保持原樣，不影響 L005）
                            mqttCommandService.sendS020_2002_StockOut(
                                    targetSystem,
                                    nvl(f.barcode),
                                    nvl(f.lotId),
                                    nvl(f.carrierId),
                                    "ZIPA",
                                    nvl(f.wipnameIn),
                                    nvl(f.qty)
                            );
                            log.info("[MCS] StatusReport Type=2/32 出倉 → S020-2002：{}", f);

                            // } else if (status == 36) {
                            //     // 36：流程 NG → 目前暫時不送；保留占位
                            //     // （若後續要送，請以同 TID 補送 ACK: NG 並更新 external_last_result=FAIL/NG）
                        } else if (status == 39) {
                            tryFinalizeR031IfAny(f);
                        } else if (status == 35) {
                            mqttCommandService.sendS020_2008_ProductLeaveShelf(
                                    targetSystem,
                                    nvl(f.barcode),
                                    nvl(f.lotId),
                                    nvl(f.carrierId),
                                    "ZIPA",
                                    nvl(f.wipnameOut),
                                    nvl(f.qty)
                            );

                            var r007Task = r007TaskRepository.findLatestByCarrierId(f.carrierId).orElse(null);
                            if (r007Task != null) {
                                cancelR007(r007Task, "ZIPA手動移除產品 " + f.carrierId);
                            }
                            log.info("[MCS] StatusReport Type=35 手動清除 → S020-2008：{}", f);
                        }

                    }

                    // ---- ZIPB（FSK-7004A）行為（維持原樣，不牽動 L005）----
                    else if ("FSK-7004A".equalsIgnoreCase(sender)) {
                        if (status == 33) {
                            mqttCommandService.sendS020_2004_ProductPutOnShelf(
                                    targetSystem,
                                    nvl(f.lotId),
                                    nvl(f.carrierId),
                                    "ZIPB",
                                    nvl(f.wipnameOut),
                                    nvl(f.qty)
                            );
                            log.info("[MCS] StatusReport Type=2/33 上架 → S020-2004：{}", f);

                            // 進位 R029：SHELVED
                            try {
                                r029OutputCaptureService.markShelvedByCarrierId(f.carrierId);
                            } catch (Exception ex) {
                                log.error("[MCS] ZIPB put-on-shelf → markShelved failed. carrierId={}, err={}",
                                        f.carrierId, ex.getMessage(), ex);
                            }

                        } else if (status == 34) {
                            mqttCommandService.sendS020_2005_ProductLeaveShelf(
                                    targetSystem,
                                    nvl(f.lotId),
                                    nvl(f.carrierId),
                                    "ZIPB",
                                    nvl(f.wipnameIn),
                                    nvl(f.qty)
                            );
                            log.info("[MCS] StatusReport Type=2/34 出倉 → S020-2005：{}", f);

                            // 進位 R029：STOCKED_OUT
                            try {
                                r029OutputCaptureService.markStockOutByCarrierId(f.carrierId);
                            } catch (Exception ex) {
                                log.error("[MCS] ZIPB take-off → markStockOut failed. carrierId={}, err={}",
                                        f.carrierId, ex.getMessage(), ex);
                            }
                        } else if (status == 35) {
                            // 進位 R029：REMOVED
                            try {
                                r029OutputCaptureService.markRemovedByCarrierId(f.carrierId);
                            } catch (Exception ex) {
                                log.error("[MCS] ZIPB take-off → markRemoved failed. carrierId={}, err={}",
                                        f.carrierId, ex.getMessage(), ex);
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn("[MCS] StatusReport(Type=2) 處理例外：{}", ex.getMessage(), ex);
            }
        }

        resp.setResultInfos(results);
        return resp;
    }

    // =====================================================================
    // 4) 讀卡事件（本版：先正規化成「十進位」再送 S010）
    // =====================================================================

    public CardReaderSecondaryBody onCardReader(Header header, CardReaderPrimaryBody body) {
        log.info("[MCS] 收到讀卡事件：{}", body);

        // 取得 Sender
        final String sender = (header != null && header.getSender() != null)
                ? header.getSender().trim()
                : "";

        final String rawCard = (body != null) ? body.getCardID() : null;

        // 先將 ZIP 帶來的卡號正規化為「十進位」字串
        final String decCard = normalizeCardNumber(rawCard);

        // 轉換位置與名稱
        final String deviceName;
        final String safeDoorName;
        switch (sender) {
            case "FSK-7003A" -> {
                deviceName = "ZIPA";
                safeDoorName = "ZIPA維修門";
            }
            case "FSK-7004A" -> {
                deviceName = "ZIPB";
                safeDoorName = "ZIPB維修門";
            }
            default -> {
                // 預設值（避免 null），如有需要你可改成丟錯或額外 mapping
                deviceName = "WIP";
                safeDoorName = "UNKNOWN";
            }
        }

        // 1) 發 S010（使用十進位卡號）
        String tid = null;
        try {
            MqttSendResult s010 = mqttCommandService.sendS010(targetSystem, decCard, deviceName, safeDoorName);
            tid = s010.getTid();
            log.info("[MCS] 已發 S010：raw='{}', normalized(dec)='{}', tid={}", rawCard, decCard, tid);
        } catch (Exception e) {
            log.error("[MCS] 發送 S010 失敗：raw='{}', dec='{}', err={}", rawCard, decCard, e.getMessage(), e);
        }

        // 預設回覆（若沒拿到 ACK 就用這份）→ 保守：FAIL
        CardReaderSecondaryBody resp = new CardReaderSecondaryBody();
        CardReaderSecondaryBody.ResultInfo info = new CardReaderSecondaryBody.ResultInfo();
        info.setResult(1); // 0 = PASS
        info.setResultMessage("NA,NA,NA,NA"); // 依規格空白用 NA 填
        resp.setResultInfo(info);

        // 2) 等 S010 ACK（若有 TID）
        if (tid != null && !tid.isBlank()) {
            Optional<MqttMessageLog> ackOpt = waitAckByTid("S010", tid, ackTimeoutMs, ackPollIntervalMs);
            if (ackOpt.isPresent() && ackOpt.get().getPayload() != null && !ackOpt.get().getPayload().isBlank()) {
                try {
                    S010AckPayload ack = objectMapper.readValue(ackOpt.get().getPayload(), S010AckPayload.class);
                    S010AckPayload.Message aMsg = ack.getMessage();

                    String ackResult = ack.getResult() != null ? ack.getResult().trim().toUpperCase() : "";
                    String ackResultMsg = ack.getResultMessage() != null ? ack.getResultMessage() : "";
                    String cardIdEcho = (aMsg != null && aMsg.getCardNumber() != null) ? aMsg.getCardNumber() : "";

                    // OK/START/PASS → 0；其他 → 1
                    int resCode = ("OK".equals(ackResult) || "START".equals(ackResult) || "PASS".equals(ackResult)) ? 0 : 1;
                    info.setResult(resCode);
                    info.setResultMessage(ackResultMsg);

                    log.info("[MCS] S010 ACK：result={}, msg='{}', echoCard='{}', tid={}, sentDec='{}'",
                            ackResult, ackResultMsg, cardIdEcho, tid, decCard);
                } catch (Exception parseEx) {
                    log.error("[MCS] 解析 S010 ACK 失敗：err={}", parseEx.getMessage(), parseEx);
                    info.setResult(1);
                    info.setResultMessage("ACK_PARSE_ERROR");
                }
            } else {
                log.warn("[MCS] 等待 S010 ACK 逾時或 payload 為空（tid={}，timeout={}ms，sentDec='{}'）",
                        tid, ackTimeoutMs, decCard);
                info.setResult(1);
                info.setResultMessage("ACK_TIMEOUT");
            }
        }

        return resp;
    }

    // =====================================================================
    // 5) 翻轉事件
    // =====================================================================

    public CarrierFlipSecondaryBody onCarrierFlip(Header header, CarrierFlipPrimaryBody body) {
        log.info("[MCS] 收到翻轉事件：{}", body);

        final String carrierId = (body != null && body.getMessage() != null) ? nvl(body.getMessage().getCarried()).trim() : null;

        CarrierFlipSecondaryBody resp = new CarrierFlipSecondaryBody();
        CarrierFlipSecondaryBody.ResultInfo info = new CarrierFlipSecondaryBody.ResultInfo();

        // 先檢查 carrierId
        if (testFlip) {
            info.setResult(1);
            info.setResultMessage("TEST FLIP");
            resp.setResultInfo(info);
            return resp;
        }

        // 先檢查 carrierId
        if (!notBlank(carrierId)) {
            info.setResult(2);
            info.setResultMessage("carrierId is blank");
            resp.setResultInfo(info);
            return resp;
        }

        // 撈最近一筆 R007（以對方回填為主）
        String flip = r007TaskRepository.findLatestByCarrierId(carrierId)
                .map(RobotR007Task::getFlip)
                .map(String::trim)
                .orElse(null);

        // 判斷邏輯：Y=1(翻) / N=0(不翻) / 其他=2(異常)
        if ("Y".equalsIgnoreCase(flip)) {
            info.setResult(1);                 // 1 = 翻
            info.setResultMessage("NA");       // 依規格空白用 NA 填
        } else if ("N".equalsIgnoreCase(flip)) {
            info.setResult(0);                 // 0 = 不翻
            info.setResultMessage("NA");
        } else {
            // 找不到或欄位空/非法
            info.setResult(2);
            String reason = (flip == null || flip.isEmpty())
                    ? "flip not found"
                    : ("invalid flip value: " + flip);
            info.setResultMessage(
                    reason + " for carrierId=" + carrierId
            );
        }

        resp.setResultInfo(info);
        return resp;
    }

    // =====================================================================
    // 6) 間隙檢事件
    // =====================================================================

    public CCDPlatformInputSecondaryBody onCCDPlatformInput(Header header, CCDPlatformInputPrimaryBody body) {
        log.info("[MCS] 收到間隙檢事件：{}", body);

        // ---- 準備回覆骨架（預設 PASS）----
        CCDPlatformInputSecondaryBody resp = new CCDPlatformInputSecondaryBody();
        CCDPlatformInputSecondaryBody.ResultInfo info = new CCDPlatformInputSecondaryBody.ResultInfo();
        info.setResult(0);                 // 0=PASS
        info.setResultMessage("");         // 空字串代表成功
        resp.setResultInfo(info);

        // ---- 取欄位 ----
        final String station = (body != null && body.getMessage() != null) ? nvl(body.getMessage().getName()).trim() : null;
        final String carrierId = (body != null && body.getMessage() != null) ? nvl(body.getMessage().getCarried()).trim() : null;
        final Integer status = (body != null && body.getMessage() != null) ? body.getMessage().getStatus() : null;

        // ---- 基本驗證：缺參數直接 BAD_REQUEST ----
        if (!notBlank(station) || !notBlank(carrierId)) {
            log.warn("[MCS] CCDPlatformInput 缺欄位：station={}, carrierId={}, status={}", station, carrierId, status);
            info.setResult(1);
            info.setResultMessage("BAD_REQUEST");
            return resp; // 直接回 NG
        }

        try {
            switch (status) {
                // 80=沒事：純心跳/通報，不動作，回 PASS
                case 80 -> {
                    log.info("[MCS] Status=80 noop: station={}, carrier={}", station, carrierId);
                    // info 預設 PASS，直接回
                }

                // 81=第一次到位：立刻觸發兩台相機 → 存兩張 → READY → 送 S072 → 等 ACK
                case 81 -> {
                    // 關閉同載具的現役會話，確保新 sid
                    int closed = s072SessionRepository.closeAllActiveByCarrierId(carrierId);
                    if (closed > 0) {
                        log.info("[MCS] Status=81 關閉 {} 個現役 S072 會話：carrier={}", closed, carrierId);
                    }

                    // enrich（沿用你原邏輯）
                    String enrichBarcode = null, enrichLotId = null, enrichTrayType = null;
                    try {
                        var l005Opt = l005SessionRepository.findLatestByPeerCarrierId(carrierId);
                        if (l005Opt.isPresent()) {
                            var l005 = l005Opt.get();
                            if (notBlank(l005.getBarcode())) enrichBarcode = l005.getBarcode().trim();
                            if (notBlank(l005.getPeerLotId())) enrichLotId = l005.getPeerLotId().trim();
                            if (notBlank(l005.getPeerTrayType())) enrichTrayType = l005.getPeerTrayType().trim();
                            log.info("[MCS] 由 L005 帶出欄位：carrierId={}, barcode={}, lotId={}, trayType={}",
                                    carrierId, enrichBarcode, enrichLotId, enrichTrayType);
                        } else {
                            log.info("[MCS] 無可用 L005 會話可帶值：carrierId={}", carrierId);
                        }
                    } catch (Exception ex) {
                        log.warn("[MCS] enrich(from L005) 例外：carrierId={}, err={}", carrierId, ex.getMessage(), ex);
                    }

                    // 解析左右相機
                    final CamPair cams;
                    try {
                        cams = resolveCameraPairByStationOrThrow(station);
                    } catch (Exception m) {
                        log.warn("[MCS] 無法解析相機配對：station={}", station, m);
                        info.setResult(1);
                        info.setResultMessage("UNKNOWN_STATION");
                        break;
                    }

                    // 建立新 session
                    S072Session ns = new S072Session();
                    ns.setStationName(station);
                    ns.setCameraIp(cams.leftIp + "," + cams.rightIp); // 記錄左右 IP
                    ns.setCarrierId(carrierId);
                    if (notBlank(enrichBarcode)) ns.setBarcode(enrichBarcode);
                    if (notBlank(enrichLotId)) ns.setLotId(enrichLotId);
                    if (notBlank(enrichTrayType)) ns.setTrayType(enrichTrayType);
                    ns.setCaptureMode("DUAL");
                    ns.setStatus("WAIT_FIRST");
                    ns.setCreatedAt(LocalDateTime.now());
                    ns.setUpdatedAt(LocalDateTime.now());
                    s072SessionRepository.save(ns);

                    // 立刻觸發兩台相機（或 file 模式用檔案）
                    final String leftPathStr;
                    final String rightPathStr;

                    if (useFileMode()) {
                        Path base = Paths.get(cameraMockBaseDir);
                        Path leftMock = base.resolve(station + "_left.jpg");
                        Path rightMock = base.resolve(station + "_right.jpg");
                        leftPathStr = requireMock(leftMock);
                        rightPathStr = requireMock(rightMock);
                        log.info("[S072][FILE] 使用檔案：left={}, right={}", leftPathStr, rightPathStr);
                    } else {
                        var snapLeft = camera.snapOnceByIp(cams.leftIp, Paths.get(cameraSaveRoot), "jpg",
                                cameraJpgQuality, false, station + "_left", true, cams.exposureUs);
                        var snapRight = camera.snapOnceByIp(cams.rightIp, Paths.get(cameraSaveRoot), "jpg",
                                cameraJpgQuality, false, station + "_right", true, cams.exposureUs);
                        leftPathStr = (snapLeft.file() == null ? null : snapLeft.file().toString());
                        rightPathStr = (snapRight.file() == null ? null : snapRight.file().toString());
                        log.info("[S072][DEVICE] 拍照完成：left={}, right={}", leftPathStr, rightPathStr);
                    }

                    s072SessionRepository.updateFirstCapture(
                            ns.getId(),
                            leftPathStr,
                            LocalDateTime.now()
                    );
                    s072SessionRepository.updateSecondCapture(
                            ns.getId(),
                            rightPathStr,
                            LocalDateTime.now()
                    );
                    log.info("[S072] 第一次到位已備妥兩張：sid={}, left={}, right={}",
                            ns.getId(), leftPathStr, rightPathStr);

                    // 標 READY → 送 S072 → 等 ACK（原樣）
                    s072SessionRepository.updateStatus(ns.getId(), "READY");
                    sendS072AndAwaitAck(ns.getId(), station, info);

                }

                // 82=收到第一次拍照結束（設備側流程用），單純記錄
                case 82 -> {
                    log.info("[MCS] Status=82(第一次拍照結束) station={}, carrier={}", station, carrierId);
                    // 不調整狀態，回 PASS
                }

                // 83=第二次到位：同樣立刻觸發兩台相機 → 覆蓋兩張 → READY → 送 S072 → 等 ACK
                case 83 -> {
                    S072Session s = s072SessionRepository.findActiveByCarrierId(carrierId)
                            .orElseThrow(() -> new IllegalStateException("找不到現役 S072 session: carrierId=" + carrierId));

                    final CamPair cams;
                    try {
                        cams = resolveCameraPairByStationOrThrow(station);
                    } catch (Exception m) {
                        log.warn("[MCS] 無法解析相機配對：station={}", station, m);
                        info.setResult(1);
                        info.setResultMessage("UNKNOWN_STATION");
                        break;
                    }

                    // 立刻觸發兩台相機（或 file 模式用檔案）
                    final String leftPathStr;
                    final String rightPathStr;

                    if (useFileMode()) {
                        Path base = Paths.get(cameraMockBaseDir);
                        Path leftMock = base.resolve(station + "_left.jpg");
                        Path rightMock = base.resolve(station + "_right.jpg");
                        leftPathStr = requireMock(leftMock);
                        rightPathStr = requireMock(rightMock);
                        log.info("[S072][FILE] 使用檔案：left={}, right={}", leftPathStr, rightPathStr);
                    } else {
                        var snapLeft = camera.snapOnceByIp(cams.leftIp, Paths.get(cameraSaveRoot), "jpg",
                                cameraJpgQuality, false, station + "_left", true, cams.exposureUs);
                        var snapRight = camera.snapOnceByIp(cams.rightIp, Paths.get(cameraSaveRoot), "jpg",
                                cameraJpgQuality, false, station + "_right", true, cams.exposureUs);
                        leftPathStr = (snapLeft.file() == null ? null : snapLeft.file().toString());
                        rightPathStr = (snapRight.file() == null ? null : snapRight.file().toString());
                        log.info("[S072][DEVICE] 拍照完成：left={}, right={}", leftPathStr, rightPathStr);
                    }

                    s072SessionRepository.updateFirstCapture(
                            s.getId(),
                            leftPathStr,
                            LocalDateTime.now()
                    );
                    s072SessionRepository.updateSecondCapture(
                            s.getId(),
                            rightPathStr,
                            LocalDateTime.now()
                    );
                    log.info("[S072] 第二次到位已備妥兩張：sid={}, left={}, right={}",
                            s.getId(), leftPathStr, rightPathStr);

                    // 標 READY → 送 S072 → 等 ACK（原樣）
                    s072SessionRepository.updateStatus(s.getId(), "READY");
                    sendS072AndAwaitAck(s.getId(), station, info);

                }

                // 84=收到第二次拍照結束（設備側流程用），單純記錄
                case 84 -> {
                    log.info("[MCS] Status=84(第二次拍照結束) station={}, carrier={}", station, carrierId);
                    // 不調整狀態，回 PASS
                }

                // 85=拍照異常
                case 85 -> {
                    s072SessionRepository.findActiveByCarrierId(carrierId).ifPresent(s ->
                            s072SessionRepository.markError(s.getId(), "CAPTURE_ERROR_FROM_DEVICE"));
                    log.warn("[MCS] 拍照異常：station={}, carrier={}", station, carrierId);
                    info.setResult(1);
                    info.setResultMessage("CAPTURE_ERROR");
                }

                // 未知狀態：回 UNKNOWN_STATUS
                default -> {
                    log.warn("[MCS] 未知狀態：{}", status);
                    info.setResult(1);
                    info.setResultMessage("UNKNOWN_STATUS");
                }
            }
        } catch (Exception e) {
            log.error("[MCS] CCDPlatformInput 處理例外：{}", e.getMessage(), e);
            info.setResult(1);
            info.setResultMessage("SERVER_ERROR");
        }

        return resp;
    }


    // =====================================================================
    // 私有工具：等待 ACK / 卡號正規化 / 雜項
    // =====================================================================

    private boolean useFileMode() {
        return "file".equalsIgnoreCase(cameraMode);
    }

    /**
     * file 模式檢查檔案存在且非 0 bytes，回傳絕對路徑字串（失敗會丟 RuntimeException）
     */
    private String requireMock(Path p) {
        try {
            if (!Files.exists(p)) throw new RuntimeException("mock image not found: " + p);
            if (Files.size(p) <= 0) throw new RuntimeException("mock image is empty: " + p);
            return p.toAbsolutePath().toString();
        } catch (Exception e) {
            throw new RuntimeException("mock image check failed: " + p + ", " + e.getMessage(), e);
        }
    }

    /**
     * 以 TID 等待最新一筆指定 cmdId 的 ACK。
     * - 會呼叫 repository 取回同一 TID 的所有紀錄，在程式側挑最新一筆 ACK。
     */
    private Optional<MqttMessageLog> waitAckByTid(String cmdId, String tid, long timeoutMs, long intervalMs) {
        long start = System.currentTimeMillis();
        do {
            Optional<MqttMessageLog> ackOpt = findLatestAckInTid(cmdId, tid);
            if (ackOpt.isPresent()) return ackOpt;

            try {
                Thread.sleep(Math.max(10L, intervalMs));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        } while (System.currentTimeMillis() - start < timeoutMs);

        return Optional.empty();
    }

    /**
     * 從同一個 TID 的所有紀錄裡，找出「最新」的一筆該 cmdId 的 ACK
     */
    private Optional<MqttMessageLog> findLatestAckInTid(String cmdId, String tid) {
        try {
            List<MqttMessageLog> all = mqttMessageLogRepository.findAllByTid(tid);
            if (all == null || all.isEmpty()) return Optional.empty();

            return all.stream()
                    .filter(this::isAck) // 只留 ACK
                    .filter(log -> cmdId.equalsIgnoreCase(safeStr(log.getCmdId()))) // 只留指定 cmdId
                    // 以 id DESC 或 createdTime DESC 取「最新」；這裡以 id 為準
                    .sorted((a, b) -> Long.compare(optLong(b.getId()), optLong(a.getId())))
                    .findFirst();
        } catch (Exception e) {
            log.warn("[MCS] 查詢 TID={} 的 ACK 失敗：{}", tid, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * 判斷是否 ACK（容忍 enum 或字串）
     */
    private boolean isAck(MqttMessageLog log) {
        String mt = safeStr(log.getMessageType());   // e.g. "ACK" 或 enum.toString()
        return "ACK".equalsIgnoreCase(mt);
    }

    private String safeStr(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private long optLong(Long v) {
        return v == null ? 0L : v;
    }

    private String text(JsonNode node, String field, String def) {
        if (node == null) return def;
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? def : v.asText(def);
    }

    // ---------------------------------------------------------------------
    // 卡號正規化（重點）
    // ---------------------------------------------------------------------

    /**
     * 將原始卡號正規化為「十進位字串」：
     * 1) 嘗試走 hex 路徑：清雜訊(移除非 0-9A-F)、可選大寫、可選兩兩倒序、小端→大端
     * 2) 用 BigInteger 將 hex 轉為十進位（避免 long 溢位）
     * 3) 若無法確定是 hex（或 hex 解析失敗），fallback 擷取純數字（有些設備直接送十進位）
     * 4) 最後仍失敗則回傳原字串（避免空值）
     */
    private String normalizeCardNumber(String raw) {
        if (raw == null || raw.isBlank()) return "";

        // 去除 CR/LF 與頭尾空白
        String trimmed = raw.replace("\r", "").replace("\n", "").trim();

        // 判斷是否像 hex：always-hex 或字串中包含 A-F
        boolean looksHex = cardAlwaysHex || HEX_ALPHA.matcher(trimmed).find();

        // 只保留「0-9A-Fa-f」字元
        String hexClean = NON_HEX.matcher(trimmed).replaceAll("");

        if (looksHex && !hexClean.isEmpty()) {
            String hex = cardUppercase ? hexClean.toUpperCase(Locale.ROOT) : hexClean;
            if (cardReverse) hex = reverseByPairs(hex);

            String dec = hexToDecimalSafe(hex);
            if (dec != null) {
                //log.debug("[MCS] 卡號解析 hex->dec：raw='{}' hex='{}' dec='{}'", raw, hex, dec);
                return dec;
            }
            //log.debug("[MCS] 卡號 hex 解析失敗，嘗試數字 fallback：raw='{}' hex='{}'", raw, hex);
        }

        // fallback：擷取純數字（十進位）
        Matcher m = DIGITS.matcher(trimmed);
        if (m.find()) {
            String dec = m.group();
            //log.debug("[MCS] 卡號採用數字 fallback：raw='{}' dec='{}'", raw, dec);
            return dec;
        }

        // 再不行就回原字串（避免空）
        //log.debug("[MCS] 卡號無法解析為十進位，回傳原字串：raw='{}'", raw);
        return trimmed;
    }

    /**
     * 使用 BigInteger 將十六進位字串轉為十進位字串；失敗回 null（避免拋例外）
     */
    private static String hexToDecimalSafe(String hex) {
        try {
            if (hex == null || hex.isBlank()) return null;
            return new BigInteger(hex, 16).toString(10);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 兩兩倒序："12345678" -> "78563412"
     * - 常見於卡機/設備用小端序傳 hex 時
     * - 若長度為奇數，保守地丟棄最後一碼避免錯配
     */
    private static String reverseByPairs(String s) {
        int n = s.length();
        if ((n & 1) == 1) n = n - 1;
        StringBuilder out = new StringBuilder(n);
        for (int i = n - 2; i >= 0; i -= 2) {
            out.append(s.charAt(i)).append(s.charAt(i + 1));
        }
        return out.toString();
    }

    // ===================== 7004A 專用：DB 補值（強型別版） =====================

    /**
     * 依 barcode(優先)/carried 尋找 container_main，再用 container_attr/container_data 補值
     */
    private InputResolvedFields resolveInputFromDb(String inBarcode, String inCarried) {
        InputResolvedFields r = new InputResolvedFields();

        // 1) 先用 barcode 找 container_main.container_code；若無，再用 carried 找 alias_code
        Optional<ContainerMain> cmOpt = findContainerMainByBarcodeOrCarrier(inBarcode, inCarried);
        if (cmOpt.isEmpty()) {
            log.warn("[MCS] 7004A：無法依 barcode/carried 找到 ContainerMain，使用對方原樣。barcode={}, carried={}",
                    inBarcode, inCarried);
            r.barcode = nvl(inBarcode);
            r.carried = nvl(inCarried);
            return r;
        }

        ContainerMain cm = cmOpt.get();

        // ---- ContainerMain 直接對應 ----
        // barcode：containerCode（若無則沿用對方）
        r.barcode = firstNonBlank(cm.getContainerCode(), inBarcode);
        // carried：aliasCode（carrierId）
        r.carried = firstNonBlank(cm.getAliasCode(), inCarried);
        // lotId：lotNo
        r.lotId = nvl(cm.getLotNo());
        // trayType：partNo
        r.trayType = nvl(cm.getPartNo());

        // 2) 取 container_attr：tray_thickness_mm -> trayHigh（若有即覆蓋）
        containerAttrRepository.findOne(cm.getId(), "tray_thickness_mm")
                .map(ContainerAttr::getAttrValue)
                .filter(this::notBlank)
                .ifPresent(v -> r.trayHigh = v);

        // 3) 取 container_attr：bin_type -> 若為 B 打進 NG
        containerAttrRepository.findOne(cm.getId(), "bin_type")
                .map(ContainerAttr::getAttrValue)
                .filter(this::notBlank)
                .ifPresent(v -> r.binType = v);

        // 4) 取 container_attr：INSPECT_PIECES_DELTA -> 若有值代表有問題
        containerAttrRepository.findOne(cm.getId(), "INSPECT_PIECES_DELTA")
                .map(ContainerAttr::getAttrValue)
                .filter(this::notBlank)
                .ifPresent(v -> r.inspectPieces = v);

        return r;
    }

    /**
     * 以強型別 Repository 查詢 ContainerMain：
     * - 先用 barcode 對 containerCode（findByContainerCode）
     * - 若無，再用 carried 對 aliasCode（findByAliasCode）
     */
    private Optional<ContainerMain> findContainerMainByBarcodeOrCarrier(String barcode, String carried) {
        if (notBlank(barcode)) {
            Optional<ContainerMain> byCode = containerMainRepository.findByContainerCode(barcode.trim());
            if (byCode.isPresent()) return byCode;
        }
        if (notBlank(carried)) {
            Optional<ContainerMain> byAlias = containerMainRepository.findByAliasCode(carried.trim());
            if (byAlias.isPresent()) return byAlias;
        }
        return Optional.empty();
    }

    // ---- 小工具（同前版，可重用）----
    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }

    private String firstNonBlank(String a, String b) {
        return notBlank(a) ? a : nvl(b);
    }

    private boolean isInspectDeltaNonZero(String deltaStr) {
        if (StringUtils.isBlank(deltaStr)) {
            return false;
        }

        try {
            // "+3"、"-2"、"0"、"+0" 都可處理
            int delta = Integer.parseInt(deltaStr.trim());
            return delta != 0;
        } catch (NumberFormatException e) {
            // 若格式異常，保守視為有問題
            log.warn("[MCS] Invalid INSPECT_PIECES_DELTA format: {}", deltaStr);
            return true;
        }
    }

    // ---- 7004A 用的承載（同前版，可重用）----
    private static final class InputResolvedFields {
        String barcode;       // container_main.container_code
        String lotId;         // container_main.lot_no
        String carried;       // container_main.alias_code
        String trayHigh;      // container_attr['tray_thickness_mm'] 或 data 備援
        String trayType;      // container_main.part_no
        String messageType;   // 需要時可另行補 attr('message_type')
        String binType;       // bintype 若為 B 代表 REJECT 線進來
        String inspectPieces; // 異物檢檢查數量

        @Override
        public String toString() {
            return "barcode=" + barcode + ", lotId=" + lotId + ", carried=" + carried +
                    ", trayHigh=" + trayHigh + ", trayType=" + trayType + ", messageType=" + messageType;
        }
    }

    /**
     * 解析 Type=2 訊息欄位
     */
    private ParsedFields parseType2Fields(List<String> fields) {
        ParsedFields f = new ParsedFields();

        if (fields == null) return f;
        if (fields.size() > 0 && notBlank(fields.get(0))) f.barcode = fields.get(0).trim();
        if (fields.size() > 1 && notBlank(fields.get(1))) f.carrierId = fields.get(1).trim();
        if (fields.size() > 2 && notBlank(fields.get(2))) f.lotId = fields.get(2).trim();
        if (fields.size() > 3 && notBlank(fields.get(3))) f.qty = fields.get(3).trim();
        if (fields.size() > 4 && notBlank(fields.get(4))) f.wipnameIn = fields.get(4).trim();
        if (fields.size() > 5 && notBlank(fields.get(5))) f.wipnameOut = fields.get(5).trim();

        return f;
    }

    /**
     * 承載解析/補值後的欄位
     */
    private static final class ParsedFields {
        String barcode = "";
        String carrierId = "";
        String lotId = "";
        String qty = "";
        String type = "";
        String wipnameIn = "";
        String wipnameOut = "";

        @Override
        public String toString() {
            return "barcode=" + barcode + ", carrierId=" + carrierId + ", lotId=" + lotId +
                    ", qty=" + qty + ", type=" + type + ", wipnameIn=" + wipnameIn + ", wipnameOut=" + wipnameOut;
        }
    }

    /**
     * 以「同 TID」回送 L005 的 ACK（完整 MESSAGE 欄位版）。
     */
    private void publishL005AckSameTid(
            String tid,
            String barcode,
            String carrierId,
            String lotId,
            String trayHigh,
            String trayType,
            String messageType,
            String result,
            String resultMessage
    ) {
        try {
            L005AckPayload ack = new L005AckPayload();
            ack.setCmd("LOAD");                         // 與對應協定一致
            ack.setCmdId("L005");
            ack.setIdDesc("BARCODE_CHECK_EVENT");
            ack.setTid(tid);

            L005AckPayload.Message m = new L005AckPayload.Message();
            m.setBarcode(barcode);
            m.setCarrierId(carrierId);
            m.setLotId(lotId);
            m.setTrayHigh(trayHigh);
            m.setTrayType(trayType);
            m.setMessageType(messageType);
            ack.setMessage(m);

            ack.setResult(result == null ? "" : result);
            ack.setResultMessage(resultMessage == null ? "" : resultMessage);

            String json = objectMapper.writeValueAsString(ack);

            // 直接以 ACK 型式發布（同一個 TID、CMD_ID=L005）
            publisher.publish(targetSystem, json, MqttMessageType.ACK, tid, "L005");
            log.info("[MCS] 已發 L005 ACK（同 TID）：tid={}, result={}, msg={}", tid, ack.getResult(), ack.getResultMessage());
        } catch (Exception e) {
            log.warn("[MCS] 發送 L005 ACK 失敗：tid={}, err={}", tid, e.getMessage(), e);
        }
    }

    private static final class CamPair {
        final String leftIp;
        final String rightIp;
        final long exposureUs; // 新增：曝光時間(微秒)

        CamPair(String leftIp, String rightIp, long exposureUs) {
            this.leftIp = leftIp;
            this.rightIp = rightIp;
            this.exposureUs = exposureUs;
        }
    }


    private CamPair resolveCameraPairByStationOrThrow(String station) {
        if (station == null || station.isBlank()) throw new IllegalArgumentException("station is blank");
        return switch (station.trim().toUpperCase()) {
            // STK01 使用 241/242
            case "STK01" -> new CamPair("192.168.3.241", "192.168.3.242", 75_000L);
            // STK02 使用 243/244
            case "STK02" -> new CamPair("192.168.3.243", "192.168.3.244", 230_000L);
            default -> throw new IllegalArgumentException("unknown station: " + station);
        };
    }

    private void sendS072AndAwaitAck(Long sessionId, String station, CCDPlatformInputSecondaryBody.ResultInfo info) {
        // 1) 取 session
        S072Session s = s072SessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalStateException("S072 session not found id=" + sessionId));

        try {
            // 2) 讀檔（左右都要；若未來協定允許單張再放寬）
            byte[] left = readFileBytesSafe(s.getImagePath1());
            byte[] right = readFileBytesSafe(s.getImagePath2());
            if (left == null || right == null) {
                String reason = "IMAGE_MISSING(leftNull=" + (left == null) + ", rightNull=" + (right == null) + ")";
                s072SessionRepository.markError(s.getId(), reason); // 我方錯誤只標 ERROR，不寫入 NG
                log.warn("[S072] 影像缺失：sid={}, {}", s.getId(), reason);
                info.setResult(1);
                info.setResultMessage(reason);
                return;
            }

            // 3) 組 payload
            S072CommandPayload.Message msg = new S072CommandPayload.Message();
            msg.setCarrierId(nvl(s.getCarrierId()));
            msg.setLotId(nvl(s.getLotId()));
            msg.setTrayType(nvl(s.getTrayType()));
            msg.setLocation(nvl(station));
            msg.setTrayLeftImage(left);
            msg.setTrayRightImage(right);

            // 4) 發送 S072（指定 TID）+ 標 SENT
            final String tid = BaseMqttHandlerUtils.generateTid();
            MqttSendResult send = mqttCommandService.sendS072WithTid(targetSystem, tid, msg);
            log.info("[S072] 已發 S072：sid={}, tid={}, dispatch={}", s.getId(), tid, send);

            s072SessionRepository.updateTid(s.getId(), tid);
            s072SessionRepository.updateStatus(s.getId(), "SENT");

            // 5) 輪詢 session（由 S072AckHandler 回填 result/status=ACK）
            final long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < ackTimeoutMs) {
                Optional<S072Session> curOpt = s072SessionRepository.findByTid(tid);
                if (curOpt.isPresent()) {
                    S072Session cur = curOpt.get();
                    String st = nvl(cur.getStatus()).toUpperCase();

                    if ("ACK".equals(st)) {
                        String r = nvl(cur.getResult()).trim().toUpperCase();     // 由 AckHandler 保證只會是 OK/NG
                        String m = nvl(cur.getResultMessage());
                        log.info("[S072] 等到 ACK：sid={}, tid={}, result={}, msg={}", cur.getId(), tid, r, m);

                        if ("OK".equals(r)) {
                            info.setResult(0);
                            info.setResultMessage(m);
                        } else { // "NG"
                            info.setResult(1);
                            info.setResultMessage(m);
                        }
                        return;
                    }

                    if ("ERROR".equals(st)) {
                        String em = nvl(cur.getErrorMessage());
                        log.warn("[S072] 會話已標 ERROR：sid={}, tid={}, err={}", cur.getId(), tid, em);
                        info.setResult(1);
                        info.setResultMessage(em.isBlank() ? "ERROR" : em);
                        return;
                    }
                }

                try {
                    Thread.sleep(Math.max(10L, ackPollIntervalMs));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            // 6) 逾時：標 ERROR，回 FAIL
            s072SessionRepository.markErrorByTid(tid, "ACK_TIMEOUT");
            log.warn("[S072] 等待 ACK 逾時：sid={}, tid={}", s.getId(), tid);
            info.setResult(1);
            info.setResultMessage("ACK_TIMEOUT");

        } catch (Exception e) {
            String reason = "SEND_OR_PARSE_ERROR:" + e.getMessage();
            s072SessionRepository.markError(s.getId(), reason); // 我方錯誤只標 ERROR
            log.error("[S072] 發送/解析流程例外：sid={}, err={}", s.getId(), e.getMessage(), e);
            info.setResult(1);
            info.setResultMessage(reason);
        }
    }

    private static byte[] readFileBytesSafe(String path) {
        if (path == null || path.isBlank()) return null;
        try {
            return Files.readAllBytes(Path.of(path));
        } catch (Exception e) {
            throw new RuntimeException("讀圖失敗: " + path, e);
        }
    }

    // ======================= 新增：R031 END 整合 =======================

    /**
     * TO 成功後呼叫：以 carrierId 找進行中 R031 任務 → 發 ASE END 並更新 task 為 END/COMPLETED
     */
    private void tryFinalizeR031IfAny(ParsedFields f) {

        String carrierId = f.carrierId;
        if (StringUtils.isBlank(carrierId)) return;

        // 找 open 的 R031 任務（QUEUED/PROCESSING 之類）再比對 carrierId
        RobotR031Task match = r031TaskRepository.findOpen().stream()
                .filter(t -> StringUtils.equalsIgnoreCase(carrierId, nz(t.getCarrierId())))
                .findFirst()
                .orElse(null);
        if (match == null) {
            //log.debug("[R031][END] 無需上報：找不到 open R031 與 carrierId={} 相符的任務", carrierId);
            return;
        }

        try {
            // 1) 發 ACK: R008 END → ASE（沿用 R008 任務 TID）
            R031AckPayload out = new R031AckPayload();
            out.setCmd("ROBOT");
            out.setCmdId("R031");
            out.setTid(match.getTid());
            out.setIdDesc("STK_MOVE_SCH_TO_MANUAL_PORT");

            R031AckPayload.Message m = new R031AckPayload.Message();
            m.setLotId(match.getLotId());
            m.setCarrierId(match.getCarrierId());
            m.setWipName(match.getWipName());
            out.setMessage(m);

            out.setResult("END");
            out.setResultMessage("");

            JsonNode payload = objectMapper.valueToTree(out);
            logService.recordReturningId(
                    "ack/r031/auto-end",
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

            log.info("[R031][END→ASE] 已自動上報：tid={}, carrierId={}, receiver={}", out.getTid(), carrierId, aseSystem);

            // 2) 更新任務：external_last_result=END、internal_state=COMPLETED
            RobotR031Task patch = new RobotR031Task();
            patch.setLogId(match.getLogId());
            patch.setExternalLastResult("END");
            patch.setExternalLastTime(LocalDateTime.now());
            patch.setInternalState("COMPLETED");
            patch.setUpdatedTime(LocalDateTime.now());

            boolean ok = r031TaskRepository.updateByLogId(patch);
            if (!ok) {
                log.warn("[R031][END] 任務狀態更新失敗：logId={}", match.getLogId());
            } else {
                log.info("[R031][END] 任務已更新為 COMPLETED/END：logId={}", match.getLogId());
            }
        } catch (Exception e) {
            log.error("[R031][END] 自動上報或更新任務失敗：carrierId={}, err={}", carrierId, e.getMessage(), e);
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }


    private void cancelR007(RobotR007Task t, String reason) {
        if (t == null) return;

        // if (isTerminated(t.getExternalLastResult())) {
        //     log.info("[R007-CANCEL] id={} 已終態({})，略過", id, t.getExternalLastResult());
        //     return;
        // }

        // 1) 先更新 DB
        t.setExternalLastResult("CANCEL");
        t.setExternalLastTime(LocalDateTime.now());
        t.setCancelReason(reason);
        t.setInternalState("CANCELLED");
        r007TaskRepository.update(t);

        // 2) 再把 DB 的內容組成 MQTT ACK 丟給 ASE
        try {
            sendR007CancelToAse(t, reason);
            sendR007CancelToSEEC(t, reason);
        } catch (Exception e) {
            log.error("[R007-CANCEL→ASE] 發送 CANCEL ACK 失敗：id={}, tid={}, err={}",
                    t.getCarrierId(), t.getTid(), e.getMessage(), e);
        }
    }

    private void sendR007CancelToAse(RobotR007Task t, String reason) throws Exception {
        if (t.getTid() == null) {
            log.warn("[R007-CANCEL→ASE] 任務缺少 TID，略過發送：id={}", t.getId());
            return;
        }

        R007AckPayload ack = new R007AckPayload();
        ack.setCmd("ROBOT");
        ack.setCmdId("R007");
        ack.setTid(t.getTid());
        ack.setIdDesc("ROBOT_MOVE_SCH_TO_EQP");

        R007AckPayload.Message m = new R007AckPayload.Message();
        m.setLotId(t.getLotId());
        m.setCarrierId(t.getCarrierId());
        m.setWipName(t.getWipName());
        m.setDestLoc(t.getDestLoc());
        m.setEqpPort(t.getEqpPort());
        m.setTrayHigh(t.getTrayHigh());
        m.setTrayType(t.getTrayType());
        m.setTrayNum(t.getTrayNum());
        m.setDeviceName(t.getDeviceName());
        m.setMovePriority(t.getMovePriority());
        m.setMissionTrip(t.getMissionTrip());
        m.setOdo(t.getOdo());
        m.setAmrSpeed(t.getAmrSpeed());
        m.setAmrRobotSpeed(t.getAmrRobotSpeed());
        m.setPpkgBodySize(t.getPpkgBodySize());

        ack.setMessage(m);

        ack.setResult("CANCEL");
        ack.setResultMessage(reason);

        var jsonNode = objectMapper.valueToTree(ack);
        logService.recordReturningId(
                "ack/r007/manual-cancel",
                logService.getLocalSystem(),   // sender
                aseSystem,                     // receiver
                jsonNode,
                MqttMessageType.ACK
        );

        publisher.publish(
                aseSystem,
                objectMapper.writeValueAsString(ack),
                MqttMessageType.ACK,
                ack.getTid(),
                ack.getCmdId()
        );

        log.info("[R007-CANCEL→ASE] 已送 R007 CANCEL：id={}, tid={}, reason={}",
                t.getId(), ack.getTid(), reason);
    }

    private void sendR007CancelToSEEC(RobotR007Task t, String reason) throws Exception {
        if (t.getTid() == null) {
            log.warn("[R007-CANCEL→SEEC] 任務缺少 TID，略過發送：id={}", t.getId());
            return;
        }

        String cmdTid = "R007_" + t.getTid();
        MqttSendResult result = mqttCommandService.sendR018("SEEC", cmdTid);

        log.info("[R018-CANCEL→SEEC] 已送 R018 CANCEL：cmd=R007, id={}, tid={}, reason={}",
                t.getId(), result.getTid(), reason);
    }

}
