package com.czkuo.rdf88701.application.service.image;

import com.czkuo.rdf88701.infra.entity.ImageAsset;
import com.czkuo.rdf88701.domain.repository.ImageAssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * ImageAssetService
 * - 將 data URL / Base64 影像解碼 → 寫入檔案系統 → 建立 image_asset 記錄
 * - 路徑規則：{root}/{scene}/yyyy/MM/dd/{refPrefix}-{refId}/{role}{ext}
 *   refPrefix: EVENT=ev, MESSAGE=msg, SESSION=ses
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageAssetService {

    private final ImageAssetRepository repo;

    /** 影像儲存根目錄（可改用你既有的 ocr.image-store-dir） */
    @Value("${image.store-root:/data/ocr}")
    private String storeRoot;

    /** 預設保留天數（對應 image_asset.retention_days，僅在未指定時使用） */
    @Value("${image.default-retention-days:30}")
    private int defaultRetentionDays;

    private static final DateTimeFormatter D_YYYY = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter D_MM   = DateTimeFormatter.ofPattern("MM");
    private static final DateTimeFormatter D_DD   = DateTimeFormatter.ofPattern("dd");

    /* ------------------------------------------------------------
     * 對外 API
     * ------------------------------------------------------------ */

    /** 通用：依 refType 儲存影像（"EVENT" / "MESSAGE" / "SESSION"） */
    public ImageAsset save(String scene,
                           String refType,
                           Long refId,
                           String role,
                           String dataUrlOrBase64,
                           String mime,
                           Map<String, Object> opts) {
        Objects.requireNonNull(scene, "scene");
        Objects.requireNonNull(refType, "refType");
        Objects.requireNonNull(refId, "refId");
        Objects.requireNonNull(role, "role");
        if (dataUrlOrBase64 == null || dataUrlOrBase64.isBlank()) {
            throw new IllegalArgumentException("image data is blank");
        }

        byte[] bytes = decode(dataUrlOrBase64);
        if (bytes.length == 0) {
            throw new IllegalArgumentException("image bytes is empty after decode");
        }

        String useMime = (mime != null && !mime.isBlank())
                ? mime
                : sniffMimeFromDataUrl(dataUrlOrBase64).orElse("image/jpeg");

        // 讀寬高（若失敗就留 null）
        Integer w = null, h = null;
        try (var bais = new ByteArrayInputStream(bytes)) {
            BufferedImage img = ImageIO.read(bais);
            if (img != null) {
                w = img.getWidth();
                h = img.getHeight();
            }
        } catch (Exception ignore) { /* no-op */ }

        String sha = sha256Hex(bytes);
        String ext = guessExt(useMime);

        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        Path dir = Paths.get(storeRoot,
                scene,
                D_YYYY.format(now),
                D_MM.format(now),
                D_DD.format(now),
                refFolderName(refType, refId));
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            throw new RuntimeException("create directories failed: " + dir, e);
        }

        // 檔名用 role，避免重名覆蓋可自行加上序號或 hash
        Path file = dir.resolve(role + ext);

        // 若檔案已存在，可選擇略過覆蓋（這裡採覆蓋）
        try {
            Files.write(file, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            throw new RuntimeException("write file failed: " + file, e);
        }

        // 建立 DB 記錄
        ImageAsset row = new ImageAsset();
        row.setScene(scene);
        row.setRefType(refType.toUpperCase(Locale.ROOT));
        row.setRefId(refId);
        row.setRole(role);
        row.setStorageUrl(file.toUri().toString()); // file:///...
        row.setMime(useMime);
        row.setBytes(bytes.length);
        row.setWidth(w);
        row.setHeight(h);
        row.setSha256(sha);
        // 可從 opts 取 retentionDays 覆蓋
        int retention = getOptInt(opts, "retentionDays", defaultRetentionDays);
        row.setRetentionDays(retention);
        row.setCapturedAt(getOptDateTime(opts, "capturedAt", null));
        row.setCreatedTime(now);
        row.setUpdatedTime(now);

        boolean ok = repo.save(row);
        if (!ok || row.getId() == null) {
            // 若 DB 寫入失敗，建議把檔案刪掉，避免孤兒檔
            try { Files.deleteIfExists(file); } catch (Exception ignore) {}
            throw new RuntimeException("insert image_asset failed");
        }
        return row;
    }

    /** 便捷：對應 mqtt_message_log.id */
    public ImageAsset saveForMessage(String scene, Long mqttMessageLogId, String role, String dataUrlOrBase64, String mime, Map<String,Object> opts) {
        return save(scene, "MESSAGE", mqttMessageLogId, role, dataUrlOrBase64, mime, opts);
    }

    /** 便捷：對應 mqtt_event_log.id */
    public ImageAsset saveForEvent(String scene, Long mqttEventLogId, String role, String dataUrlOrBase64, String mime, Map<String,Object> opts) {
        return save(scene, "EVENT", mqttEventLogId, role, dataUrlOrBase64, mime, opts);
    }

    /** 批次存圖（回傳 role → ImageAsset） */
    public Map<String, ImageAsset> saveBatch(String scene,
                                             String refType,
                                             Long refId,
                                             Map<String, String> roleToDataUrlOrB64,
                                             String defaultMime,
                                             Map<String, Object> opts) {
        if (roleToDataUrlOrB64 == null || roleToDataUrlOrB64.isEmpty()) return Collections.emptyMap();
        Map<String, ImageAsset> out = new LinkedHashMap<>();
        for (var e : roleToDataUrlOrB64.entrySet()) {
            String role = e.getKey();
            String data = e.getValue();
            if (data == null || data.isBlank()) continue;
            ImageAsset a = save(scene, refType, refId, role, data, defaultMime, opts);
            out.put(role, a);
        }
        return out;
    }

    /* ------------------------------------------------------------
     * Helpers
     * ------------------------------------------------------------ */

    private static String refFolderName(String refType, Long refId) {
        String pfx = switch (refType.toUpperCase(Locale.ROOT)) {
            case "EVENT" -> "ev-";
            case "SESSION" -> "ses-";
            default -> "msg-"; // MESSAGE
        };
        return pfx + refId;
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(bytes);
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("sha256 error", e);
        }
    }

    private static String guessExt(String mime) {
        String m = (mime == null) ? "" : mime.toLowerCase(Locale.ROOT);
        if (m.contains("png"))  return ".png";
        if (m.contains("bmp"))  return ".bmp";
        if (m.contains("gif"))  return ".gif";
        if (m.contains("webp")) return ".webp";
        // 預設 jpg
        return ".jpg";
    }

    private static Optional<String> sniffMimeFromDataUrl(String s) {
        if (s == null) return Optional.empty();
        int i = s.indexOf("data:");
        int j = s.indexOf(";base64,");
        if (i == 0 && j > 5) {
            return Optional.ofNullable(s.substring(5, j));
        }
        return Optional.empty();
    }

    /** 支援 data URL（data:image/jpeg;base64,...）與純 base64 字串 */
    private static byte[] decode(String dataUrlOrBase64) {
        String s = dataUrlOrBase64.trim();
        // data URL
        int idx = s.indexOf(";base64,");
        if (s.startsWith("data:") && idx > 0) {
            String b64 = s.substring(idx + ";base64,".length());
            return Base64.getDecoder().decode(b64.getBytes(StandardCharsets.US_ASCII));
        }
        // 純 Base64
        return Base64.getDecoder().decode(s.getBytes(StandardCharsets.US_ASCII));
    }

    private static int getOptInt(Map<String,Object> opts, String key, int def) {
        if (opts == null) return def;
        Object v = opts.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String str) {
            try { return Integer.parseInt(str.trim()); } catch (Exception ignore) {}
        }
        return def;
    }

    private static LocalDateTime getOptDateTime(Map<String,Object> opts, String key, LocalDateTime def) {
        if (opts == null) return def;
        Object v = opts.get(key);
        if (v instanceof LocalDateTime ldt) return ldt;
        return def;
    }
}
