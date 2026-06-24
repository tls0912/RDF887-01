package com.czkuo.rdf88701.common.image;

import MvCameraControlWrapper.MvCameraControlDefines;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;

/**
 * 將 HIK MVS 抓到的 raw 影像，依 pixelType 轉成 BufferedImage，
 * 並可再編碼成 jpg/bmp 位元組（不依賴 SDK 的 SaveImage*）。
 *
 * 注意：
 * - 僅針對常見像素格式：Mono8、RGB8_Packed、BGR8_Packed、
 *   BayerRG8/GR8/GB8/BG8、YUYV(422)。
 * - 若你的相機用到 10/12/16 bit 格式或其他 YUV 變種，需再擴充。
 * - Spring Boot 預設 headless=true，可正常使用 ImageIO。
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public final class ImageConv {
    private ImageConv() {}

    // Bayer 排列（放方法外，JDK8 OK）
    private enum BayerPattern { RGGB, GRBG, GBRG, BGGR }

    // ===== 入口：raw + info -> BufferedImage =====
    public static BufferedImage toBufferedImage(byte[] raw, MvCameraControlDefines.MV_FRAME_OUT_INFO info) {
        int w = unsignedShort(info.width);
        int h = unsignedShort(info.height);
        MvCameraControlDefines.MvGvspPixelType pt = info.pixelType;

        if (pt == MvCameraControlDefines.MvGvspPixelType.PixelType_Gvsp_Mono8) {
            return mono8ToImage(raw, w, h);
        }
        if (pt == MvCameraControlDefines.MvGvspPixelType.PixelType_Gvsp_RGB8_Packed) {
            return rgb8PackedToBGRImage(raw, w, h);
        }
        if (pt == MvCameraControlDefines.MvGvspPixelType.PixelType_Gvsp_BGR8_Packed) {
            return bgr8PackedToBGRImage(raw, w, h);
        }
        if (pt == MvCameraControlDefines.MvGvspPixelType.PixelType_Gvsp_BayerRG8
                || pt == MvCameraControlDefines.MvGvspPixelType.PixelType_Gvsp_BayerGR8
                || pt == MvCameraControlDefines.MvGvspPixelType.PixelType_Gvsp_BayerGB8
                || pt == MvCameraControlDefines.MvGvspPixelType.PixelType_Gvsp_BayerBG8) {
            return bayer8ToBGRImage(raw, w, h, pt);
        }
        // 常見 YUV：YUYV (422)
        if (pt == MvCameraControlDefines.MvGvspPixelType.PixelType_Gvsp_YUV422_YUYV_Packed
                || pt == MvCameraControlDefines.MvGvspPixelType.PixelType_Gvsp_YUV422_Packed) {
            return yuyv422ToBGRImage(raw, w, h);
        }

        throw new UnsupportedOperationException("未支援的像素格式: " + pt);
    }

    // ===== 入口：BufferedImage -> 已編碼位元組（jpg/bmp） =====
    public static byte[] writeToBytes(BufferedImage img, String ext, int jpegQuality1to100) throws Exception {
        String e = (ext == null) ? "jpg" : ext.toLowerCase();
        if ("jpeg".equals(e)) e = "jpg";

        ByteArrayOutputStream baos = new ByteArrayOutputStream(64 * 1024);
        if ("jpg".equals(e)) {
            // JPEG
            Iterator<ImageWriter> it = ImageIO.getImageWritersByFormatName("jpg");
            if (!it.hasNext()) throw new IllegalStateException("找不到 JPEG ImageWriter");
            ImageWriter writer = it.next();
            MemoryCacheImageOutputStream ios = null;
            try {
                ios = new MemoryCacheImageOutputStream(baos);
                writer.setOutput(ios);
                ImageWriteParam p = writer.getDefaultWriteParam();
                p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                float q = Math.max(1, Math.min(jpegQuality1to100, 100)) / 100f;
                p.setCompressionQuality(q);
                writer.write(null, new IIOImage(img, null, null), p);
            } finally {
                if (ios != null) ios.close();
                writer.dispose();
            }
            return baos.toByteArray();
        } else if ("bmp".equals(e)) {
            ImageIO.write(img, "bmp", baos);
            return baos.toByteArray();
        } else if ("png".equals(e)) {
            ImageIO.write(img, "png", baos);
            return baos.toByteArray();
        } else {
            // 其他副檔名一律嘗試交給 ImageIO
            if (!ImageIO.write(img, e, baos)) {
                throw new IllegalArgumentException("不支援的輸出格式: " + ext);
            }
            return baos.toByteArray();
        }
    }

    // ================= 具體轉換 =================

    private static BufferedImage mono8ToImage(byte[] raw, int w, int h) {
        int need = w * h;
        if (raw.length < need) throw new IllegalArgumentException("Mono8 資料長度不足");
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        byte[] dst = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
        System.arraycopy(raw, 0, dst, 0, Math.min(dst.length, raw.length));
        return img;
    }

    private static BufferedImage rgb8PackedToBGRImage(byte[] raw, int w, int h) {
        int need = w * h * 3;
        if (raw.length < need) throw new IllegalArgumentException("RGB8 資料長度不足");
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
        byte[] dst = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
        for (int i = 0, j = 0; i < need; i += 3, j += 3) {
            int r = raw[i] & 0xFF;
            int g = raw[i + 1] & 0xFF;
            int b = raw[i + 2] & 0xFF;
            dst[j]     = (byte) b;
            dst[j + 1] = (byte) g;
            dst[j + 2] = (byte) r;
        }
        return img;
    }

    private static BufferedImage bgr8PackedToBGRImage(byte[] raw, int w, int h) {
        int need = w * h * 3;
        if (raw.length < need) throw new IllegalArgumentException("BGR8 資料長度不足");
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
        byte[] dst = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
        System.arraycopy(raw, 0, dst, 0, Math.min(dst.length, raw.length));
        return img;
    }

    // --- Bayer 8-bit 轉換（雙線性去馬賽克，簡化版） ---
    private static BufferedImage bayer8ToBGRImage(byte[] raw, int w, int h, MvCameraControlDefines.MvGvspPixelType pt) {
        int need = w * h;
        if (raw.length < need) throw new IllegalArgumentException("Bayer8 資料長度不足");
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
        byte[] dst = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();

        BayerPattern pattern;
        if (pt == MvCameraControlDefines.MvGvspPixelType.PixelType_Gvsp_BayerRG8) {
            pattern = BayerPattern.RGGB;
        } else if (pt == MvCameraControlDefines.MvGvspPixelType.PixelType_Gvsp_BayerGR8) {
            pattern = BayerPattern.GRBG;
        } else if (pt == MvCameraControlDefines.MvGvspPixelType.PixelType_Gvsp_BayerGB8) {
            pattern = BayerPattern.GBRG;
        } else {
            pattern = BayerPattern.BGGR;
        }

        for (int y = 0; y < h; y++) {
            boolean yEven = (y & 1) == 0;
            for (int x = 0; x < w; x++) {
                boolean xEven = (x & 1) == 0;
                int center = raw[y * w + x] & 0xFF;

                int r = 0, g = 0, b = 0; // 先給初值避免未初始化告警

                switch (pattern) {
                    case RGGB:
                        if (yEven && xEven) { // R
                            r = center;
                            g = avgSafe(raw, w, h, x-1,y, x+1,y, x,y-1, x,y+1);
                            b = avgSafe(raw, w, h, x-1,y-1, x+1,y-1, x-1,y+1, x+1,y+1);
                        } else if (yEven) { // G (R row)
                            r = avgSafe(raw, w, h, x-1,y, x+1,y);
                            g = center;
                            b = avgSafe(raw, w, h, x,y-1, x,y+1);
                        } else if (xEven) { // G (B row)
                            r = avgSafe(raw, w, h, x,y-1, x,y+1);
                            g = center;
                            b = avgSafe(raw, w, h, x-1,y, x+1,y);
                        } else { // B
                            r = avgSafe(raw, w, h, x-1,y-1, x+1,y-1, x-1,y+1, x+1,y+1);
                            g = avgSafe(raw, w, h, x-1,y, x+1,y, x,y-1, x,y+1);
                            b = center;
                        }
                        break;

                    case GRBG:
                        if (yEven && xEven) { // G (R row)
                            r = avgSafe(raw, w, h, x-1,y, x+1,y);
                            g = center;
                            b = avgSafe(raw, w, h, x,y-1, x,y+1);
                        } else if (yEven) { // R
                            r = center;
                            g = avgSafe(raw, w, h, x-1,y, x+1,y, x,y-1, x,y+1);
                            b = avgSafe(raw, w, h, x-1,y-1, x+1,y-1, x-1,y+1, x+1,y+1);
                        } else if (xEven) { // B
                            r = avgSafe(raw, w, h, x-1,y-1, x+1,y-1, x-1,y+1, x+1,y+1);
                            g = avgSafe(raw, w, h, x-1,y, x+1,y, x,y-1, x,y+1);
                            b = center;
                        } else { // G (B row)
                            r = avgSafe(raw, w, h, x,y-1, x,y+1);
                            g = center;
                            b = avgSafe(raw, w, h, x-1,y, x+1,y);
                        }
                        break;

                    case GBRG:
                        if (yEven && xEven) { // G (B row)
                            r = avgSafe(raw, w, h, x,y-1, x,y+1);
                            g = center;
                            b = avgSafe(raw, w, h, x-1,y, x+1,y);
                        } else if (yEven) { // B
                            r = avgSafe(raw, w, h, x-1,y-1, x+1,y-1, x-1,y+1, x+1,y+1);
                            g = avgSafe(raw, w, h, x-1,y, x+1,y, x,y-1, x,y+1);
                            b = center;
                        } else if (xEven) { // R
                            r = center;
                            g = avgSafe(raw, w, h, x-1,y, x+1,y, x,y-1, x,y+1);
                            b = avgSafe(raw, w, h, x-1,y-1, x+1,y-1, x-1,y+1, x+1,y+1);
                        } else { // G (R row)
                            r = avgSafe(raw, w, h, x-1,y, x+1,y);
                            g = center;
                            b = avgSafe(raw, w, h, x,y-1, x,y+1);
                        }
                        break;

                    case BGGR:
                        if (yEven && xEven) { // B
                            r = avgSafe(raw, w, h, x-1,y-1, x+1,y-1, x-1,y+1, x+1,y+1);
                            g = avgSafe(raw, w, h, x-1,y, x+1,y, x,y-1, x,y+1);
                            b = center;
                        } else if (yEven) { // G (B row)
                            r = avgSafe(raw, w, h, x,y-1, x,y+1);
                            g = center;
                            b = avgSafe(raw, w, h, x-1,y, x+1,y);
                        } else if (xEven) { // G (R row)
                            r = avgSafe(raw, w, h, x-1,y, x+1,y);
                            g = center;
                            b = avgSafe(raw, w, h, x,y-1, x,y+1);
                        } else { // R
                            r = center;
                            g = avgSafe(raw, w, h, x-1,y, x+1,y, x,y-1, x,y+1);
                            b = avgSafe(raw, w, h, x-1,y-1, x+1,y-1, x-1,y+1, x+1,y+1);
                        }
                        break;
                }

                int di = (y * w + x) * 3;
                dst[di]     = (byte) clamp8(b);
                dst[di + 1] = (byte) clamp8(g);
                dst[di + 2] = (byte) clamp8(r);
            }
        }

        return img;
    }

    private static BufferedImage yuyv422ToBGRImage(byte[] raw, int w, int h) {
        int need = w * h * 2;
        if (raw.length < need) throw new IllegalArgumentException("YUYV422 資料長度不足");
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
        byte[] dst = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();

        int si = 0;
        int di = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x += 2) {
                int y0 = raw[si++] & 0xFF;
                int u  = raw[si++] & 0xFF;
                int y1 = raw[si++] & 0xFF;
                int v  = raw[si++] & 0xFF;

                int[] rgb0 = yuvToRgb(y0, u, v);
                dst[di++] = (byte) rgb0[2]; // B
                dst[di++] = (byte) rgb0[1]; // G
                dst[di++] = (byte) rgb0[0]; // R

                int[] rgb1 = yuvToRgb(y1, u, v);
                dst[di++] = (byte) rgb1[2];
                dst[di++] = (byte) rgb1[1];
                dst[di++] = (byte) rgb1[0];
            }
        }
        return img;
    }

    // ================= 小工具 =================

    private static int unsignedShort(short s) {
        return s & 0xFFFF;
    }

    private static int avgSafe(byte[] raw, int w, int h, int... coords) {
        // coords: x1,y1,x2,y2,...
        int sum = 0, cnt = 0;
        for (int i = 0; i + 1 < coords.length; i += 2) {
            int x = coords[i], y = coords[i + 1];
            if (x >= 0 && x < w && y >= 0 && y < h) {
                sum += raw[y * w + x] & 0xFF;
                cnt++;
            }
        }
        if (cnt == 0) return 0;
        return sum / cnt;
    }

    private static int clamp8(int v) {
        if (v < 0) return 0;
        if (v > 255) return 255;
        return v;
    }

    private static int[] yuvToRgb(int y, int u, int v) {
        int c = y - 16;
        int d = u - 128;
        int e = v - 128;
        int r = (298 * c + 409 * e + 128) >> 8;
        int g = (298 * c - 100 * d - 208 * e + 128) >> 8;
        int b = (298 * c + 516 * d + 128) >> 8;
        return new int[]{ clamp8(r), clamp8(g), clamp8(b) };
    }
}
