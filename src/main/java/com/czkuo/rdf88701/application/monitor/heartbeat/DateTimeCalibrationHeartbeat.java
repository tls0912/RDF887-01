package com.czkuo.rdf88701.application.monitor.heartbeat;

import com.czkuo.rdf88701.application.service.PlcAccessService;
import com.czkuo.rdf88701.infra.adapter.plc.connection.PlcClientManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Date/Time Calibration Heartbeat
 * ------------------------------------------------------------
 * 將系統時間以 BCD 寫入：
 * W0000: Year(YY) / Month
 * W0001: Day / Hour
 * W0002: Minute / Second
 * 並用 B0008(REQ) / B0608(ACK) 做交握。
 * <p>
 * 流程：
 * 1) 寫入 W0000~W0002 時間 → REQ=1
 * 2) 等 ACK=1 → REQ=0
 * 3) 等 ACK=0 → 本次完成，等待 period 後再觸發下一次
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DateTimeCalibrationHeartbeat {

    private final PlcAccessService plc;
    private final PlcClientManager clientManager;

    // ===== 固定位址與預設參數（不靠 config）=====
    private static final String LCS_READY = "B0000";
    private static final String WORDS_BASE = "W0000";    // 連續寫 3 words：年/月、日/時、分/秒（皆 BCD）
    private static final String REQ_BIT = "B0008";    // Date/Time Calibration Req (PC→PLC)
    private static final String ACK_BIT = "B0608";    // Date/Time Calibration Ack (PLC→PC)

    private static final ZoneId ZONE = ZoneId.of("Asia/Taipei");
    private static final long PERIOD_MS = 1000; // 成功一次後等待 1s 再觸發
    private static final long ACK_HIGH_TIMEOUT_MS = 800;  // 等 ACK↑ 超時，則重送
    private static final long ACK_LOW_TIMEOUT_MS = 4000; // 等 ACK↓ 超時，維持 REQ=0 繼續等
    private static final long TICK_MS = 120;  // 排程輪詢間隔

    // ===== 每裝置的握手狀態 =====
    private enum Phase {IDLE, WAIT_ACK_HIGH, WAIT_ACK_LOW}

    private final Map<String, Phase> phase = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSuccessMs = new ConcurrentHashMap<>();
    private final Map<String, Long> phaseStartMs = new ConcurrentHashMap<>();

    @Scheduled(fixedDelay = TICK_MS)
    public void run() {
        // 只針對「已初始化」的裝置名稱；未連線時自動跳過
        Set<String> targets = Set.copyOf(clientManager.getAllDeviceNames());
        if (targets.isEmpty()) return;

        long now = System.currentTimeMillis();

        for (String device : targets) {
            try {
                // 僅在「實際物理連線」時才進行握手；斷線時回到 IDLE
                if (!clientManager.isActuallyConnected(device)) {
                    phase.put(device, Phase.IDLE);
                    continue;
                }

                boolean isReady = plc.readBoolean(device, LCS_READY);
                if (!isReady) {
                    plc.writeBoolean(device, LCS_READY, true);
                }

                Phase p = phase.getOrDefault(device, Phase.IDLE);
                switch (p) {
                    case IDLE -> {
                        long lastOk = lastSuccessMs.getOrDefault(device, 0L);
                        if (now - lastOk < PERIOD_MS) break;

                        // 起手若 ACK 仍是高，先把 REQ 壓低等待清邊緣
                        if (readAckSafe(device)) {
                            writeReq(device, false);
                            break;
                        }

                        // 寫入目前時間（BCD）→ REQ=1
                        writeCurrentTime(device);
                        writeReq(device, true);

                        phase.put(device, Phase.WAIT_ACK_HIGH);
                        phaseStartMs.put(device, now);
                        if (log.isDebugEnabled()) log.debug("[DT-HB:{}] REQ↑，等待 ACK↑", device);
                    }

                    case WAIT_ACK_HIGH -> {
                        if (readAckSafe(device)) {
                            // ACK↑ → REQ=0，等 ACK↓
                            writeReq(device, false);
                            phase.put(device, Phase.WAIT_ACK_LOW);
                            phaseStartMs.put(device, now);
                            if (log.isDebugEnabled()) log.debug("[DT-HB:{}] ACK↑，REQ↓，等待 ACK↓", device);
                        } else if (now - phaseStartMs.getOrDefault(device, now) > ACK_HIGH_TIMEOUT_MS) {
                            // 超時重送：REQ 0→1 並重寫時間
                            log.warn("[DT-HB:{}] 等待 ACK↑ 超時 {}ms，重送", device, ACK_HIGH_TIMEOUT_MS);
                            writeReq(device, false);
                            writeCurrentTime(device);
                            writeReq(device, true);
                            phaseStartMs.put(device, now);
                        }
                    }

                    case WAIT_ACK_LOW -> {
                        if (!readAckSafe(device)) {
                            // 完成一次握手 → 記錄成功時間，回 IDLE 等 1 秒
                            lastSuccessMs.put(device, now);
                            phase.put(device, Phase.IDLE);
                            if (log.isDebugEnabled()) log.debug("[DT-HB:{}] ACK↓，完成一次心跳", device);
                        } else if (now - phaseStartMs.getOrDefault(device, now) > ACK_LOW_TIMEOUT_MS) {
                            // 超時：維持 REQ=0，持續等待 PLC 拉低 ACK
                            log.warn("[DT-HB:{}] 等待 ACK↓ 超時 {}ms，維持 REQ=0", device, ACK_LOW_TIMEOUT_MS);
                            writeReq(device, false);
                            phaseStartMs.put(device, now);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[DT-HB:{}] 例外：{}", device, e.toString(), e);
                phase.put(device, Phase.IDLE); // 出錯時回到 IDLE，避免卡死
            }
        }
    }

    // ===== IO =====

    private void writeCurrentTime(String device) {
        LocalDateTime t = LocalDateTime.now(ZONE);
        int yy = t.getYear() % 100;  // 00..99
        int mm = t.getMonthValue();  // 01..12
        int dd = t.getDayOfMonth();  // 01..31
        int hh = t.getHour();        // 00..23
        int mi = t.getMinute();      // 00..59
        int ss = t.getSecond();      // 00..59

        int w0 = (bcd(yy) << 8) | bcd(mm); // Year / Month
        int w1 = (bcd(dd) << 8) | bcd(hh); // Day / Hour
        int w2 = (bcd(mi) << 8) | bcd(ss); // Min / Sec

        byte[] buf = new byte[6];
        putWordLE(buf, 0, w0);
        putWordLE(buf, 2, w1);
        putWordLE(buf, 4, w2);

        plc.writeBytes(device, WORDS_BASE, buf); // 連續寫入 W0000~W0002
    }

    private void writeReq(String device, boolean v) {
        plc.writeBoolean(device, REQ_BIT, v);
    }

    private boolean readAckSafe(String device) {
        try {
            return plc.readBoolean(device, ACK_BIT);
        } catch (Throwable t) {
            // 若 PlcAccessService 尚未提供 readBoolean，這裡會回 false（握手會停在等待 ACK↑）
            log.warn("[DT-HB:{}] 讀取 ACK 失敗，視為 false：{}", device, t.toString());
            return false;
        }
    }

    // ===== 小工具 =====

    /**
     * 將 0..99 轉成一個位元組的 BCD（十位高 nibble、個位低 nibble）
     */
    private static int bcd(int x) {
        int tens = (x / 10) % 10, ones = x % 10;
        return ((tens & 0xF) << 4) | (ones & 0xF);
    }

    /**
     * Little-Endian：低位元組在前（與你現有 encoder 一致）
     */
    private static void putWordLE(byte[] buf, int idx, int word) {
        buf[idx] = (byte) (word & 0xFF);
        buf[idx + 1] = (byte) ((word >> 8) & 0xFF);
    }
}
