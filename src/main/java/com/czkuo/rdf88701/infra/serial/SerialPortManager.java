package com.czkuo.rdf88701.infra.serial;

import com.czkuo.rdf88701.config.serial.SerialProperties;
import com.czkuo.rdf88701.infra.event.model.serial.SerialFrameEvent;
import com.fazecast.jSerialComm.SerialPort;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 序列埠集中管理器。
 *
 * <p>依 serial 設定開啟多個 port，使用 jSerialComm data listener 事件驅動讀取資料，
 * 支援 LINE、STX_ETX、FIXED 分包，切出 frame 後發布 SerialFrameEvent，並在斷線時自動重連。</p>
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SerialPortManager {

    private final SerialProperties props;
    private final ApplicationEventPublisher publisher;

    /** alias -> context */
    private final Map<String, PortCtx> byAlias = new ConcurrentHashMap<>();

    /** 事件觸發後的「湊包」等待，避免只讀到 1~2 bytes（模擬 C# Sleep(100) 的概念） */
    private static final int STABILIZE_SLEEP_MS = 20;   // 每回合小等 20ms
    private static final int STABILIZE_LOOPS    = 5;    // 最多等 5 回合 ≈ 100ms
    private static final int MAX_READ_CHUNK     = 4096; // 單次最大讀取量（避免一次性大陣列）

    // ===== Lifecycle =====
    @PostConstruct
    public void start() {
        if (!props.isEnabled()) {
            log.info("[Serial] disabled");
            return;
        }
        for (SerialProperties.Port p : props.getPorts()) {
            createAndOpenContext(p);
        }
        log.info("[Serial] ready aliases: {}", byAlias.keySet());
    }

    @PreDestroy
    public void stop() {
        byAlias.values().forEach(this::safeClose);
        byAlias.clear();
    }

    // ===== Public API =====

    /** 發 ASCII（會依原字串寫出，是否附加 CR/LF 由呼叫端決定） */
    public boolean sendAscii(String alias, String data) {
        PortCtx ctx = byAlias.get(alias);
        if (ctx == null) {
            log.warn("[Serial] alias {} not found", alias);
            return false;
        }
        return write(ctx, data.getBytes(StandardCharsets.US_ASCII));
    }

    /** 發 HEX 字串（允許含空白；會去空白後每兩碼一個 byte） */
    public boolean sendHex(String alias, String hexNoSpacesOrWith) {
        PortCtx ctx = byAlias.get(alias);
        if (ctx == null) {
            log.warn("[Serial] alias {} not found", alias);
            return false;
        }
        return write(ctx, hexToBytes(hexNoSpacesOrWith));
    }

    /** 發原始位元組 */
    public boolean sendRaw(String alias, byte[] bytes) {
        PortCtx ctx = byAlias.get(alias);
        if (ctx == null) {
            log.warn("[Serial] alias {} not found", alias);
            return false;
        }
        return write(ctx, bytes);
    }

    /** 取得目前開啟列表（alias 集合） */
    public Set<String> aliases() {
        return Collections.unmodifiableSet(byAlias.keySet());
    }

    // ===== Core (open, listener, reconnect) =====

    /** 依 port 設定建立 context 並嘗試開啟 */
    private void createAndOpenContext(SerialProperties.Port p) {
        String alias = Optional.ofNullable(p.getAlias()).orElse(p.getId());
        PortCtx ctx = new PortCtx(alias, p);
        byAlias.put(alias, ctx);
        openWithListener(ctx);
    }

    /** 嘗試開啟並掛上 DataListener；失敗則啟動重連 */
    private void openWithListener(PortCtx ctx) {
        try {
            SerialPort sp = configureAndOpen(ctx.config);
            if (sp == null) {
                log.warn("[Serial] {} open failed, will reconnect", ctx.alias);
                scheduleReconnect(ctx);
                return;
            }
            ctx.replacePort(sp);
            attachDataAndDisconnectListener(ctx);
            log.info("[Serial] {} open OK", ctx.alias);
        } catch (Exception e) {
            log.error("[Serial] {} open exception: {}", ctx.alias, e.getMessage(), e);
            scheduleReconnect(ctx);
        }
    }

    /** 依 config 設定 SerialPort 並 open；失敗回傳 null */
    private SerialPort configureAndOpen(SerialProperties.Port p) {
        SerialPort sp = SerialPort.getCommPort(p.getId());
        sp.setComPortParameters(
                or(p.getBaudRate(), props.getDefaults().getBaudRate()),
                or(p.getDataBits(),  props.getDefaults().getDataBits()),
                stopBits(or(p.getStopBits(), props.getDefaults().getStopBits())),
                parity(or(p.getParity(), props.getDefaults().getParity()))
        );
        sp.setFlowControl(flow(or(p.getFlowControl(), props.getDefaults().getFlowControl())));

        // 事件驅動：使用 NONBLOCKING（回呼裡不做阻塞式 read）
        sp.setComPortTimeouts(
                SerialPort.TIMEOUT_NONBLOCKING,
                0, // read timeout（不用）
                or(p.getWriteTimeoutMs(), props.getDefaults().getWriteTimeoutMs())
        );

        // DTR/RTS：某些讀卡機/轉接頭需要 DTR/RTS 拉高才會供電或啟動傳訊
        boolean dtr = or(p.getDtr(), props.getDefaults().isDtr());
        if (dtr) sp.setDTR(); else sp.clearDTR();
        boolean rts = or(p.getRts(), props.getDefaults().isRts());
        if (rts) sp.setRTS(); else sp.clearRTS();

        if (!sp.openPort()) return null;
        return sp;
    }

    /** 同時監聽「資料可讀」與「斷線」兩種事件 */
    private void attachDataAndDisconnectListener(PortCtx ctx) {
        SerialPort sp = ctx.port();
        sp.addDataListener(new com.fazecast.jSerialComm.SerialPortDataListener() {
            @Override public int getListeningEvents() {
                return SerialPort.LISTENING_EVENT_DATA_AVAILABLE
                        | SerialPort.LISTENING_EVENT_PORT_DISCONNECTED;
            }
            @Override public void serialEvent(com.fazecast.jSerialComm.SerialPortEvent ev) {
                int t = ev.getEventType();
                if (t == SerialPort.LISTENING_EVENT_PORT_DISCONNECTED) {
                    log.warn("[Serial] {} PORT_DISCONNECTED", ctx.alias);
                    onDisconnected(ctx);
                } else if (t == SerialPort.LISTENING_EVENT_DATA_AVAILABLE) {
                    onDataAvailable(ctx); // 有資料 → 抽乾 → 切帧 → 發布事件
                }
            }
        });
    }

    /** 有資料事件：讓資料「湊齊」後，一口氣抽乾緩衝、切帧、發布事件 */
    private void onDataAvailable(PortCtx ctx) {
        SerialPort sp = ctx.port();
        if (sp == null || !sp.isOpen()) return;

        try {
            // 等待資料穩定（連續迭代直到 bytesAvailable 不再增加或達到上限）
            int last = sp.bytesAvailable();
            if (last < 0) return; // -1 表示非正常狀態
            for (int i = 0; i < STABILIZE_LOOPS; i++) {
                try { Thread.sleep(STABILIZE_SLEEP_MS); } catch (InterruptedException ignored) {}
                int now = sp.bytesAvailable();
                if (now < 0) return;
                if (now == last) break; // 不再增加，穩定
                last = now;
            }

            // 把 OS 緩衝「抽乾」；以 MAX_READ_CHUNK 分段讀，避免一次性配置過大
            int remaining = sp.bytesAvailable();
            if (remaining <= 0) return;

            while (remaining > 0) {
                int toRead = Math.min(remaining, MAX_READ_CHUNK);
                byte[] buf = new byte[toRead];
                int n = sp.readBytes(buf, toRead);
                if (n <= 0) break;               // 沒讀到或錯誤就結束本次
                if (n < toRead) buf = Arrays.copyOf(buf, n); // 精確裁切

                // 丟給封包累積器；只有到 delimiter / STX-ETX / 固定長才會切出完整 frame
                for (byte[] frame : ctx.acc.feed(buf)) {
                    publisher.publishEvent(new SerialFrameEvent(this, ctx.alias, frame));
                }
                remaining -= n;
            }
        } catch (Exception e) {
            // 讀取異常：記 Log；實際斷線會觸發 PORT_DISCONNECTED 並走重連
            log.warn("[Serial] {} data read error: {}", ctx.alias, e.toString());
        }
    }

    /** 統一斷線處理：標記 → 關閉 → 啟動重連 */
    private void onDisconnected(PortCtx ctx) {
        if (!ctx.markDisconnected()) return; // 已在重連中
        safeClose(ctx);
        scheduleReconnect(ctx);
    }

    /** 指數退避重連（直到成功或被停止） */
    private void scheduleReconnect(PortCtx ctx) {
        SerialProperties.Reconnect rc = props.getReconnect();
        if (rc == null || !rc.isEnabled()) return;
        ctx.startReconnectLoop((attempt) -> {
            if (ctx.stopped) return false;
            SerialPort sp = configureAndOpen(ctx.config);
            if (sp != null) {
                ctx.replacePort(sp);
                attachDataAndDisconnectListener(ctx);
                log.info("[Serial] {} reconnected (attempt #{})", ctx.alias, attempt);
                return true;
            }
            return false;
        }, rc);
    }

    /** 關閉埠並移除 listener */
    private void safeClose(PortCtx ctx) {
        try {
            SerialPort sp = ctx.port();
            if (sp != null) {
                try { sp.removeDataListener(); } catch (Exception ignored) {}
                if (sp.isOpen()) sp.closePort();
            }
        } catch (Exception ignored) {}
        log.info("[Serial] closed {}", ctx.alias);
    }

    /** 寫出共用方法 */
    private boolean write(PortCtx ctx, byte[] data) {
        try {
            SerialPort sp = ctx.port();
            if (sp == null || !sp.isOpen()) {
                log.warn("[Serial] {} not open", ctx.alias);
                return false;
            }
            int n = sp.writeBytes(data, data.length);
            return n == data.length;
        } catch (Exception e) {
            log.error("[Serial] write error alias={}", ctx.alias, e);
            return false;
        }
    }

    // ===== Helpers =====

    /** stop bits：1 or 2 */
    private static int stopBits(int sb) {
        return sb == 2 ? SerialPort.TWO_STOP_BITS : SerialPort.ONE_STOP_BIT;
    }

    /** parity：NONE/EVEN/ODD/MARK/SPACE */
    private static int parity(String p) {
        String s = p == null ? "NONE" : p.toUpperCase(Locale.ROOT);
        return switch (s) {
            case "EVEN" -> SerialPort.EVEN_PARITY;
            case "ODD" -> SerialPort.ODD_PARITY;
            case "MARK" -> SerialPort.MARK_PARITY;
            case "SPACE" -> SerialPort.SPACE_PARITY;
            default -> SerialPort.NO_PARITY;
        };
    }

    /** flow control：NONE/RTS_CTS/XON_XOFF */
    private static int flow(String f) {
        String s = f == null ? "NONE" : f.toUpperCase(Locale.ROOT);
        return switch (s) {
            case "RTS_CTS" -> SerialPort.FLOW_CONTROL_RTS_ENABLED | SerialPort.FLOW_CONTROL_CTS_ENABLED;
            case "XON_XOFF" -> SerialPort.FLOW_CONTROL_XONXOFF_IN_ENABLED | SerialPort.FLOW_CONTROL_XONXOFF_OUT_ENABLED;
            default -> SerialPort.FLOW_CONTROL_DISABLED;
        };
    }

    private static int or(Integer v, int d) { return v != null ? v : d; }
    private static boolean or(Boolean v, boolean d) { return v != null ? v : d; }
    private static String or(String v, String d) { return v != null ? v : d; }

    /** "01 02 0A" / "01020A" → byte[] */
    private static byte[] hexToBytes(String s) {
        String clean = s.replaceAll("\\s+", "");
        int len = clean.length();
        if ((len & 1) == 1) throw new IllegalArgumentException("HEX length must be even");
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) Integer.parseInt(clean.substring(i, i + 2), 16);
        }
        return out;
    }

    // ===== Port Context =====

    /** 每個 alias 對應一個 PortCtx（含 SerialPort 與累積器） */
    private static class PortCtx {
        final String alias;
        final SerialProperties.Port config;
        final FrameAccumulator acc;

        private volatile SerialPort port;
        private volatile boolean reconnecting = false;
        private volatile boolean stopped = false;

        PortCtx(String alias, SerialProperties.Port cfg) {
            this.alias = alias;
            this.config = cfg;
            this.acc = new FrameAccumulator(cfg);
        }

        SerialPort port() { return port; }
        void replacePort(SerialPort sp) { this.port = sp; this.reconnecting = false; }

        boolean markDisconnected() {
            if (reconnecting) return false;
            reconnecting = true;
            return true;
        }

        /** 重連背景執行緒（指數退避 + 抖動） */
        void startReconnectLoop(ReconnectTry tryOpen, SerialProperties.Reconnect conf) {
            new Thread(() -> {
                int attempt = 0;
                long delay = conf.getInitialDelayMs();
                Random rnd = new Random();
                while (!stopped) {
                    attempt++;
                    try {
                        Thread.sleep(delay + rnd.nextInt(Math.max(1, conf.getJitterMs())));
                    } catch (InterruptedException ignored) {}
                    if (tryOpen.apply(attempt)) return; // 成功
                    delay = Math.min((long) (delay * conf.getMultiplier()), conf.getMaxDelayMs());
                    log.info("[Serial] {} reconnect failed, retry in {} ms (#{})", alias, delay, attempt);
                }
            }, "serial-reconnect-" + alias).start();
        }

        interface ReconnectTry { boolean apply(int attempt); }
    }

    // ===== Frame Accumulator =====
    /**
     * 小型封包累積器：
     * - LINE：以 delimiter（如 "\n"、"\r\n"）切包（會去掉分隔符）
     * - STX_ETX：以 STX/ETX（hex）切包（會去掉 STX/ETX）
     * - FIXED：固定長度
     *
     * 註：
     * - feed() 可多次餵入碎片；只有當累積到完整封包才會回傳 frame。
     * - STX_ETX 模式：避免「橫跨兩次 read」時把前一段的尾巴清掉，會保留 stx.length-1 的尾巴。
     */
    private static class FrameAccumulator {
        private final Mode mode;
        private final byte[] delim;       // LINE
        private final byte[] stx;         // STX_ETX
        private final byte[] etx;         // STX_ETX
        private final int fixedLen;       // FIXED
        private final List<Byte> buf = new ArrayList<>();

        enum Mode { LINE, STX_ETX, FIXED }

        FrameAccumulator(SerialProperties.Port p) {
            String proto = Optional.ofNullable(p.getProtocol()).orElse("LINE").toUpperCase(Locale.ROOT);
            mode = Mode.valueOf(proto);
            if (mode == Mode.LINE) {
                // 預設 CRLF；若你的讀卡機僅 LF，請在 yml 設 delimiter: "\n"
                String d = Optional.ofNullable(p.getDelimiter()).orElse("\r\n");
                delim = d.getBytes(StandardCharsets.US_ASCII);
                stx = etx = null; fixedLen = -1;
            } else if (mode == Mode.STX_ETX) {
                String stxHex = Optional.ofNullable(p.getStx()).orElse("02");
                String etxHex = Optional.ofNullable(p.getEtx()).orElse("03");
                stx = hexToBytes(stxHex);
                etx = hexToBytes(etxHex);
                delim = null; fixedLen = -1;
            } else { // FIXED
                fixedLen = Optional.ofNullable(p.getFrameLength()).orElse(0);
                if (fixedLen <= 0) throw new IllegalArgumentException("FIXED requires frameLength > 0");
                delim = null; stx = etx = null;
            }
        }

        /** 餵入新資料，回傳「本次因為足夠而切出」的完整封包清單 */
        List<byte[]> feed(byte[] incoming) {
            for (byte b : incoming) buf.add(b);
            List<byte[]> frames = new ArrayList<>();
            switch (mode) {
                case LINE -> {
                    while (true) {
                        int idx = indexOf(buf, delim, 0);
                        if (idx < 0) break;
                        byte[] frame = pop(buf, idx + delim.length);
                        frames.add(Arrays.copyOf(frame, frame.length - delim.length)); // 去分隔符
                    }
                }
                case STX_ETX -> {
                    final int noiseLimit = 4096; // 防止噪音把緩衝撐爆
                    while (true) {
                        int s = indexOf(buf, stx, 0);
                        if (s < 0) {
                            // 不要整包清掉；保留最多 stx.length-1 個尾巴，避免 STX 橫跨兩次 read 被清掉
                            int keep = Math.max(0, stx.length - 1);
                            int remove = Math.max(0, buf.size() - keep);
                            for (int i = 0; i < remove; i++) buf.remove(0);
                            if (buf.size() > noiseLimit) buf.clear();
                            break;
                        }
                        int e = indexOf(buf, etx, s + stx.length);
                        if (e < 0) break; // 等下一次補齊
                        byte[] frame = slice(buf, s + stx.length, e); // 去掉 STX/ETX
                        pop(buf, e + etx.length); // 丟掉已處理
                        frames.add(frame);
                    }
                }
                case FIXED -> {
                    while (buf.size() >= fixedLen) {
                        frames.add(pop(buf, fixedLen));
                    }
                }
            }
            return frames;
        }

        private static int indexOf(List<Byte> src, byte[] pat, int from) {
            outer:
            for (int i = from; i <= src.size() - pat.length; i++) {
                for (int j = 0; j < pat.length; j++) {
                    if (src.get(i + j) != pat[j]) continue outer;
                }
                return i;
            }
            return -1;
        }

        private static byte[] pop(List<Byte> src, int n) {
            byte[] out = new byte[n];
            for (int i = 0; i < n; i++) out[i] = src.remove(0);
            return out;
        }

        private static byte[] slice(List<Byte> src, int from, int toExcl) {
            byte[] out = new byte[toExcl - from];
            for (int i = from; i < toExcl; i++) out[i - from] = src.get(i);
            return out;
        }
    }
}
