package com.czkuo.rdf88701.application.monitor;

import com.czkuo.rdf88701.application.mqtt.util.BaseMqttHandlerUtils;
import com.czkuo.rdf88701.application.service.mqtt.MqttCommandService;
import com.czkuo.rdf88701.common.dto.mqtt.command.S073CommandPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class S073ManualTester {

    private final MqttCommandService mqttCommandService;

    @Value("${s073.target-system:ase}")
    private String targetSystem;

    /** Tray 本體影像所在資料夾（需要存在 img_0..img_3） */
    @Value("${s073.manual.tray-dir:D:\\\\data\\\\ocr\\\\task-106}")
    private String trayDir;

    /** 上蓋影像所在資料夾（需要存在 img_0..img_3） */
    @Value("${s073.manual.cover-dir:D:\\\\data\\\\ocr\\\\task-107}")
    private String coverDir;

    @Value("${s073.manual.lot-id:LOT-TEST-001}")
    private String lotId;

    @Value("${s073.manual.tray-type:TRAY}")
    private String trayType;

    @Value("${s073.manual.tray-desc:TRAY PN:TEST-1234 LOT:LOT-TEST-001}")
    private String trayDesc;

    /** 開關：關掉就不自動送 */
    @Value("${s073.manual.enabled:true}")
    private boolean enabled;

    /** 週期（毫秒）在 @Scheduled 的 fixedDelayString 覆寫，這裡只是備註 */
    @Value("${s073.manual.interval-ms:60000}")
    private long intervalMs;

    /** 防止尚未送完就又開始下一輪 */
    private final AtomicBoolean busy = new AtomicBoolean(false);

    // ----------------------------------------------------------------------
    // 定時任務：每 60 秒跑一次（可用 yml 覆寫），啟動 5 秒後第一次跑
    // ----------------------------------------------------------------------
    // @Scheduled(
    //         fixedDelayString = "${s073.manual.interval-ms:60000}",
    //         initialDelayString = "${s073.manual.initial-delay-ms:5000}"
    // )
    public void tick() {
        if (!enabled) return;
        if (!busy.compareAndSet(false, true)) {
            //log.debug("[S073-TEST] 前一輪尚在送出中，跳過這一輪");
            return;
        }
        try {
            runOnce();
        } catch (Exception ex) {
            log.error("[S073-TEST] 週期送出異常：{}", ex.toString(), ex);
        } finally {
            busy.set(false);
        }
    }

    // ----------------------------------------------------------------------
    // 手動觸發（可在測試或其他控制器呼叫）
    // ----------------------------------------------------------------------
    public void runOnce() {
        try {
            List<String> trayImgs  = loadFourImagesAsDataUrls(trayDir);
            List<String> coverImgs = loadFourImagesAsDataUrls(coverDir);

            if (trayImgs.stream().allMatch(Objects::isNull)) {
                log.warn("[S073-TEST] tray 影像皆缺失：dir={}", trayDir);
                return;
            }
            if (coverImgs.stream().allMatch(Objects::isNull)) {
                log.warn("[S073-TEST] cover 影像皆缺失：dir={}", coverDir);
                return;
            }

            S073CommandPayload.Message msg = buildS073Message(
                    lotId, trayType, trayDesc, trayImgs, coverImgs);

            String tid = BaseMqttHandlerUtils.generateUniqueTid();
            mqttCommandService.sendS073WithTid(
                    targetSystem, tid,
                    msg.getLotId(), msg.getTrayType(), msg.getTrayDesc(),
                    msg
            );

            log.info("[S073-TEST] 已送 S073：TID={} | trayDir={} coverDir={} | time={}",
                    tid, trayDir, coverDir, LocalDateTime.now());

        } catch (Exception e) {
            log.error("[S073-TEST] 發送 S073 失敗：{}", e.getMessage(), e);
        }
    }

    // ----------------------------------------------------------------------
    // 組 S073 Message：index 對應 0:前一燈、1:前三燈、2:後一燈、3:後三燈；不足補 null
    // cover = 上蓋影像；tray = 本體影像
    // ----------------------------------------------------------------------
    private S073CommandPayload.Message buildS073Message(String lotId,
                                                        String trayType,
                                                        String trayDesc,
                                                        List<String> trayDataUrls,   // 本體
                                                        List<String> coverDataUrls) { // 上蓋
        S073CommandPayload.Message m = new S073CommandPayload.Message();
        m.setLotId(nz(lotId));
        m.setTrayType(nz(trayType));
        m.setTrayDesc(nz(trayDesc));

        // 上蓋（Site#12/14 角色）
        m.setUpperCoverTrayFrontOneLight   (toBytes(safeGet(coverDataUrls, 0)));
        m.setUpperCoverTrayFrontThreeLight (toBytes(safeGet(coverDataUrls, 1)));
        m.setUpperCoverTrayBackOneLight    (toBytes(safeGet(coverDataUrls, 2)));
        m.setUpperCoverTrayBackThreeLight  (toBytes(safeGet(coverDataUrls, 3)));

        // Tray 本體（目前容器）
        m.setTrayFrontOneLight   (toBytes(safeGet(trayDataUrls, 0)));
        m.setTrayFrontThreeLight (toBytes(safeGet(trayDataUrls, 1)));
        m.setTrayBackOneLight    (toBytes(safeGet(trayDataUrls, 2)));
        m.setTrayBackThreeLight  (toBytes(safeGet(trayDataUrls, 3)));
        return m;
    }

    private static String safeGet(List<String> list, int idx) {
        return (list != null && idx >= 0 && idx < list.size()) ? list.get(idx) : null;
    }

    // ----------------------------------------------------------------------
    // 讀取目錄下 img_0..img_3 檔案並轉 Data URL；不存在則回傳 null
    // 支援副檔名：jpg/jpeg/png/bmp/gif，其他一律當 octet-stream
    // ----------------------------------------------------------------------
    private List<String> loadFourImagesAsDataUrls(String dir) {
        List<String> out = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            Path p = findExistingImage(Paths.get(dir), "img_" + i);
            out.add(p != null ? toDataUrl(p) : null);
        }
        return out;
    }

    /** 在目錄中尋找同名不同副檔的第一個存在檔 */
    private Path findExistingImage(Path baseDir, String baseName) {
        String[] exts = {"jpg","jpeg","png","bmp","gif","webp"};
        for (String ext : exts) {
            Path p = baseDir.resolve(baseName + "." + ext);
            if (Files.exists(p)) return p;
        }
        // 也接受沒有副檔名
        Path p0 = baseDir.resolve(baseName);
        return Files.exists(p0) ? p0 : null;
    }

    /** 讀檔→base64→Data URL */
    private String toDataUrl(Path path) {
        try {
            byte[] bytes = Files.readAllBytes(path);
            String b64 = Base64.getEncoder().encodeToString(bytes);
            String mime = guessMime(path.getFileName().toString());
            return "data:" + mime + ";base64," + b64;
        } catch (IOException e) {
            log.warn("[S073-TEST] 讀檔失敗：{}", path, e);
            return null;
        }
    }

    private String guessMime(String filename) {
        String f = filename.toLowerCase(Locale.ROOT);
        if (f.endsWith(".jpg") || f.endsWith(".jpeg")) return "image/jpeg";
        if (f.endsWith(".png"))  return "image/png";
        if (f.endsWith(".bmp"))  return "image/bmp";
        if (f.endsWith(".gif"))  return "image/gif";
        if (f.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
    }

    /** SDK 會吃 bytes；若是 dataURL 就切掉前綴再 base64 decode；若不是，直接當作 base64 對待 */
    private byte[] toBytes(String dataUrlOrBase64) {
        if (dataUrlOrBase64 == null || dataUrlOrBase64.isBlank()) return null;
        String s = dataUrlOrBase64.trim();
        int comma = s.indexOf(',');
        String payload = (s.startsWith("data:") && comma > 0) ? s.substring(comma + 1) : s;
        try {
            return Base64.getDecoder().decode(payload);
        } catch (IllegalArgumentException e) {
            log.warn("[S073-TEST] base64 解析失敗，略過影像：{}", e.toString());
            return null;
        }
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
