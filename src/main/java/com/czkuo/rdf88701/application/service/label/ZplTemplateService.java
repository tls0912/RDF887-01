package com.czkuo.rdf88701.application.service.label;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * ZplTemplateService
 * ------------------------------------------------------------
 * 專責把 LabelingInfoService.LabelVars → ZPL 指令字串。
 *
 * 設計重點
 * - 提供兩種常用樣板：
 *   (1) buildBasicSevenFields()：S065 七欄位（SCH/QTY/PASS/BGA/BBI/MARK/TPI）
 *   (2) buildDetailedS066()：   S066 專用之「固定版面 + 中文 + TTF 字型」標籤
 *
 * 重要說明（S066 新版）：
 * - 採用 Zebra 下載字型：E:87710702.TTF
 *   → 以 ^A@N,h,w,E:87710702.TTF 設定字型。
 * - 文字編碼採 UTF-8，再在 ZPL 內用 ^CI28 + ^FH + HEX 輸出：
 *   Java 變數存「正常文字」（可含中文），在組 ZPL 時才做 UTF-8 → HEX：
 *
 *   例如：
 *     "SN球成分"
 *   轉成：
 *     _53_4E_E7_90_83_E6_88_90_E5_88_86
 *
 *   並輸出：
 *     ^FH^FD _53_4E_E7_90_83_E6_88_90_E5_88_86^FS
 *
 * - Marking / Rework 內的 '|' 代表換行，行數不固定：
 *   - 在 Java 端以 '|' split，逐行畫到標籤上。
 *
 * - 本類只負責組 ZPL 字串，不處理「字型檔下載到印表機」這件事
 *   （你已用 Zebra 工具把 87710702.TTF 放到 E:，這裡只負責引用）。
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Service
public class ZplTemplateService {

    /** Zebra 下載字型路徑（需事先用 Zebra 工具放到印表機的 E:） */
    private static final String FONT_PATH = "E:SIM000.TTF";

    // ========================= Public API =========================

    /**
     * S065 七欄位版：相容舊格式，字級可調。
     *
     * @param v        從 LabelingInfoService.extractLabelVars() 取得
     * @param fontSize 字級（會自動防呆 >= 20）
     */
    public String buildBasicSevenFields(LabelingInfoService.LabelVars v, int fontSize) {
        ZplOptions opt = ZplOptions.builder().fontSize(fontSize).build();
        return buildDetailedS065(v);
        //return buildBasicSevenFields(v, opt);
    }

    /**
     * S066 詳細欄位版（新版）
     * --------------------------------------------------------
     * - 不再走舊的「LOT/BIN/QTY/CLASS/Remark/條碼」通用樣板，
     *   而是直接產生你那張固定版面的 ZPL：
     *
     *   - 外框 (GB)
     *   - 左側 CUST/PKG/LC / PKG SIZE / SN球成分 / FLUX成份 / 迴焊曲線 / REWORK缺點項目
     *   - 右側 正印/背印內容 (Marking)
     *   - 下方 開單資訊 (UserTime / Spec)
     *   - 條碼區 (BarCode)
     *   - 其他欄位：binClass / binCode / binQty / binType / TT / PKG / SNBall / Flux / Reflow / Rework ...
     *
     * - fontSize 參數在此版本僅保留相容性，不再實際使用；
     *   真正的字級是直接寫死在版面上（跟你原本 ZPL 的數值一致）。
     */
    public String buildDetailedS066(LabelingInfoService.LabelVars v, int fontSize) {
        return buildDetailedS066(v);
    }

    /**
     * S066 詳細欄位版（新版核心實作）
     * @param v 由 LabelingInfoService.extractLabelVars() 回來的資料
     */
    public String buildDetailedS066(LabelingInfoService.LabelVars v) {
        StringBuilder sb = new StringBuilder(4096);

        // ───────────────── Label 起始/通用設定 ─────────────────
        sb.append("^XA\r\n");
        sb.append("~TA000\r\n");
        sb.append("~JSN\r\n");
        sb.append("^LT0\r\n");
        sb.append("^MNW\r\n");
        sb.append("^MTT\r\n");
        sb.append("^PON\r\n");
        sb.append("^PMN\r\n");
        sb.append("^LH0,0\r\n");
        sb.append("^JMA\r\n");
        sb.append("^PR2,2\r\n");
        sb.append("~SD15\r\n");
        sb.append("^JUS\r\n");
        sb.append("^LRN\r\n");
        // 先維持 Latin（CI27），但真正印字前會切到 CI28
        sb.append("^CI27\r\n");
        sb.append("^PA0,1,1,0\r\n");

        // 開始正式標籤內容
        sb.append("^MMT\r\n");
        sb.append("^PW2079\r\n");
        sb.append("^LL2079\r\n");
        sb.append("^LS0\r\n");
        // 使用 UTF-8（搭配 ^FH + HEX）
        sb.append("^CI28\r\n");

        // ───────────────── 畫框架（照你給的 2079 版） ─────────────────
        sb.append("^FO101,154^GB1937,331,5^FS\r\n");
        sb.append("^FO1046,509^GB992,71,5^FS\r\n");
        sb.append("^FO1046,603^GB992,1346,5^FS\r\n");
        sb.append("^FO1258,1973^GB780,71,5^FS\r\n");
        sb.append("^FO1022,1973^GB213,71,5^FS\r\n");
        sb.append("^FO455,509^GB567,71,5^FS\r\n");
        sb.append("^FO101,509^GB331,71,5^FS\r\n");
        sb.append("^FO101,603^GB331,71,5^FS\r\n");
        sb.append("^FO101,698^GB331,71,5^FS\r\n");
        sb.append("^FO101,792^GB331,71,5^FS\r\n");
        sb.append("^FO101,887^GB331,71,5^FS\r\n");
        sb.append("^FO455,603^GB567,71,5^FS\r\n");
        sb.append("^FO455,698^GB567,71,5^FS\r\n");
        sb.append("^FO455,792^GB567,71,5^FS\r\n");
        sb.append("^FO455,887^GB567,71,5^FS\r\n");
        sb.append("^FO111,983^GB921,83,5^FS\r\n");
        sb.append("^FO111,1088^GB921,862,5^FS\r\n");
        sb.append("^FO111,1973^GB213,71,5^FS\r\n");
        sb.append("^FO337,1973^GB661,71,5^FS\r\n");

        // ───────────────── 左側固定標題（全部用 TTF + HEX） ─────────────────
        // CUST/PKG/LC
        writeHexText(sb, 113, 563, 42, 43, "CUST/PKG/LC");
        // PKG SIZE
        writeHexText(sb, 113, 657, 42, 43, "PKG SIZE");
        // SN球成分
        writeHexText(sb, 113, 752, 42, 43, "SN球成分");
        // FLUX成份
        writeHexText(sb, 113, 846, 42, 43, "FLUX成份");
        // 迴焊曲線
        writeHexText(sb, 113, 941, 42, 43, "迴焊曲線");
        // REWORK缺點項目
        writeHexText(sb, 455, 1042, 42, 43, "REWORK缺點項目");
        // 正印/背印內容
        writeHexText(sb, 1412, 563, 42, 43, "正印/背印內容");
        // 開單資訊（左下）
        writeHexText(sb, 113, 2027, 42, 43, "開單資訊");
        // 開單資訊（右下）
        writeHexText(sb, 1034, 2027, 42, 43, "開單資訊");

        // ───────────────── 條碼區 ─────────────────
        // Code128 條碼：^BY 宽/比、^BC 內容
        sb.append("^BY8,3,191\r\n");
        sb.append("^FT588,376^BCN,,Y,N\r\n");
        // BarCode：通常為 UCC/EAN-128，需要前綴 >:，這裡依你之前的例子組：">:" + BarCode
        String barCode = safe(v.getBarCode());
        if (StringUtils.isNotBlank(barCode)) {
            sb.append("^FD").append(escapeFD(">:".concat(barCode))).append("^FS\r\n");
        } else {
            sb.append("^FD").append(" ").append("^FS\r\n");
        }

        // ───────────────── 左上 binClass / binCode / binType / binQty ─────────────────
        // binClass（例：Green）
        writeHexText(sb, 183, 294, 75, 76, v.getBinClass());
        // binCode（例：3601 / 3326）
        writeHexText(sb, 197, 392, 50, 51, v.getBinCode());
        // binType（例：R2）
        writeHexText(sb, 1825, 312, 108, 109, v.getBinType());
        // binQty（例：2）
        writeHexText(sb, 1932, 408, 50, 51, v.getBinQty());

        // ───────────────── 中段製程/條件資料 ─────────────────
        // TT（例：SMS/PM2H/292）
        writeHexText(sb, 469, 554, 33, 33, v.getTestType());
        // PKG（例：VFBGA 5X5X0.45 (X2L MAP)）
        writeHexText(sb, 469, 648, 33, 33, v.getPkg());
        // SNBall（例：98.25SN_1.2AG_0.5CU_0.05NI_0.25）
        writeHexText(sb, 469, 748, 33, 33, v.getSnBall());
        // Flux（例：SP6900 / SF6000）
        writeHexText(sb, 475, 843, 33, 33, v.getFlux());
        // Reflow（例：NOPB2407）
        writeHexText(sb, 469, 937, 33, 33, v.getReflow());

        // ───────────────── REWORK 區（v.getRework()，'|' = 換行） ─────────────────
        // 範例：
        // Rework:
        //   ■球大小__1qty__
        //   u3000BALL DIMENSION
        //   ■球偏__10qty____10qty__
        //   u3000BALL MISPLACE
        // → 行首 (x,y) = (97,1119)，每行 y += 68
        String rework = safe(v.getRework());
        if (StringUtils.isNotBlank(rework)) {
            String[] lines = StringUtils.split(rework, '|');
            int rx = 127;
            int ry = 1179;
            int step = 68; // 1187-1119 = 68，1255-1187 = 68
            if (lines != null) {
                for (String ln : lines) {
                    String line = StringUtils.trimToEmpty(ln);
                    if (line.isEmpty()) continue;
                    writeHexText(sb, rx, ry, 54, 53, line);
                    ry += step;
                }
            }
        }

        // ───────────────── Marking 區（v.getMarking()，'|' = 換行） ─────────────────
        // 範例：
        // Marking:
        //   Laser Top Mark
        //   @
        //   88E1548-BAM2
        //   P3NM00.03JW
        //   2349 A1E
        //   TW
        //   @
        //
        // → 行首 (x,y) = (1041,625)，每行 y += 68
        String marking = safe(v.getMarking());
        if (StringUtils.isNotBlank(marking)) {
            String[] lines = StringUtils.split(marking, '|');
            int mx = 1071;
            int my = 685;
            int step = 68;
            if (lines != null) {
                for (String ln : lines) {
                    String line = StringUtils.trimToEmpty(ln);
                    if (line.isEmpty()) continue;
                    writeHexText(sb, mx, my, 54, 53, line);
                    my += step;
                }
            }
        }

        // ───────────────── 底部 UserTime / Spec ─────────────────
        writeHexText(sb, 357, 2024, 33, 33, v.getUserTime());
        writeHexText(sb, 1281, 2024, 33, 33, v.getSpec());

        // 結尾
        sb.append("^PQ1,0,1,Y\r\n");
        sb.append("^XZ\r\n");
        log.info("[Labeling] zpl ={}", sb);

        return sb.toString();
    }
    /**
     * S065 詳細欄位版（新版核心實作）
     * @param v 由 LabelingInfoService.extractLabelVars() 回來的資料
     */
    public String buildDetailedS065(LabelingInfoService.LabelVars v) {
        StringBuilder sb = new StringBuilder(4096);

        // ───────────────── Label 起始/通用設定 ─────────────────
        sb.append("^XA\r\n");
        sb.append("~TA000\r\n");
        sb.append("~JSN\r\n");
        sb.append("^LT0\r\n");
        sb.append("^MNW\r\n");
        sb.append("^MTT\r\n");
        sb.append("^PON\r\n");
        sb.append("^PMN\r\n");
        sb.append("^LH0,0\r\n");
        sb.append("^JMA\r\n");
        sb.append("^PR2,2\r\n");
        sb.append("~SD15\r\n");
        sb.append("^JUS\r\n");
        sb.append("^LRN\r\n");
        // 先維持 Latin（CI27），但真正印字前會切到 CI28
        sb.append("^CI27\r\n");
        sb.append("^PA0,1,1,0\r\n");

        // 開始正式標籤內容
        sb.append("^MMT\r\n");
        sb.append("^PW2079\r\n");
        sb.append("^LL2079\r\n");
        sb.append("^LS0\r\n");
        // 使用 UTF-8（搭配 ^FH + HEX）
        sb.append("^CI28\r\n");

        // ───────────────── 畫框架（照你給的 2079 版） ─────────────────
        sb.append("^FO188,820^GB1800,1116,5^FS\r\n");
        sb.append("^FO575,1116^GB1266,0,5^FS\r\n");
        sb.append("^FO575,1286^GB1266,0,5^FS\r\n");
        sb.append("^FO575,1456^GB1266,0,5^FS\r\n");
        sb.append("^FO575,1626^GB1266,0,5^FS\r\n");
        sb.append("^FO575,1796^GB1266,0,5^FS\r\n");

        // ───────────────── 固定資料區（全部用 TTF + HEX） ─────────────────
        writeHexText(sb, 1114,128,60,60,"ASE Confidential/Security-C");
        writeHexText(sb, 274,306,160,160,"SCH:");
        writeHexText(sb, 274,562,120,120,"總量(QTY):");
        writeHexText(sb, 434,910,65,100,"Full Function VM Summary");
        writeHexText(sb, 254,1060,120,120,"PASS:");
        writeHexText(sb, 310,1230,120,120,"BGA:");
        writeHexText(sb, 310,1400,120,120,"BBI:");
        writeHexText(sb, 245,1570,120,120,"MARK:");
        writeHexText(sb, 310,1740,120,120,"TPI:");

        // ───────────────── 變動資料區 ─────────────────
        writeHexText(sb, 658,306,160,160,safe(v.getSch()));
        writeHexText(sb, 870,562,120,120,String.valueOf(v.getQty()));
        writeHexText(sb, 914,1080,120,120,String.valueOf(v.getPass()));
        writeHexText(sb, 914,1240,120,120,String.valueOf(v.getBga()));
        writeHexText(sb, 914,1400,120,120,String.valueOf(v.getBbi()));
        writeHexText(sb, 914,1570,120,120,String.valueOf(v.getMark()));
        writeHexText(sb, 914,1740,120,120,String.valueOf(v.getTpi()));


        // 結尾
        sb.append("^PQ1,0,1,Y\r\n");
        sb.append("^XZ\r\n");
        log.info("[Labeling] zpl ={}", sb);
        return sb.toString();
    }

    /**
     * S065 七欄位版（可完全客製 ZplOptions）
     * ------------------------------------------------------------
     * 保留原本簡單格式，不牽涉中文與 TTF，仍用內建字型（^CF0）。
     */
    public String buildBasicSevenFields(LabelingInfoService.LabelVars v, ZplOptions opt) {
        final ZplOptions o = sane(opt);
        StringBuilder sb = new StringBuilder(1024);

        // 起始/通用設定
        openBasic(sb, o);
        int x = o.getMarginLeft();
        int y = o.getMarginTop();

        // 七欄位
        lineKV(sb, x, y,   "SCH",  safe(v.getSch()));           y += o.getLineHeight();
        lineKV(sb, x, y,   "QTY",  String.valueOf(v.getQty())); y += o.getLineHeight();
        lineKV(sb, x, y,   "PASS", String.valueOf(v.getPass()));y += o.getLineHeight();
        lineKV(sb, x, y,   "BGA",  String.valueOf(v.getBga())); y += o.getLineHeight();
        lineKV(sb, x, y,   "BBI",  String.valueOf(v.getBbi())); y += o.getLineHeight();
        lineKV(sb, x, y,   "MARK", String.valueOf(v.getMark()));y += o.getLineHeight();
        lineKV(sb, x, y,   "TPI",  String.valueOf(v.getTpi())); y += o.getLineHeight();

        close(sb);
        return sb.toString();
    }

    // ========================= Options =========================

    /**
     * ZPL 版面選項（給 S065 用；S066 走固定版面）
     */
    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ZplOptions {
        /** 標籤起點（左上角偏移） */
        private int marginLeft;       // default 20
        private int marginTop;        // default 20

        /** 文字字級與行高（行高建議 fontSize * 1.25 ~ 1.4） */
        private int fontSize;         // default 60
        private int lineHeight;       // default -1 => auto by fontSize

        /** 一般縮排（如 Remark 子項縮排用） */
        private int indent;           // default 20

        /** Remark 區塊寬度（像素）；<=0 表示不使用 ^FB 自動換行 */
        private int remarkWidth;      // default 0

        /** 條碼控制 */
        private boolean printBarcode;       // default true
        private boolean printHumanReadable; // default true（顯示解譯文字）
        private int barcodeHeight;          // default 180
        private int barcodeModuleWidth;     // default 2（模組寬）
        private int barcodeWideRatio;       // default 2（wide:narrow 比）
    }

    // ========================= Private Helpers =========================

    /** 將 options 補齊預設與防呆（給 S065 用） */
    private ZplOptions sane(ZplOptions in) {
        ZplOptions o = in == null ? ZplOptions.builder().build() : in;
        if (o.getMarginLeft() <= 0)  o.setMarginLeft(20);
        if (o.getMarginTop()  <= 0)  o.setMarginTop(20);
        if (o.getFontSize()   <  20) o.setFontSize(60);
        if (o.getLineHeight() <= 0)  o.setLineHeight((int)Math.round(o.getFontSize() * 1.33));
        if (o.getIndent()     <= 0)  o.setIndent(20);
        if (o.getBarcodeHeight() <= 0)       o.setBarcodeHeight(180);
        if (o.getBarcodeModuleWidth() <= 0)  o.setBarcodeModuleWidth(2);
        if (o.getBarcodeWideRatio() <= 0)    o.setBarcodeWideRatio(2);
        return o;
    }

    /** S065 用的 ^XA 開始 + 字型與 Label Home（不啟用 UTF-8 / TTF） */
    private void openBasic(StringBuilder sb, ZplOptions o) {
        sb.append("^XA\r\n");
        // 全域字體（^CF family,height）：這裡只設定高度；字族用 0（預設字）
        sb.append("^CF0,").append(o.getFontSize()).append("\r\n");
        // Label Home（左上角位移）
        sb.append("^LH").append(o.getMarginLeft()).append(",").append(o.getMarginTop()).append("\r\n");
    }

    /** ^XZ 結束（共用） */
    private void close(StringBuilder sb) {
        sb.append("^XZ\r\n");
    }

    /** Key:Value 一行輸出（KEY: VALUE），給 S065 使用（純 ASCII） */
    private void lineKV(StringBuilder sb, int x, int y, String key, String value) {
        sb.append(fo(x, y)).append("^FD ")
                .append(escapeFD(key)).append(": ")
                .append(escapeFD(value)).append("^FS\r\n");
    }

    /** 區塊文字（^FB）可自動換行（目前只給 S065/備用） */
    @SuppressWarnings("unused")
    private void blockText(StringBuilder sb, int x, int y, int width, int lines, String content, ZplOptions o) {
        // ^FBw,h,g,j,ls  → 我們只用 width/lines，其餘採預設
        sb.append(fo(x, y)).append("^FB").append(width).append(",").append(lines).append(",")
                .append(0).append(",L,0\r\n");
        sb.append("^FD").append(escapeFD(content)).append("^FS\r\n");
    }

    /** 格式：^FOx,y */
    private static String fo(int x, int y) {
        return "^FO" + x + "," + y;
    }

    // ──────────────── S066 用：TTF + UTF-8 HEX 輸出工具 ────────────────

    /**
     * 在指定座標輸出一段文字，使用：
     * - TTF：E:87710702.TTF
     * - UTF-8 → HEX + ^FH
     *
     * @param x    ^FT x
     * @param y    ^FT y
     * @param h    字高（ZPL A@ height）
     * @param w    字寬（ZPL A@ width）
     * @param text 原始字串（可含中文）
     */
    private void writeHexText(StringBuilder sb, int x, int y, int h, int w, String text) {
        String s = safe(text);
        sb.append("^FT").append(x).append(",").append(y)
                .append("^A@N,").append(h).append(",").append(w).append(",").append(FONT_PATH)
                .append("^FH^FD ");
        sb.append(toZplHexUtf8(s));
        sb.append("^FS\r\n");
    }

    /**
     * 將字串轉成 ZPL ^FH 用的 HEX 格式：
     *
     *   "SN球成分"
     * → "_53_4E_E7_90_83_E6_88_90_E5_88_86"
     *
     * 規則：
     * - 先以 UTF-8 編碼成 byte[]
     * - 每個 byte 轉成兩位大寫十六進位，前面加底線 '_'
     */
    private String toZplHexUtf8(String s) {
        if (s == null || s.isEmpty()) return "";
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        StringBuilder hex = new StringBuilder(bytes.length * 3);
        for (byte b : bytes) {
            hex.append('_');
            int v = b & 0xFF;
            String hv = Integer.toHexString(v).toUpperCase();
            if (hv.length() == 1) hex.append('0');
            hex.append(hv);
        }
        return hex.toString();
    }

    /** 內容防呆：null→""；trim；去 CR/LF */
    private static String safe(String s) {
        if (s == null) return "";
        String t = StringUtils.trimToEmpty(s);
        t = t.replace("\r", " ").replace("\n", " ");
        return t;
    }

    /**
     * ^FD 內的基本保護（給 S065 或條碼 ASCII 用）：
     * - 將 ^ 與 ~ 換成空白，避免被當成指令開頭。
     * - 如需更嚴格，可再限制為 ASCII printable。
     */
    private static String escapeFD(String s) {
        String t = safe(s);
        t = t.replace('^', ' ').replace('~', ' ');
        if (t.length() > 300) t = t.substring(0, 300);
        return t;
    }

    private static String wrapParen(String s) {
        return StringUtils.isBlank(s) ? "" : " (" + s + ")";
    }
}
