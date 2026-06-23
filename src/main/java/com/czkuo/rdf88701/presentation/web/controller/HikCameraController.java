package com.czkuo.rdf88701.presentation.web.controller;

import com.czkuo.rdf88701.application.service.camera.HikCameraSnapService;
import com.czkuo.rdf88701.common.util.ImageUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/hikrobot/cameras")
@RequiredArgsConstructor
public class HikCameraController {

    private final HikCameraSnapService snapService;

    /** 健診：SDK 版本 */
    @GetMapping("/sdk-version")
    public Map<String, Object> version() {
        return Map.of("sdkVersion", snapService.sdkVersion());
    }

    /** 列出相機（簡易） */
    @GetMapping
    public List<Map<String, Object>> list() {
        return snapService.listDevices().stream().map(d -> {
            String ip = (d.gigEInfo != null) ? d.gigEInfo.currentIp : "";
            String model = (d.gigEInfo != null) ? d.gigEInfo.modelName
                    : (d.usb3VInfo != null ? d.usb3VInfo.modelName : "");
            String serial = (d.gigEInfo != null) ? d.gigEInfo.serialNumber
                    : (d.usb3VInfo != null ? d.usb3VInfo.serialNumber : "");

            return Map.<String, Object>of(
                    "transport", d.transportLayerType,
                    "ip",        ip,
                    "model",     model,
                    "serial",    serial
            );
        }).toList();
    }

    /** 以索引拍照（存檔；可選是否回傳 Base64 / dataURL） */
    @PostMapping("/{index}/snap")
    public ResponseEntity<?> snapByIndex(
            @PathVariable int index,
            @RequestParam(defaultValue = "bmp") String format,
            @RequestParam(defaultValue = "90") int quality,
            @RequestParam(required = false) String saveRoot,
            @RequestParam(defaultValue = "false") boolean returnBase64,
            @RequestParam(defaultValue = "false") boolean dataUrl
    ) {
        try {
            Path root = (saveRoot == null || saveRoot.isBlank())
                    ? Path.of("D:/logs/rdf88701/camera")
                    : Path.of(saveRoot);

            var result = snapService.snapOnce(index, root, format, quality, returnBase64);

            String base64 = returnBase64 ? result.base64() : null;
            String dataURL = null;
            if (dataUrl && base64 != null) {
                String mime = ImageUtils.guessMimeFromFilename("x." + format);
                dataURL = "data:" + mime + ";base64," + base64;
            }

            return ResponseEntity.ok(Map.of(
                    "ip",        result.ip(),
                    "format",    format.toLowerCase(),
                    "path",      result.file().toString(),
                    "width",     result.width(),
                    "height",    result.height(),
                    "pixelType", result.pixelType().name(),
                    "base64",    base64,
                    "dataUrl",   dataURL
            ));
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.badRequest().body(Map.of("error", iae.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 以 IP 拍照
     * - 預設會存檔（saveRoot 可選）；可同時回傳 Base64/dataURL
     * - 若 base64Only=true：只回 Base64，不落地
     */
    @PostMapping("/snap-by-ip")
    public ResponseEntity<?> snapByIp(
            @RequestParam String ip,
            @RequestParam(defaultValue = "bmp") String format,
            @RequestParam(defaultValue = "90") int quality,
            @RequestParam(required = false) String saveRoot,
            @RequestParam(defaultValue = "false") boolean returnBase64,
            @RequestParam(defaultValue = "false") boolean dataUrl,
            @RequestParam(defaultValue = "false") boolean base64Only
    ) {
        try {
            // base64Only 優先（不落地）
            if (base64Only) {
                var r = snapService.snapOnceByIpBase64Only(ip, format, quality);
                String base64 = r.base64();
                String mime = ImageUtils.guessMimeFromFilename("x." + format);
                String dataURL = dataUrl ? ("data:" + mime + ";base64," + base64) : null;

                return ResponseEntity.ok(Map.of(
                        "ip",        r.ip(),
                        "format",    format.toLowerCase(),
                        "path",      null,                 // 不落地
                        "width",     r.width(),
                        "height",    r.height(),
                        "pixelType", r.pixelType().name(),
                        "base64",    base64,
                        "dataUrl",   dataURL
                ));
            }

            // 需存檔（可選是否同時回傳 Base64）
            Path root = (saveRoot == null || saveRoot.isBlank())
                    ? Path.of("D:/logs/rdf88701/camera")
                    : Path.of(saveRoot);

            var r = snapService.snapOnceByIp(ip, root, format, quality, returnBase64);

            String base64 = returnBase64 ? r.base64() : null;
            String dataURL = null;
            if (dataUrl && base64 != null) {
                String mime = ImageUtils.guessMimeFromFilename("x." + format);
                dataURL = "data:" + mime + ";base64," + base64;
            }

            return ResponseEntity.ok(Map.of(
                    "ip",        r.ip(),
                    "format",    format.toLowerCase(),
                    "path",      r.file().toString(),
                    "width",     r.width(),
                    "height",    r.height(),
                    "pixelType", r.pixelType().name(),
                    "base64",    base64,
                    "dataUrl",   dataURL
            ));
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.badRequest().body(Map.of("error", iae.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
