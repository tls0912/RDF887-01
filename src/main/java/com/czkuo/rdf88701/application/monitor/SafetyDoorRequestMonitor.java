package com.czkuo.rdf88701.application.monitor;

import com.czkuo.rdf88701.application.service.PlcAccessService;
import com.czkuo.rdf88701.application.service.mqtt.MqttCommandService;
import com.czkuo.rdf88701.common.dto.MqttSendResult;
import com.czkuo.rdf88701.domain.repository.DoorAccessInfoRepository;
import com.czkuo.rdf88701.infra.entity.DoorAccessInfo;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SafetyDoorRequestMonitor
 *
 * 功能總覽（不省略）：
 *  1) 週期掃描 PLC 的安全門請求字 (W1020, W1022, ... W1032)：
 *     - 值=1(開門) → 發 S011
 *     - 值=2(關門) → 發 S012
 *     - 發送成功 → 以 TID + 門號 + 請求值入庫 DoorAccessInfo（狀態 PENDING，writeback=WAITING）
 *     - 使用 in-memory inflight 去重，避免同門在未結案前重送
 *
 *  2) 逾時批次：
 *     - 以 DB 批次 API：將「現在-ackTimeoutMs 之前建立且仍 PENDING」標記為 TIMEOUT 並視同 NG
 *     - 逾時後 writeback_status 維持 WAITING，等待回寫 PLC
 *
 *  3) 回寫結果到 PLC（含握手）：
 *     - 週期撈取 writeback_status=WAITING 的列（建議依建立時間先來先寫）
 *     - 若狀態=ACK_OK → W002x 回寫 1；若狀態=ACK_NG/TIMEOUT → 回寫 2
 *     - 結果握手 (W002x+1) 打 1 再清 0（80ms）
 *     - 清除對應請求字 W102x = 0（避免現場保持請求）
 *     - 成功後標記 WRITTEN + 記錄 written_at；失敗則 FAILED + last_error
 *
 *  4) in-memory inflight 清理：
 *     - 當看到 PLC 的請求字變回 0，就同步移除 inflight（避免卡住）
 *
 * 設計重點：
 *  - 不用 properties 做對映表，全部用位址計算式
 *  - DB 是唯一真相（ACK/逾時皆寫入 DB，再由本類回寫 PLC）
 *  - 三段式補償：寫結果→打握手→清請求；任何一步失敗，該列會被標記 FAILED 以供重試/人工介入
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SafetyDoorRequestMonitor {

    /* ====== 依賴 ====== */
    private final PlcAccessService plc;
    private final MqttCommandService mqtt;
    private final DoorAccessInfoRepository doorRepo;

    /* ====== 可調參數（yml 覆寫） ====== */

    /** 對方系統（S011/S012 送往哪邊） */
    @Value("${door.mqtt.target-system:ASE}")
    private String targetSystem;

    /** PLC 裝置名稱 */
    @Value("${door.mqtt.device-name:PLC-Main}")
    private String plcDevice;

    /** 監控門數（W1020 起算） */
    @Value("${door.count:10}")
    private int doorCount;

    /** 逾時毫秒（PENDING 超過此時間視為逾時） */
    @Value("${door.monitor.ack-timeout-ms:15000}")
    private long ackTimeoutMs;

    /** 每次寫回最多處理幾筆（避免長時間佔用） */
    @Value("${door.monitor.writeback-pick-limit:20}")
    private int writebackPickLimit;

    /** 握手打 1 → 清 0 的延遲毫秒 */
    @Value("${door.monitor.handshake-pulse-ms:80}")
    private long handshakePulseMs;

    /** 每回合寫回之前，先讀結果字確認是否已是正確值（避免重複寫） */
    @Value("${door.monitor.double-check-before-write:true}")
    private boolean doubleCheckBeforeWrite;

    /* ====== in-memory inflight：避免同門重送 ====== */
    private final Map<Integer, Inflight> inflightByDoor = new ConcurrentHashMap<>();

    /* **********************************************************************
     * 1) 掃描 PLC 請求字 → 成功則發 S011/S012 + 入庫 PENDING
     * **********************************************************************/

    /**
     * 每 200ms 掃描一次請求字（W1020, W1022, ...）。
     *  - 值==0 → 清掉 inflight（如仍在表內）
     *  - 值==1/2 且不在 inflight → 送 S011/S012；成功 → repo.savePending(tid,door,req)；加入 inflight
     */
    @Scheduled(fixedDelayString = "${door.monitor.fixed-rate-ms:400}")
    public void pollDoorRequests() {
        for (int door = 1; door <= doorCount; door++) {
            int reqAddr = reqWord(door);
            int reqVal = readU16(reqAddr);

            if (reqVal == 0) {
                // 現場已清除請求 → 我方同步清 in-flight
                inflightByDoor.remove(door);
                continue;
            }
            if (reqVal != 1 && reqVal != 2) {
                // 非法或未定義值，不處理
                continue;
            }
            if (inflightByDoor.containsKey(door)) {
                // 這扇門尚有未結案的請求，避免重送
                continue;
            }

            String doorName   = doorNameOf(door);
            String deviceName = deviceNameOf(doorName);

            // 發 S011(開門)/S012(關門)
            MqttSendResult send = (reqVal == 1)
                    ? mqtt.sendS011(targetSystem, deviceName, doorName)
                    : mqtt.sendS012(targetSystem, deviceName, doorName);

            if (!send.isSuccess()) {
                log.error("[Door] 發送 {} 失敗 door={}, err={}",
                        (reqVal == 1 ? "S011" : "S012"), door, send.getMessage());
                continue;
            }

            String tid = send.getTid();
            boolean saved = doorRepo.savePending(tid, door, reqVal);
            if (!saved) {
                log.warn("[Door] savePending 失敗（可能 DB 問題），door={}, tid={}, req={}", door, tid, reqVal);
                // 仍把 inflight 放上，避免短時間內重送淹死對方；等 REQ=0 或逾時/回寫後自然回收
            }

            inflightByDoor.put(door, new Inflight(tid, door, reqVal, Instant.now().toEpochMilli()));
            log.info("[Door] ▶️ 已送 {}，door={}({}/{})，req={}，tid={}",
                    (reqVal == 1 ? "S011" : "S012"),
                    door, deviceName, doorName, reqVal, tid);

        }
    }

    /* **********************************************************************
     * 2) 逾時批次：將久未回覆的 PENDING → TIMEOUT/NG（交給寫回任務）
     * **********************************************************************/

    /**
     * 每 1 秒執行一次逾時檢查。
     * - repo 會把「現在-ackTimeoutMs 之前仍 PENDING」的列，標成 TIMEOUT 並視同 NG，
     *   且保持 writeback_status=WAITING，讓寫回流程去回寫 2(NG) 到 PLC。
     */
    @Scheduled(fixedDelayString = "${door.monitor.timeout-scan-ms:1000}")
    public void markTimeoutsBatch() {
        try {
            int n = doorRepo.markTimeoutAsNg(ackTimeoutMs, "ACK timeout");
            if (n > 0) {
                log.warn("[Door] ⏰ Timeout 標記完成：{} 筆 PENDING → TIMEOUT/NG", n);
            }
        } catch (Exception e) {
            log.error("[Door] Timeout 批次標記發生例外：{}", e.getMessage(), e);
        }
    }

    /* **********************************************************************
     * 3) 回寫結果到 PLC + 三段式握手補償（寫值→打握手→清請求）
     * **********************************************************************/

    /**
     * 每 150ms 撈取 writeback=WAITING 的列，將結果寫回 PLC，並做握手：
     *  - 結果碼：ACK_OK → 1；ACK_NG/TIMEOUT → 2；其它狀態跳過（如仍 PENDING／CANCELLED）
     *  - 寫 W002x 結果 → 打 W002x+1（寫 1）→ 延遲 → 清 W002x+1（寫 0）
     *  - 清 W102x 請求字（寫 0）
     *  - 成功 → repo.markWritebackSuccess(id, now)；失敗 → repo.markWritebackFailed(id, err)
     */
    @Scheduled(fixedDelayString = "${door.monitor.writeback-fixed-delay-ms:150}")
    public void writebackResultsToPlc() {
        List<DoorAccessInfo> items = doorRepo.pickWaitingWriteback(writebackPickLimit);
        if (items == null || items.isEmpty()) return;

        for (DoorAccessInfo row : items) {
            Long id = row.getId();
            Integer door = row.getDoorNo();
            String status = nullToEmpty(row.getStatus());
            String ackResult = nullToEmpty(row.getAckResult());

            // 僅處理已決定結果者：ACK_OK/ACK_NG/TIMEOUT
            Integer code = toResultCode(status, ackResult);
            if (code == null) {
                // 例如仍是 PENDING 或 CANCELLED → 略過（保持 WAITING 或由其他流程處理）
                continue;
            }

            try {
                // 1) 回寫 ReturnCode（W002x）：1=OK, 2=NG
                int rcAddr = resultWord(door);
                if (doubleCheckBeforeWrite) {
                    int curr = readU16(rcAddr);
                    if (curr != code) {
                        plc.writeInt32(plcDevice, w(rcAddr), code);
                    }
                } else {
                    plc.writeInt32(plcDevice, w(rcAddr), code);
                }

                // 2) 打結果握手（W002x+1）：寫 1 → 等 → 寫 0
                int hsAddr = resultHandshakeWord(door);
                plc.writeInt32(plcDevice, w(hsAddr), 1);
                sleepQuiet(handshakePulseMs);
                plc.writeInt32(plcDevice, w(hsAddr), 0);

                // 3) 清請求字（W102x）：寫 0（避免現場一直保持請求）
                int reqAddr = reqWord(door);
                int reqVal = readU16(reqAddr);
                if (reqVal != 0) {
                    plc.writeInt32(plcDevice, w(reqAddr), 0);
                }

                // 4) 成功結案（寫 PLC 完成時間）
                doorRepo.markWritebackSuccess(id, LocalDateTime.now());
                // 同步清除 inflight
                inflightByDoor.remove(door);

                log.info("[Door] ✅ 寫回完成 door={}, rc={}, rcAddr={}, hsAddr={}，清除 reqAddr={}",
                        door, code, w(rcAddr), w(hsAddr), w(reqAddr));
            } catch (Exception ex) {
                String err = ex.getClass().getSimpleName() + ": " + ex.getMessage();
                doorRepo.markWritebackFailed(id, err);
                log.error("[Door] ❌ 寫回失敗 door={}, id={}, err={}", door, id, err, ex);
            }
        }
    }

    /* ========================== 位址/PLC 小工具 ========================== */

    /** "W%04X" 格式（例如 0x1020 -> "W1020"） */
    private static String w(int addr) { return "W" + String.format("%04X", addr); }

    /** Request word：W1020, W1022, ...（door=1 對應 W1020） */
    private static int reqWord(int door) { return 0x1020 + (door - 1) * 2; }

    /** Result word：W0020, W0022, ...（1:OK, 2:NG） */
    private static int resultWord(int door) { return 0x0020 + (door - 1) * 2; }

    /** Result handshake：W0021, W0023, ...（結果握手，打 1 → 清 0） */
    private static int resultHandshakeWord(int door) { return resultWord(door) + 1; }

    /** 讀 U16：底層以 int32 API 讀，再截 16 位使用 */
    private int readU16(int addr) {
        int v = plc.readInt32(plcDevice, w(addr));
        return v & 0xFFFF;
    }

    /** 將狀態/ACK 結果轉為要寫入的 ReturnCode（1=OK / 2=NG）。無法決定時回傳 null。 */
    private static Integer toResultCode(String status, String ackResult) {
        // ACK_OK → 1
        if (DoorAccessInfoRepository.STATUS_ACK_OK.equals(status) ||
                DoorAccessInfoRepository.ACK_OK.equalsIgnoreCase(ackResult)) {
            return 1;
        }
        // ACK_NG / TIMEOUT → 2
        if (DoorAccessInfoRepository.STATUS_ACK_NG.equals(status) ||
                DoorAccessInfoRepository.STATUS_TIMEOUT.equals(status) ||
                DoorAccessInfoRepository.ACK_NG.equalsIgnoreCase(ackResult)) {
            return 2;
        }
        // 其他狀態（PENDING/CANCELLED）→ 尚未可寫
        return null;
    }

    private static String nullToEmpty(String s) { return (s == null) ? "" : s; }

    private static void sleepQuiet(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    /* ========================== in-memory 型別 ========================== */

    @Getter
    private static final class Inflight {
        private final String tid;     // 送單 TID
        private final int doorNo;     // 門號
        private final int reqValue;   // 1=open / 2=close
        private final long sentAtMs;  // 送出時間（記錄用）

        private Inflight(String tid, int doorNo, int reqValue, long sentAtMs) {
            this.tid = tid; this.doorNo = doorNo; this.reqValue = reqValue; this.sentAtMs = sentAtMs;
        }
    }

    // ======== 門號/門名與 DEVICE_NAME 推導 ========

    /** 1..9 號門固定順序名稱（索引 0 保留不用） */
    private static final String[] DOOR_NAMES = {
            "", // 占位：讓 doorNo 可直接當索引
            "Crane操作側",   // 1
            "Crane維修側",   // 2
            "ZIPA維修門",    // 3
            "ZIPB維修門",    // 4
            "拆併區_維修門",  // 5
            "拆併區_打帶1",   // 6
            "拆併區_打帶2",   // 7
            "拆併區_打帶3",   // 8
            "拆併區_貼標"    // 9
    };

    /** 依門號取門名；超出範圍時回傳 "UNKNOWN" */
    private static String doorNameOf(int doorNo) {
        if (doorNo >= 1 && doorNo < DOOR_NAMES.length) return DOOR_NAMES[doorNo];
        return "UNKNOWN";
    }

    /** 由門名推導 DEVICE_NAME */
    private static String deviceNameOf(String doorName) {
        if (doorName == null) return "WIP";
        if (doorName.startsWith("ZIPA") || doorName.contains("ZIPA")) return "ZIPA";
        if (doorName.startsWith("ZIPB") || doorName.contains("ZIPB")) return "ZIPB";
        if (doorName.startsWith("拆併區") || doorName.contains("拆併區")) return "拆併區";
        if (doorName.startsWith("Crane") || doorName.contains("Crane")) return "WIP";
        return "WIP";
    }

}
