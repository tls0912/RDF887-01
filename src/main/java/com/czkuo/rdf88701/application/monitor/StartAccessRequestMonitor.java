package com.czkuo.rdf88701.application.monitor;

import com.czkuo.rdf88701.application.service.PlcAccessService;
import com.czkuo.rdf88701.application.service.mqtt.MqttCommandService;
import com.czkuo.rdf88701.common.dto.MqttSendResult;
import com.czkuo.rdf88701.domain.repository.StartAccessInfoRepository;
import com.czkuo.rdf88701.infra.entity.StartAccessInfo;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * StartAccessRequestMonitor（S013）
 *
 * 功能總覽（完整版）：
 *  1) 週期掃描 PLC 的 RESET/START 請求字：
 *     - W1035=WIP、W1037=FSK6001A、W1039=ZIPA、W103B=ZIPB
 *     - 值=1 → START；值=256 → RESET；(其餘忽略)
 *     - 發送 S013 成功 → 以 TID + target_code + req_value 入庫 start_access_info（PENDING / writeback=WAITING）
 *     - 用 in-memory inflight（key=target_code）避免同目標未結案時重送
 *
 *  2) 逾時批次：
 *     - 將「現在-ackTimeoutMs 之前仍 PENDING」標記 TIMEOUT 並視同 NG
 *     - 仍維持 writeback_status=WAITING，讓寫回流程去回寫 PLC
 *
 *  3) 回寫結果到 PLC + 三段式補償（寫值→打握手→清請求）：
 *     - 撈取 writeback_status=WAITING
 *     - 狀態=ACK_OK → 回寫 1；ACK_NG/TIMEOUT → 回寫 2
 *     - 寫 ReturnCode(W003x) → 打握手(W003x+1=1 → 延遲 → 0) → 清請求(W103x=0)
 *     - 成功 → markWritebackSuccess；失敗 → markWritebackFailed
 *     - 完成後同步清除 inflight
 *
 * 設計重點：
 *  - 位址用固定對應（不走 properties 表）
 *  - DB 是唯一真相（ACK/逾時皆先回 DB，再由本類回寫 PLC）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartAccessRequestMonitor {

    /* ====== 依賴 ====== */
    private final PlcAccessService plc;
    private final MqttCommandService mqtt;
    private final StartAccessInfoRepository repo;

    /* ====== 可調參數（yml 覆寫） ====== */

    /** S013 送往的對方系統代碼（預設 ASE） */
    @Value("${start.mqtt.target-system:ASE}")
    private String targetSystem;

    /** PLC 裝置名稱 */
    @Value("${start.mqtt.device-name:PLC-Main}")
    private String plcDevice;

    /** 逾時毫秒（PENDING 超過此時間視為逾時） */
    @Value("${start.monitor.ack-timeout-ms:15000}")
    private long ackTimeoutMs;

    /** 每次寫回最多處理幾筆（避免長時間佔用） */
    @Value("${start.monitor.writeback-pick-limit:20}")
    private int writebackPickLimit;

    /** 握手打 1 → 清 0 的延遲毫秒 */
    @Value("${start.monitor.handshake-pulse-ms:80}")
    private long handshakePulseMs;

    /** 寫回前是否先讀目前 ReturnCode，已一致就不再寫（避免重複 IO） */
    @Value("${start.monitor.double-check-before-write:true}")
    private boolean doubleCheckBeforeWrite;

    /** 請求掃描週期（預設 200ms），用於 @Scheduled fixedRateString */
    @Value("${start.monitor.fixed-rate-ms:200}")
    private String pollFixedRateMs;

    /** 逾時掃描週期（預設 1000ms），用於 @Scheduled fixedDelayString */
    @Value("${start.monitor.timeout-scan-ms:1000}")
    private String timeoutScanMs;

    /** 寫回排程（預設 150ms），用於 @Scheduled fixedDelayString */
    @Value("${start.monitor.writeback-fixed-delay-ms:150}")
    private String writebackDelayMs;

    /* ====== in-memory inflight（避免同 target 重送） ====== */
    private final Map<String, Inflight> inflightByTarget = new ConcurrentHashMap<>();

    /* **********************************************************************
     * 固定位址對映（不走 properties）
     * **********************************************************************/

    /**
     * 每個目標的 PLC 位址定義。
     * reqAddr/reqHsAddr = W103x / W103x+1
     * rcAddr/rcHsAddr   = W003x / W003x+1
     */
    @Getter
    private static final class Target {
        private final String code;   // 'WIP' / 'FSK6001A' / 'ZIPA' / 'ZIPB'
        private final int reqAddr;
        private final int reqHsAddr;
        private final int rcAddr;
        private final int rcHsAddr;

        private Target(String code, int reqAddr, int rcAddr) {
            this.code = code;
            this.reqAddr = reqAddr;
            this.reqHsAddr = reqAddr + 1;
            this.rcAddr = rcAddr;
            this.rcHsAddr = rcAddr + 1;
        }
    }

    // 依據你的指定：WIP, 拆併區, ZIPA, ZIPB
    private static final Target T_WIP        = new Target("WIP",     0x1035, 0x0035);
    private static final Target T_DISMANTLE  = new Target("拆併區",    0x1037, 0x0037); // 原 FSK6001A 改成「拆併區」
    private static final Target T_ZIPA       = new Target("ZIPA",    0x1039, 0x0039);
    private static final Target T_ZIPB       = new Target("ZIPB",    0x103B, 0x003B);

    /** 掃描順序：WIP → 拆併區 → ZIPA → ZIPB */
    private static final List<Target> TARGETS = List.of(
            T_WIP, T_DISMANTLE, T_ZIPA, T_ZIPB
    );

    /** 代碼查表（DB 寫回/日誌使用） */
    private static final Map<String, Target> BY_CODE = Map.of(
            T_WIP.getCode(),       T_WIP,
            T_DISMANTLE.getCode(), T_DISMANTLE,
            T_ZIPA.getCode(),      T_ZIPA,
            T_ZIPB.getCode(),      T_ZIPB
    );

    /* **********************************************************************
     * 1) 掃描 PLC 請求字 → 發 S013 + 入庫 PENDING
     * **********************************************************************/

    /**
     * 每固定週期掃描 Start/Reset 請求：
     *  - req=0 → 清 inflight
     *  - req=1(START) / 256(RESET) 且不在 inflight → 發 S013 → savePending → inflight.put
     */
    @Scheduled(fixedDelayString = "${start.monitor.fixed-rate-ms:200}")
    public void pollStartRequests() {
        for (Target t : TARGETS) {
            int req = readU16(t.reqAddr);

            if (req == 0) {
                // 現場清除請求 → 同步清 in-flight
                inflightByTarget.remove(t.code);
                continue;
            }
            if (req != 1 && req != 256) {
                // 僅接受 1(START) / 256(RESET)
                continue;
            }
            if (inflightByTarget.containsKey(t.code)) {
                // 未結案 → 避免重送
                continue;
            }

            // 發 S013（RESET/START 驗證），不夾帶參數（你的 DTO 即此定義）
            MqttSendResult send = mqtt.sendS013(targetSystem, t.getCode());
            if (!send.isSuccess()) {
                log.error("[S013] 發送失敗 target={}, req={}, err={}", t.code, req, send.getMessage());
                continue;
            }

            String tid = send.getTid();
            boolean saved = repo.savePending(tid, t.code, req);
            if (!saved) {
                log.warn("[S013] savePending 失敗（可能 DB 問題），target={}, tid={}, req={}", t.code, tid, req);
                // 仍放入 inflight，避免短時間內重送；等現場清 0 或寫回後會自然回收
            }

            inflightByTarget.put(t.code, new Inflight(tid, t.code, req, System.currentTimeMillis()));
            log.info("[S013] ▶️ 已送 S013，target={}, req={}, tid={}", t.code, req, tid);
        }
    }

    /* **********************************************************************
     * 2) 逾時批次：PENDING → TIMEOUT/NG
     * **********************************************************************/
    @Scheduled(fixedDelayString = "${start.monitor.timeout-scan-ms:1000}")
    public void markTimeoutsBatch() {
        try {
            int n = repo.markTimeoutAsNg(ackTimeoutMs, "ACK timeout");
            if (n > 0) {
                log.warn("[S013] ⏰ Timeout 標記完成：{} 筆 PENDING → TIMEOUT/NG", n);
            }
        } catch (Exception e) {
            log.error("[S013] Timeout 批次標記發生例外：{}", e.getMessage(), e);
        }
    }

    /* **********************************************************************
     * 3) 回寫結果到 PLC + 三段式握手（寫值→打握手→清請求）
     * **********************************************************************/
    @Scheduled(fixedDelayString = "${start.monitor.writeback-fixed-delay-ms:50}")
    public void writebackResultsToPlc() {
        List<StartAccessInfo> items = repo.pickWaitingWriteback(writebackPickLimit);
        if (items == null || items.isEmpty()) return;

        for (StartAccessInfo row : items) {
            Long id = row.getId();
            String targetCode = nvl(row.getTargetCode());
            String status = nvl(row.getStatus());
            String ackResult = nvl(row.getAckResult());

            // 僅處理已決定結果者：ACK_OK/ACK_NG/TIMEOUT
            Integer code = toReturnCode(status, ackResult);
            if (code == null) {
                // 仍 PENDING / CANCELLED 等 → 跳過
                continue;
            }

            Target t = BY_CODE.get(targetCode);
            if (t == null) {
                repo.markWritebackFailed(id, "Unknown target_code: " + targetCode);
                log.error("[S013] ❌ 未知 target_code，id={}, target={}", id, targetCode);
                continue;
            }

            try {
                // 1) 回寫 ReturnCode（W003x）：1=OK, 2=NG
                if (doubleCheckBeforeWrite) {
                    int curr = readU16(t.rcAddr);
                    if (curr != code) {
                        plc.writeInt32(plcDevice, w(t.rcAddr), code);
                    }
                } else {
                    plc.writeInt32(plcDevice, w(t.rcAddr), code);
                }

                // 2) 打結果握手（W003x+1）：寫 1 → 延遲 → 寫 0
                plc.writeInt32(plcDevice, w(t.rcHsAddr), 1);
                sleepQuiet(handshakePulseMs);
                plc.writeInt32(plcDevice, w(t.rcHsAddr), 0);

                // 3) 清請求字（W103x）：寫 0
                int reqNow = readU16(t.reqAddr);
                if (reqNow != 0) {
                    plc.writeInt32(plcDevice, w(t.reqAddr), 0);
                }

                // 4) 成功結案 + 清 inflight
                repo.markWritebackSuccess(id, LocalDateTime.now());
                inflightByTarget.remove(targetCode);

                log.info("[S013] ✅ 寫回完成 target={}, rc={}, rcAddr={}, hsAddr={}，清除 reqAddr={}",
                        targetCode, code, w(t.rcAddr), w(t.rcHsAddr), w(t.reqAddr));
            } catch (Exception ex) {
                String err = ex.getClass().getSimpleName() + ": " + ex.getMessage();
                repo.markWritebackFailed(id, err);
                log.error("[S013] ❌ 寫回失敗 target={}, id={}, err={}", targetCode, id, err, ex);
            }
        }
    }

    /* ========================== 小工具 ========================== */

    /** "W%04X" 格式（例如 0x1035 -> "W1035"） */
    private String w(int addr) { return "W" + String.format("%04X", addr); }

    /** 讀 U16：底層以 int32 API 讀，再截 16 位使用 */
    private int readU16(int addr) {
        int v = plc.readInt32(plcDevice, w(addr));
        return v & 0xFFFF;
    }

    /** 將狀態/ACK 結果轉為要寫入的 ReturnCode（1=OK / 2=NG）。無法決定時回傳 null。 */
    private static Integer toReturnCode(String status, String ackResult) {
        if (StartAccessInfoRepository.STATUS_ACK_OK.equals(status) ||
                StartAccessInfoRepository.ACK_OK.equalsIgnoreCase(ackResult)) {
            return 1;
        }
        if (StartAccessInfoRepository.STATUS_ACK_NG.equals(status) ||
                StartAccessInfoRepository.STATUS_TIMEOUT.equals(status) ||
                StartAccessInfoRepository.ACK_NG.equalsIgnoreCase(ackResult)) {
            return 2;
        }
        return null;
    }

    private static String nvl(String s) { return (s == null) ? "" : s; }

    private static void sleepQuiet(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    /* ========================== in-memory 型別 ========================== */

    @Getter
    private static final class Inflight {
        private final String tid;         // 送單 TID
        private final String targetCode;  // 'WIP' / 'FSK6001A' / 'ZIPA' / 'ZIPB'
        private final int reqValue;       // 1=START / 256=RESET
        private final long sentAtMs;      // 送出時間（記錄用）

        private Inflight(String tid, String targetCode, int reqValue, long sentAtMs) {
            this.tid = tid; this.targetCode = targetCode; this.reqValue = reqValue; this.sentAtMs = sentAtMs;
        }
    }
}