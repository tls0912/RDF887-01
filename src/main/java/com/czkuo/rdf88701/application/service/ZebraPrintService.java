package com.czkuo.rdf88701.application.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ZebraPrintService {

    public record PrintResult(boolean ok, String message) {}

    private static final byte STX = 0x02; // <STX>
    private static final byte ETX = 0x03; // <ETX>

    /** 若 ZPL 未宣告 UTF-8（^CI28），在第一個 ^XA 後自動補上；其餘內容不動 */
    private static String ensureUtf8(String zpl) {
        if (zpl == null || zpl.isEmpty()) return zpl;
        if (zpl.contains("^CI28")) return zpl;
        int idx = zpl.indexOf("^XA");
        return (idx >= 0)
                ? zpl.substring(0, idx + 3) + "\n^CI28\n" + zpl.substring(idx + 3)
                : "^XA\n^CI28\n" + zpl;
    }

    /** 觸發列印並等待完成（以 ~HS 三段回覆判定） */
    public PrintResult printAndWait(String ip, int port, String zpl,
                                    Duration connectTimeout,
                                    Duration readTimeout,
                                    Duration pollInterval,
                                    Duration overallTimeout) {
        long deadline = System.currentTimeMillis() + overallTimeout.toMillis();

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), (int) connectTimeout.toMillis());
            socket.setSoTimeout((int) readTimeout.toMillis());

            try (OutputStream out = socket.getOutputStream(); InputStream in = socket.getInputStream()) {
                // 1) 寫 ZPL（UTF-8；確保 ^CI28）
                String payload = ensureUtf8(zpl);
                out.write(payload.getBytes(StandardCharsets.UTF_8));
                out.flush();

                // 可選：給印表機一點處理時間，避免立刻查詢 ~HS 時尚未就緒
                Thread.sleep(Math.min(200, (int) pollInterval.toMillis()));

                // 2) 輪詢 ~HS，直到完成或 overallTimeout
                while (System.currentTimeMillis() < deadline) {
                    HostStatus status = queryHostStatusHS(out, in, readTimeout);
                    if (!status.receivedAny) {
                        // 規格：遇到以下狀態時，印表機 **不回應** ~HS
                        // MEDIA OUT / RIBBON OUT / HEAD OPEN / REWINDER FULL / HEAD OVER-TEMPERATURE
                        return new PrintResult(false, "No ~HS response (possible MEDIA OUT/RIBBON OUT/HEAD OPEN/REWINDER FULL/OVER-TEMP)");
                    }

                    if (status.errorMessage != null) {
                        return new PrintResult(false, "Printer error: " + status.errorMessage);
                    }
                    if (status.labelsRemaining == 0) {
                        return new PrintResult(true, "Printed OK");
                    }

                    Thread.sleep(pollInterval.toMillis());
                }
                return new PrintResult(false, "Timeout waiting for completion");
            }
        } catch (Exception e) {
            log.error("Print failed", e);
            return new PrintResult(false, "Exception: " + e.getMessage());
        }
    }

    /** 只送 ZPL 不等完成（非阻塞） */
    public PrintResult fireAndForget(String ip, int port, String zpl, Duration connectTimeout) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), (int) connectTimeout.toMillis());
            try (OutputStream out = socket.getOutputStream()) {
                String payload = ensureUtf8(zpl);
                out.write(payload.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
            return new PrintResult(true, "Sent");
        } catch (Exception e) {
            log.error("Send ZPL failed", e);
            return new PrintResult(false, e.getMessage());
        }
    }

    /** 查詢 ~HS：送 CRLF，讀 STX..ETX frame（最多 3 段）。readTimeout 內完全沒 frame → 無回覆。 */
    private HostStatus queryHostStatusHS(OutputStream out, InputStream in, Duration readTimeout) throws Exception {
        // 送 command：必須 CRLF
        out.write("~HS\r\n".getBytes(StandardCharsets.US_ASCII));
        out.flush();

        long idleDeadline = System.currentTimeMillis() + readTimeout.toMillis();
        List<String> frames = new ArrayList<>(3);

        StringBuilder cur = null;
        boolean inFrame = false;

        byte[] buf = new byte[2048];
        while (System.currentTimeMillis() < idleDeadline && frames.size() < 3) {
            int n;
            try {
                n = in.read(buf);
            } catch (java.net.SocketTimeoutException ste) {
                break; // 本次 poll 視為結束
            }
            if (n <= 0) break;

            for (int i = 0; i < n; i++) {
                byte b = buf[i];
                if (!inFrame) {
                    if (b == STX) {
                        inFrame = true;
                        cur = new StringBuilder(256);
                    }
                    continue;
                }
                if (b == ETX) {
                    // ETX 後面通常會跟 CR LF，但我們不依賴它
                    frames.add(cur.toString());
                    inFrame = false;
                    cur = null;
                    if (frames.size() >= 3) break;
                } else {
                    cur.append((char) (b & 0xFF));
                }
            }
            idleDeadline = System.currentTimeMillis() + 80; // 有收到資料就延長一點點，等同一波資料到齊
        }

        if (frames.isEmpty()) {
            return HostStatus.noResponse();
        }

        //log.debug("~HS frames ({}):\n1) {}\n2) {}\n3) {}",
//                frames.size(),
//                frames.size() >= 1 ? frames.get(0) : "",
//                frames.size() >= 2 ? frames.get(1) : "",
//                frames.size() >= 3 ? frames.get(2) : "");

        return HostStatus.fromFrames(frames);
    }

    /** ~HS 解析：取 String1/2 的欄位與錯誤旗標 */
    static class HostStatus {
        final boolean receivedAny;
        final int labelsRemaining;
        final String errorMessage;

        private HostStatus(boolean receivedAny, int labelsRemaining, String errorMessage) {
            this.receivedAny = receivedAny;
            this.labelsRemaining = labelsRemaining;
            this.errorMessage = errorMessage;
        }

        static HostStatus noResponse() {
            return new HostStatus(false, -1, null);
        }

        static HostStatus fromFrames(List<String> frames) {
            String s1 = frames.size() >= 1 ? frames.get(0) : "";
            String s2 = frames.size() >= 2 ? frames.get(1) : "";
            // String s3 = frames.size() >= 3 ? frames.get(2) : "";

            // String1: aaa,b,c,dddd,eee,f,g,h,iii,j,k,l
            String[] f1 = s1.split(",", -1);
            boolean paperOut = f1.length > 1 && "1".equals(trim0(f1[1]));   // b
            boolean underTemp = f1.length > 10 && "1".equals(trim0(f1[10])); // k
            boolean overTemp  = f1.length > 11 && "1".equals(trim0(f1[11])); // l

            // String2: mmm,n,o,p,q,r,s,t,uuuuuuuu,v,www
            String[] f2 = s2.split(",", -1);
            boolean ribbonOut = f2.length > 3 && "1".equals(trim0(f2[3]));   // p
            int remaining = 0;
            if (f2.length > 8) {
                try { remaining = Integer.parseInt(trim0(f2[8])); } catch (Exception ignored) {}
            }

            String err = null;
            if (paperOut)   err = appendErr(err, "MEDIA OUT");
            if (ribbonOut)  err = appendErr(err, "RIBBON OUT");
            if (overTemp)   err = appendErr(err, "HEAD OVER-TEMPERATURE");
            if (underTemp)  err = appendErr(err, "HEAD UNDER-TEMPERATURE");
            // HEAD OPEN / REWINDER FULL 會反映在其他旗標或直接「不回應」的情況

            return new HostStatus(true, Math.max(remaining, 0), err);
        }

        private static String trim0(String s) { return s == null ? "" : s.trim(); }
        private static String appendErr(String base, String add) {
            return base == null ? add : base + "; " + add;
        }
    }
}
