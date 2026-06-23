package com.czkuo.rdf88701.common.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class ImageUtils {
    private ImageUtils() {}

    // 常見副檔名對應 MIME
    private static final Map<String, String> EXT_TO_MIME;
    static {
        Map<String, String> m = new HashMap<>();
        m.put("jpg",  "image/jpeg");
        m.put("jpeg", "image/jpeg");
        m.put("png",  "image/png");
        m.put("bmp",  "image/bmp");
        m.put("gif",  "image/gif");
        m.put("webp", "image/webp");
        m.put("tif",  "image/tiff");
        m.put("tiff", "image/tiff");
        EXT_TO_MIME = Collections.unmodifiableMap(m);
    }

    /** 讀檔 → Base64（不含 data URL 前綴） */
    public static String fileToBase64(Path file) {
        try {
            return bytesToBase64(Files.readAllBytes(file));
        } catch (IOException e) {
            throw new RuntimeException("fileToBase64 失敗: " + file, e);
        }
    }

    /** 位元組 → Base64（不含 data URL 前綴） */
    public static String bytesToBase64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    /** Base64（支援 data URL 前綴）→ 位元組 */
    public static byte[] base64ToBytes(String base64OrDataUrl) {
        try {
            String b64 = stripDataUrlPrefix(base64OrDataUrl);
            return Base64.getDecoder().decode(b64);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("base64ToBytes 失敗：內容不是合法 Base64", e);
        }
    }

    /** Base64（支援 data URL 前綴）→ 存檔；會自動建父資料夾 */
    public static Path base64ToFile(String base64OrDataUrl, Path file) {
        try {
            byte[] bytes = base64ToBytes(base64OrDataUrl);
            ensureParentDir(file);
            Files.write(file, bytes);
            return file;
        } catch (IOException e) {
            throw new RuntimeException("base64ToFile 失敗: " + file, e);
        }
    }

    /** 位元組 → data URL（例如 image/jpeg） */
    public static String toDataUrl(String mime, byte[] bytes) {
        if (mime == null || mime.isBlank()) mime = "application/octet-stream";
        return "data:" + mime + ";base64," + bytesToBase64(bytes);
    }

    /** 讀檔 → data URL（MIME 由副檔名推斷；無法推斷時用 application/octet-stream） */
    public static String fileToDataUrl(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            String mime = guessMimeFromFilename(file.getFileName().toString());
            return toDataUrl(mime, bytes);
        } catch (IOException e) {
            throw new RuntimeException("fileToDataUrl 讀檔失敗: " + file, e);
        }
    }

    /** 讀檔 → data URL（顯式指定 MIME）——提供與舊 Base64Utils 相同語意 */
    public static String fileToDataUrl(Path file, String mime) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            if (mime == null || mime.isBlank()) {
                mime = "application/octet-stream";
            }
            return toDataUrl(mime, bytes);
        } catch (IOException e) {
            throw new RuntimeException("fileToDataUrl 讀檔失敗: " + file, e);
        }
    }

    /** 由檔名或副檔名推斷 MIME；未知時回 application/octet-stream */
    public static String guessMimeFromFilename(String filenameOrExt) {
        if (filenameOrExt == null || filenameOrExt.isBlank()) return "application/octet-stream";
        String name = filenameOrExt.toLowerCase(Locale.ROOT).trim();
        String ext = name;
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) ext = name.substring(dot + 1);
        String mime = EXT_TO_MIME.get(ext);
        return mime != null ? mime : "application/octet-stream";
    }

    /** 去掉 data URL 前綴（若存在），只回純 Base64 本體 */
    public static String stripDataUrlPrefix(String base64OrDataUrl) {
        if (base64OrDataUrl == null) return "";
        String s = base64OrDataUrl.trim();
        int comma = s.indexOf(',');
        if (comma > 0 && s.regionMatches(true, 0, "data:", 0, 5)) {
            return s.substring(comma + 1);
        }
        return s;
    }

    /** 寫位元組到檔案（自動建父資料夾） */
    public static Path writeBytes(Path file, byte[] bytes) {
        try {
            ensureParentDir(file);
            Files.write(file, bytes);
            return file;
        } catch (IOException e) {
            throw new RuntimeException("writeBytes 失敗: " + file, e);
        }
    }

    /** 讀檔 → 位元組 */
    public static byte[] readAllBytes(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new RuntimeException("readAllBytes 讀檔失敗: " + file, e);
        }
    }

    // -------------------- private helpers --------------------
    private static void ensureParentDir(Path file) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }
}
