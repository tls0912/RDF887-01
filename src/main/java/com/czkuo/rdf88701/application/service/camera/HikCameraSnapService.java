package com.czkuo.rdf88701.application.service.camera;

import MvCameraControlWrapper.MvCameraControl;
import MvCameraControlWrapper.MvCameraControlDefines;
import com.czkuo.rdf88701.common.image.ImageConv;
import com.czkuo.rdf88701.common.util.ImageUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;

@Slf4j
@Service
public class HikCameraSnapService {

    private static final int DEFAULT_TIMEOUT_MS = 5000;
    private static final int DEFAULT_BUF_SIZE = 20 * 1024 * 1024; // 20MB

    /**
     * 以相機索引抓拍（存檔；可選是否一併回傳 Base64）
     */
    public SnapResult snapOnce(int cameraIndex, Path saveRoot, String format, int jpgQuality, boolean returnBase64) {
        var dev = listDevices().get(cameraIndex);
        return snapInternalEx(dev, saveRoot, format, jpgQuality, /*saveFile*/true, returnBase64, /*fixedFilenameNoExt*/null, /*overwrite*/false, null);
    }

    /**
     * 以相機 IPv4（例如 192.168.3.2）抓拍（存檔；可選是否一併回傳 Base64）
     */
    public SnapResult snapOnceByIp(String ip, Path saveRoot, String format, int jpgQuality, boolean returnBase64) {
        var dev = findDeviceByIp(ip);
        return snapInternalEx(dev, saveRoot, format, jpgQuality, /*saveFile*/true, returnBase64, /*fixedFilenameNoExt*/null, /*overwrite*/false, null);
    }

    /**
     * 以相機 IPv4 抓拍（存檔；可選是否一併回傳 Base64），並「指定固定檔名」與是否覆蓋
     */
    public SnapResult snapOnceByIp(String ip, Path saveRoot, String format, int jpgQuality, boolean returnBase64,
                                   String fixedFilenameNoExt, boolean overwrite) {
        var dev = findDeviceByIp(ip);
        return snapInternalEx(dev, saveRoot, format, jpgQuality, /*saveFile*/true, returnBase64, fixedFilenameNoExt, overwrite, null);
    }

    /**
     * 以相機 IPv4 抓拍（存檔；可選是否一併回傳 Base64），並「指定固定檔名」與是否覆蓋，夾帶曝光時間
     */
    public SnapResult snapOnceByIp(String ip, Path saveRoot, String format, int jpgQuality, boolean returnBase64,
                                   String fixedFilenameNoExt, boolean overwrite, Long exposureUs) {
        var dev = findDeviceByIp(ip);
        return snapInternalEx(dev, saveRoot, format, jpgQuality,
                /*saveFile*/true, returnBase64, fixedFilenameNoExt, overwrite, exposureUs);
    }

    /**
     * 只要 Base64，不存檔
     */
    public SnapResult snapOnceByIpBase64Only(String ip, String format, int jpgQuality) {
        var dev = findDeviceByIp(ip);
        // saveFile=false, returnBase64=true
        return snapInternalEx(dev, /*saveRoot*/null, format, jpgQuality, false, true, /*fixedFilenameNoExt*/null, /*overwrite*/false, null);
    }

    private MvCameraControlDefines.MV_CC_DEVICE_INFO findDeviceByIp(String ip) {
        var devs = listDevices();
        return devs.stream()
                .filter(d -> d.transportLayerType == MvCameraControlDefines.MV_GIGE_DEVICE
                        && d.gigEInfo != null
                        && ip.equalsIgnoreCase(d.gigEInfo.currentIp))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("[HikSnap]找不到指定 IP 的 GigE 相機: " + ip));
    }

    /**
     * 列舉所有相機（GigE/USB）
     */
    public ArrayList<MvCameraControlDefines.MV_CC_DEVICE_INFO> listDevices() {
        int flag = MvCameraControlDefines.MV_GIGE_DEVICE | MvCameraControlDefines.MV_USB_DEVICE;
        try {
            var devs = MvCameraControl.MV_CC_EnumDevices(flag);
            if (devs == null || devs.isEmpty()) {
                throw new IllegalStateException("[HikSnap]找不到任何 HIKROBOT 相機");
            }
            return devs;
        } catch (Exception e) {
            throw new RuntimeException("[HikSnap]EnumDevices 失敗", e);
        }
    }

    /**
     * 共同抓拍（可選存檔與是否回傳 Base64）
     */
    private SnapResult snapInternalEx(MvCameraControlDefines.MV_CC_DEVICE_INFO dev,
                                      Path saveRoot, String format, int jpgQuality,
                                      boolean saveFile, boolean returnBase64) {
        return snapInternalEx(dev, saveRoot, format, jpgQuality, saveFile, returnBase64, null, false, null);
    }

    /**
     * 共同抓拍（可選存檔與是否回傳 Base64；可指定固定檔名並覆蓋）
     */
    private SnapResult snapInternalEx(MvCameraControlDefines.MV_CC_DEVICE_INFO dev,
                                      Path saveRoot, String format, int jpgQuality,
                                      boolean saveFile, boolean returnBase64,
                                      String fixedFilenameNoExt, boolean overwrite,
                                      Long exposureUs) {
        // 建 Handle
        MvCameraControlDefines.Handle h;
        try {
            h = MvCameraControl.MV_CC_CreateHandle(dev);
        } catch (Exception e) {
            log.warn("[HikSnap] CreateHandle 失敗", e);
            throw new RuntimeException("CreateHandle 失敗", e);
        }

        try {
            // 開機
            chk(MvCameraControl.MV_CC_OpenDevice(h), "OpenDevice");

            // 設定曝光（若有帶）
            if (exposureUs != null && exposureUs > 0) {
                try {
                    // 有些相機需要先關 Auto Exposure
                    try {
                        // 0=Off, 1=Once, 2=Continuous (常見定義)
                        MvCameraControl.MV_CC_SetEnumValue(h, "ExposureAuto", 0);
                    } catch (Throwable ignore) {
                    }

                    // ExposureTime 通常是 float（us）
                    int r = MvCameraControl.MV_CC_SetFloatValue(h, "ExposureTime", exposureUs.floatValue());
                    if (r != 0) {
                        log.warn("[HikSnap] 設定曝光失敗 ip={} exposureUs={} ret=0x{}",
                                (dev.gigEInfo != null ? dev.gigEInfo.currentIp : "USB"),
                                exposureUs, Integer.toHexString(r));
                    } else {
                        log.info("[HikSnap] 曝光已設定 ip={} exposureUs={}",
                                (dev.gigEInfo != null ? dev.gigEInfo.currentIp : "USB"),
                                exposureUs);
                    }
                } catch (Throwable t) {
                    log.warn("[HikSnap] 設定曝光發生非致命錯誤 exposureUs={} err={}", exposureUs, t.toString());
                }
            }

            // GigE 小優化
//            try {
//                int pkt = MvCameraControl.MV_CC_GetOptimalPacketSize(h);
//                if (pkt > 0) MvCameraControl.MV_CC_SetIntValue(h, "GevSCPSPacketSize", pkt);
//                MvCameraControl.MV_GIGE_SetGvcpTimeout(h, 1000);
//                MvCameraControl.MV_GIGE_SetGvspTimeout(h, 1000);
//            } catch (Throwable t) {
//                log.warn("設定 GigE 參數時發生非致命錯誤: {}", t.toString());
//            }

            // 連續取流
            chk(MvCameraControl.MV_CC_SetEnumValue(h, "TriggerMode", 0), "TriggerMode=Off");
            chk(MvCameraControl.MV_CC_StartGrabbing(h), "StartGrabbing");

            // 抓一張
            var info = new MvCameraControlDefines.MV_FRAME_OUT_INFO();
            byte[] raw = new byte[DEFAULT_BUF_SIZE];
            chk(MvCameraControl.MV_CC_GetOneFrameTimeout(h, raw, info, DEFAULT_TIMEOUT_MS), "GetOneFrameTimeout");

            // 輸出格式/路徑
            String ext = ("jpg".equalsIgnoreCase(format) || "jpeg".equalsIgnoreCase(format)) ? "jpg" : "bmp";
            Path file = null;

            if (saveFile) {
                if (fixedFilenameNoExt != null && !fixedFilenameNoExt.isBlank()) {
                    // 固定檔名：直接寫在 saveRoot 下，利於覆蓋保留最新
                    try {
                        Files.createDirectories(saveRoot);
                    } catch (Exception io) {
                        log.warn("[HikSnap] 建立資料夾失敗 saveRoot={}", saveRoot, io);
                        throw new RuntimeException("建立資料夾失敗: " + saveRoot, io);
                    }
                    file = saveRoot.resolve(fixedFilenameNoExt + "." + ext);
                    if (overwrite) {
                        try {
                            Files.deleteIfExists(file);
                        } catch (Exception ignore) {
                            log.warn("[HikSnap] deleteIfExists Exception file={}", file);
                        }
                    }
                } else {
                    // 舊行為：日期/時間戳
                    String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                    String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
                    Path dir = saveRoot.resolve(date);
                    file = dir.resolve("snap_" + ts + "." + ext);
                    try {
                        Files.createDirectories(dir);
                    } catch (Exception io) {
                        log.warn("[HikSnap] 建立資料夾失敗 dir={}", dir, io);
                        throw new RuntimeException("建立資料夾失敗: " + dir, io);
                    }
                }
            }

            String base64 = null;

            // ---- 路線 A：需要 Base64（或不存檔）→ 先拿到「記憶體中的已編碼影像位元組」 ----
            if (returnBase64 || !saveFile) {
                byte[] encoded = encodeToBytesInMemory(h, info, raw, ext, jpgQuality);
                if (saveFile && file != null) {
                    Files.write(file, encoded, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                }
                if (returnBase64) base64 = ImageUtils.bytesToBase64(encoded);
            }
            // ---- 路線 B：僅存檔，不需要 Base64 → 走 SDK 優先（帶 fallback） ----
            else {
                boolean saved = false;
                try {
                    var p = new MvCameraControlDefines.MV_SAVE_IMAGE_TO_FILE_PARAM_EX();
                    p.pixelType = info.pixelType;
                    p.width = info.width;
                    p.height = info.height;
                    p.data = raw;
                    p.dataLen = info.frameLen;
                    p.imageType = imageTypeOf(ext);
                    p.jpgQuality = sanitizeQuality(jpgQuality);
                    p.methodValue = 0;
                    p.imagePath = file.toString();
                    int r1 = MvCameraControl.MV_CC_SaveImageToFileEx(h, p);
                    if (r1 == 0)
                        saved = true;
                    else
                        log.warn("SaveImageToFileEx ret=0x{}", Integer.toHexString(r1));
                } catch (UnsatisfiedLinkError | NoSuchMethodError ignore) {
                    try {
                        log.warn("SaveImageToFileEx1 ");
                        var pOld = new MvCameraControlDefines.MV_SAVE_IMG_TO_FILE_PARAM();
                        pOld.pixelType = info.pixelType;
                        pOld.width = info.width;
                        pOld.height = info.height;
                        pOld.data = raw;
                        pOld.dataLen = info.frameLen;
                        pOld.imageType = imageTypeOf(ext);
                        pOld.jpgQuality = sanitizeQuality(jpgQuality);
                        pOld.imagePath = file.toString();
                        pOld.methodValue = 0;
                        int r2 = MvCameraControl.MV_CC_SaveImageToFile(h, pOld);
                        if (r2 == 0) saved = true;
                        else log.warn("SaveImageToFile ret=0x{}", Integer.toHexString(r2));
                    } catch (UnsatisfiedLinkError | NoSuchMethodError ignore2) {
                        log.warn("SaveImageToFileEx2 ");
                        /* 交給 Ex3 */
                    }
                }
                if (!saved) {
                    // Ex3 取 bytes 寫檔（覆蓋）
                    byte[] encoded = encodeToBytesInMemory(h, info, raw, ext, jpgQuality);
                    Files.write(file, encoded, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                }
            }

            String ipStr = (dev.transportLayerType == MvCameraControlDefines.MV_GIGE_DEVICE && dev.gigEInfo != null)
                    ? dev.gigEInfo.currentIp : "USB";
            log.info("[HikSnap] ip={} saved={} width={} height={} pixelType={}",
                    ipStr, (file != null ? file : "<no-save>"), info.width, info.height, info.pixelType);
            return new SnapResult(file, info.width, info.height, info.pixelType, ipStr, base64);

        } catch (RuntimeException re) {
            log.warn("[HikSnap] 拍照流程失敗 RuntimeException", re);
            throw re;
        } catch (Exception e) {
            log.warn("[HikSnap] 拍照流程失敗 Exception", e);
            throw new RuntimeException("拍照流程失敗", e);
        } finally {
            try {
                MvCameraControl.MV_CC_StopGrabbing(h);
            } catch (Exception e) {
                log.warn("MV_CC_StopGrabbing", e);
            }
            try {
                //MvCameraControl.MV_CC_CloseDevice(h);
            } catch (Exception e) {
                log.warn("MV_CC_CloseDevice", e);
            }
            try {
                MvCameraControl.MV_CC_DestroyHandle(h);
            } catch (Exception e) {
                log.warn("MV_CC_DestroyHandle", e);
            }
        }
    }

    /**
     * 用 SDK Ex3 或純 Java 轉圖，產生「已編碼」的影像位元組（jpg/bmp）
     */
    private byte[] encodeToBytesInMemory(MvCameraControlDefines.Handle h,
                                         MvCameraControlDefines.MV_FRAME_OUT_INFO info,
                                         byte[] raw, String ext, int jpgQuality) throws Exception {
        // 先試 Ex3（若 DLL 支援）
        try {
            var ex3 = new MvCameraControlDefines.MV_SAVE_IMAGE_PARAM_EX3();
            ex3.pixelType = info.pixelType;
            ex3.width = (int) info.width;
            ex3.height = (int) info.height;
            ex3.data = raw;
            ex3.dataLen = info.frameLen;
            ex3.imageType = imageTypeOf(ext);
            ex3.jpgQuality = sanitizeQuality(jpgQuality);
            ex3.methodValue = 0;
            ex3.imageBuffer = new byte[DEFAULT_BUF_SIZE];
            ex3.bufferSize = ex3.imageBuffer.length;

            int r = MvCameraControl.MV_CC_SaveImageEx3(h, ex3);
            if (r == 0 && ex3.imageLen > 0) {
                return Arrays.copyOf(ex3.imageBuffer, ex3.imageLen);
            }
            log.warn("SaveImageEx3 ret=0x{}", Integer.toHexString(r));
        } catch (UnsatisfiedLinkError | NoSuchMethodError ignore) {
            // 進純 Java
        }
        // 純 Java：把 raw+pixelType 轉 BufferedImage，再 ImageIO 輸出
        var img = ImageConv.toBufferedImage(raw, info);
        return ImageConv.writeToBytes(img, ext, sanitizeQuality(jpgQuality));
    }

    /**
     * 讀 SDK 版本（健診用）
     */
    public String sdkVersion() {
        try {
            return MvCameraControl.MV_CC_GetSDKVersion();
        } catch (Throwable t) {
            return "unknown: " + t.getMessage();
        }
    }

    private static void chk(int ret, String step) {
        if (ret != 0) throw new RuntimeException(step + " 失敗，ret=0x" + Integer.toHexString(ret));
    }

    private static int sanitizeQuality(int q) {
        return Math.max(1, Math.min(q, 100));
    }

    private static MvCameraControlDefines.MV_SAVE_IAMGE_TYPE imageTypeOf(String ext) {
        return ("jpg".equalsIgnoreCase(ext) || "jpeg".equalsIgnoreCase(ext))
                ? MvCameraControlDefines.MV_SAVE_IAMGE_TYPE.MV_Image_Jpeg
                : MvCameraControlDefines.MV_SAVE_IAMGE_TYPE.MV_Image_Bmp;
    }

    // 回傳結果
    public record SnapResult(Path file, int width, int height,
                             MvCameraControlDefines.MvGvspPixelType pixelType,
                             String ip,
                             String base64 // 如未要求回傳則為 null
    ) {
    }
}
