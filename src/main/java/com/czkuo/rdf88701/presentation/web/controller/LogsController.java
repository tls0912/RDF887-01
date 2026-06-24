package com.czkuo.rdf88701.presentation.web.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@RestController
@RequestMapping("/api/logs")
public class LogsController {

    @Value("${LOG_PATH:D:/logs/rdf88701}")
    private String logPath;

    @Value("${APP_NAME:rdf88701}")
    private String appName;

    // === DTOs (Java 17 records) ===
    public record LogFileDto(String name, long size, boolean gzip, long lastModified, String type) {}
    public record TailResponse(String date, String name, int lines, boolean gzip, List<String> content) {}

    // GET /api/logs/dates
    @GetMapping("/dates")
    public List<String> listDates() throws IOException {
        Path base = Paths.get(logPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(base)) return List.of();

        try (Stream<Path> s = Files.list(base)) {
            return s.filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .filter(n -> n.matches("\\d{4}-\\d{2}-\\d{2}"))
                    .sorted(Comparator.reverseOrder())
                    .toList();
        }
    }

    // GET /api/logs/files?date=yyyy-MM-dd
    @GetMapping("/files")
    public ResponseEntity<List<LogFileDto>> listFiles(@RequestParam String date) throws IOException {
        Path dir = safeJoin(Paths.get(logPath), date);
        if (!Files.isDirectory(dir)) return ResponseEntity.ok(List.of());

        List<LogFileDto> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(Files::isRegularFile)
                    .map(p -> {
                        try {
                            String name = p.getFileName().toString();
                            long size = Files.size(p);
                            boolean gzip = name.endsWith(".gz");
                            long lm = Files.getLastModifiedTime(p).toMillis();
                            String type = detectType(name);
                            return new LogFileDto(name, size, gzip, lm, type);
                        } catch (IOException e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparingLong(LogFileDto::lastModified).reversed())
                    .collect(Collectors.toList());
        }
        return ResponseEntity.ok(files);
    }

    // GET /api/logs/tail?date=yyyy-MM-dd&name=<file>&lines=200&charset=UTF-8
    @GetMapping("/tail")
    public ResponseEntity<TailResponse> tail(
            @RequestParam String date,
            @RequestParam String name,
            @RequestParam(defaultValue = "200") int lines,
            @RequestParam(defaultValue = "UTF-8") String charset
    ) throws IOException {

        Path file = safeJoin(Paths.get(logPath), date, name);
        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        boolean gzip = name.endsWith(".gz");
        Charset cs = safeCharset(charset);

        List<String> content = tailLastLines(file, lines, gzip, cs);
        TailResponse resp = new TailResponse(date, name, lines, gzip, content);
        return ResponseEntity.ok(resp);
    }

    // GET /api/logs/download?date=yyyy-MM-dd&name=<file>
    @GetMapping("/download")
    public ResponseEntity<?> download(
            @RequestParam String date,
            @RequestParam String name
    ) throws IOException {

        Path file = safeJoin(Paths.get(logPath), date, name);
        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        FileSystemResource res = new FileSystemResource(file.toFile());
        String ct = guessContentType(name);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename(name).build());
        headers.setContentType(MediaType.parseMediaType(ct));
        headers.setContentLength(res.contentLength());

        return new ResponseEntity<>(res, headers, HttpStatus.OK);
    }

    // ===== helpers =====

    private Path safeJoin(Path base, String... parts) throws IOException {
        Path p = base.toAbsolutePath().normalize();
        for (String s : parts) {
            p = p.resolve(s);
        }
        p = p.normalize().toAbsolutePath();
        Path baseNorm = base.toAbsolutePath().normalize();

        if (!p.startsWith(baseNorm)) {
            throw new SecurityException("Invalid path.");
        }
        return p;
    }

    private String detectType(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains(".json.")) return "json";
        if (lower.startsWith("mqtt")) return "mqtt";
        if (lower.startsWith("infrared")) return "infrared";
        if (lower.startsWith(appName.toLowerCase(Locale.ROOT))) return "app";
        return "other";
    }

    private String guessContentType(String name) {
        if (name.endsWith(".gz")) return "application/gzip";
        if (name.endsWith(".json")) return MimeTypeUtils.APPLICATION_JSON_VALUE;
        return MediaType.TEXT_PLAIN_VALUE;
    }

    private Charset safeCharset(String name) {
        try {
            return Charset.forName(name);
        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
    }

    // 以「環狀緩衝」回傳最後 N 行；.gz 會整檔 stream 一次（10MB 級別 OK）
    private List<String> tailLastLines(Path path, int n, boolean gzip, Charset cs) throws IOException {
        if (n <= 0) return List.of();

        Deque<String> buffer = new ArrayDeque<>(n);
        try (InputStream fis = Files.newInputStream(path);
             InputStream is = gzip ? new GZIPInputStream(fis) : fis;
             BufferedReader br = new BufferedReader(new InputStreamReader(is, cs))) {

            String line;
            while ((line = br.readLine()) != null) {
                if (buffer.size() == n) buffer.removeFirst();
                buffer.addLast(line);
            }
        }
        return new ArrayList<>(buffer);
    }
}
