package com.czkuo.rdf88701.application.service.command;

import com.czkuo.rdf88701.application.service.location.LocationAccountingService;
import com.czkuo.rdf88701.common.dto.mqtt.ack.L005AckPayload;
import com.czkuo.rdf88701.common.enums.EntryType;
import com.czkuo.rdf88701.domain.repository.ContainerAttrRepository;
import com.czkuo.rdf88701.domain.repository.ContainerDataRepository;
import com.czkuo.rdf88701.domain.repository.ContainerMainRepository;
import com.czkuo.rdf88701.infra.entity.ContainerAttr;
import com.czkuo.rdf88701.infra.entity.ContainerMain;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;


/**
 * 建立容器服務
 * - 支援從 L005 ACK 來的建帳前置資訊（upsert 主檔 + container_data.content_kind + attr 寫入）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContainerCreateService {

    private static final Set<String> ALLOWED_TYPES = Set.of("TRAY","CASSETTE","FOUP","BOX");

    private final ContainerMainRepository containerMainRepository;
    private final ContainerAttrRepository containerAttrRepository;
    private final ContainerDataRepository containerDataRepository;
    private final LocationAccountingService locationAccountingService;

    /**
     * 建立虛擬容器（系統內部用）
     */
    public Long createVirtualContainer(String prefix, String type, String lotNo, String partNo) {
        String aliasCode = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);

        ContainerMain container = new ContainerMain();
        container.setAliasCode(aliasCode);
        container.setContainerType(type);
        container.setLotNo(lotNo);
        container.setPartNo(partNo);
        container.setCreatedTime(LocalDateTime.now());

        containerMainRepository.save(container);

        log.info("[建立虛擬容器] aliasCode={}, type={}, lot={}, part={}", aliasCode, type, lotNo, partNo);
        return container.getId();
    }

    /**
     * 建立實體容器（指定 aliasCode）
     */
    public Long createRealContainer(String aliasCode, String type, String lotNo, String partNo) {
        ContainerMain container = new ContainerMain();
        container.setAliasCode(aliasCode);
        container.setContainerType(type);
        container.setLotNo(lotNo);
        container.setPartNo(partNo);
        container.setCreatedTime(LocalDateTime.now());

        containerMainRepository.save(container);

        log.info("[建立實體容器] aliasCode={}, type={}, lot={}, part={}", aliasCode, type, lotNo, partNo);
        return container.getId();
    }

    /**
     * 建立虛擬容器並立即建帳
     */
    public Long createAndEntryVirtualContainer(String prefix, String type, Long locationPointId) {
        Long containerId = createVirtualContainer(prefix, type, null, null);

        locationAccountingService.entry(
                containerId,
                locationPointId,
                EntryType.PLC,
                "system-auto",
                null
        );

        log.info("[建立並建帳] containerId={} → locationPointId={}", containerId, locationPointId);
        return containerId;
    }

    /**
     * 建立實體容器並立即建帳
     */
    public Long createAndEntryRealContainer(String aliasCode, String type, Long locationPointId) {
        Long containerId = createRealContainer(aliasCode, type, null, null);

        locationAccountingService.entry(
                containerId,
                locationPointId,
                EntryType.PLC,
                "system-auto",
                null
        );

        log.info("[建立並建帳] containerId={} → locationPointId={}", containerId, locationPointId);
        return containerId;
    }

    /**
     * 建立實體容器並立即建帳
     */
    public Long entryRealContainer(Long containerId, String type, Long locationPointId) {
        locationAccountingService.entry(
                containerId,
                locationPointId,
                EntryType.PLC,
                "system-auto",
                null
        );

        log.info("[建立並建帳] containerId={} → locationPointId={}", containerId, locationPointId);
        return containerId;
    }

    // =========================================================
    // ===============  L005 整合（含 content_kind/attr）  ======
    // =========================================================

    /**
     * L005：確保/建立母容器（冪等）+ 寫入 container_data.content_kind + 記錄 L005 result/height
     * 規則：
     * - container_type 一律 'TRAY'
     * - lot_no = LOT_ID（若空才回退 CARRIERID）
     * - alias_code = CARRIERID
     * - container_code = BARCODE
     * - TRAY_TYPE → content_kind（NORMAL_WITH_COVER / ALL_COVER / NORMAL_NO_COVER / EMPTY / UNKNOWN）
     * - result、result_message → attr（l005_result / l005_result_message）
     * - TRAY_HIGH → attr（tray_thickness_mm，單位 mm）
     *
     * @return container_main.id（若 CARRIERID 缺失回傳 null）
     */
    @Transactional
    public Long ensureFromL005(L005AckPayload ack) {
        if (ack == null || ack.getMessage() == null) {
            log.warn("[L005.ensure] payload/message 為空，略過");
            return null;
        }

        final String carrierId   = StringUtils.trimToEmpty(ack.getMessage().getCarrierId());
        final String lotId       = StringUtils.defaultIfBlank(StringUtils.trimToEmpty(ack.getMessage().getLotId()), carrierId);
        final String barcode     = StringUtils.trimToEmpty(ack.getMessage().getBarcode());
        final String trayTypeRaw = StringUtils.trimToEmpty(ack.getMessage().getTrayType()); // ← 將寫入 part_no
        final String trayHighRaw = StringUtils.trimToEmpty(ack.getMessage().getTrayHigh());
        final String result      = StringUtils.upperCase(StringUtils.trimToEmpty(ack.getResult()));
        final String resultMsg   = StringUtils.trimToEmpty(ack.getResultMessage());

        if (StringUtils.isBlank(carrierId)) {
            log.warn("[L005.ensure] CARRIERID 缺失，無法建立/關聯容器");
            return null;
        }

        // 1) upsert 主檔（type 固定 TRAY；lot_no=LOT_ID；container_code=BARCODE；part_no=TrayType）
        ContainerMain main = containerMainRepository.findByAliasCode(carrierId).orElse(null);

        // 若用 carrierId 找不到，且 barcode 有值 → 改用 barcode 找
        if (main == null && StringUtils.isNotBlank(barcode)) {
            try {
                main = containerMainRepository.findByContainerCode(barcode).orElse(null);
            } catch (Exception ignore) {
                // 若你的 repo 尚未提供 findByContainerCode，可移除此段
            }
            if (main != null && StringUtils.isBlank(main.getAliasCode())) {
                main.setAliasCode(carrierId); // 補 alias_code
                try {
                    boolean filled = containerMainRepository.update(main);
                    log.info("[L005.ensure] 以 BARCODE 對應 id={}，補 alias_code={} (updated={})",
                            main.getId(), carrierId, filled);
                } catch (Exception e) {
                    log.warn("[L005.ensure] 補寫 alias_code 失敗/衝突：{}", e.getMessage());
                }
            }
        }

        if (main == null) {
            // 建立新主檔
            main = new ContainerMain();
            main.setAliasCode(carrierId);
            main.setContainerType("TRAY");
            main.setLotNo(lotId);
            if (StringUtils.isNotBlank(barcode)) main.setContainerCode(barcode);
            if (StringUtils.isNotBlank(trayTypeRaw)) main.setPartNo(trayTypeRaw); // ★ TrayType → part_no
            main.setCreatedTime(LocalDateTime.now());
            containerMainRepository.save(main);
            log.info("[L005.ensure] 建立母容器 id={} alias={} lot={} barcode={} partNo={}",
                    main.getId(), main.getAliasCode(), main.getLotNo(), main.getContainerCode(), main.getPartNo());
        } else {
            boolean needUpdate = false;

            if (!"TRAY".equalsIgnoreCase(StringUtils.defaultString(main.getContainerType()))) {
                main.setContainerType("TRAY");
                needUpdate = true;
            }
            if (!StringUtils.equals(lotId, StringUtils.defaultString(main.getLotNo()))) {
                main.setLotNo(lotId);
                needUpdate = true;
            }
            if (StringUtils.isNotBlank(barcode)
                    && !StringUtils.equals(barcode, StringUtils.defaultString(main.getContainerCode()))) {
                main.setContainerCode(barcode);
                needUpdate = true;
            }
            // TrayType → part_no（有值才覆蓋）
            if (StringUtils.isNotBlank(trayTypeRaw)
                    && !StringUtils.equals(trayTypeRaw, StringUtils.defaultString(main.getPartNo()))) {
                main.setPartNo(trayTypeRaw);
                needUpdate = true;
            }

            if (needUpdate) {
                boolean updated = containerMainRepository.update(main);
                log.info("[L005.ensure] 補寫母容器 id={} type={} lot={} barcode={} partNo={} (updated={})",
                        main.getId(), main.getContainerType(), main.getLotNo(), main.getContainerCode(), main.getPartNo(), updated);
            }
        }

        final Long containerId = main.getId();

        // 2) content_kind 落在 container_data（僅 patch 該欄位）
        //    規則：REDTRAY → ALL_COVER；其餘 → NORMAL_WITH_COVER；空白 → UNKNOWN
        final String contentKind = mapContentKindForL005(trayTypeRaw);
        if (StringUtils.isNotBlank(contentKind)) {
            containerDataRepository.upsertContentKind(containerId, contentKind);
        }

        // 3) 記錄 L005 結果 & 高度到 attr
        if (StringUtils.isNotBlank(result)) {
            upsertAttr(containerId, "l005_result", result, null);
        }
        if (StringUtils.isNotBlank(resultMsg)) {
            upsertAttr(containerId, "l005_result_message", resultMsg, null);
        }
        if (StringUtils.isNotBlank(trayTypeRaw)) {
            upsertAttr(containerId, "l005_tray_type", trayTypeRaw, null); // 方便追溯（同時也已寫入 part_no）
        }
        if (StringUtils.isNotBlank(trayHighRaw)) {
            // 原字串 & 標準化數值
            upsertAttr(containerId, "tray_height_raw", trayHighRaw, null);
            BigDecimal heightMm = tryParseMillimetersScale2(trayHighRaw);
            if (heightMm != null) {
                upsertAttr(containerId, "tray_thickness_mm", String.valueOf(heightMm), "mm");
            }
        }

        return containerId;
    }

    /**
     * TR2 自動建立實體 TRAY 並立即建帳，且預設：
     * - container_data.content_kind = NORMAL_WITH_COVER
     * - attr: tray_thickness_mm = 5.62 (unit=mm)
     * - alias_code = CARRIERID（由 BARCODE 雜湊推導）
     * - container_code = BARCODE（原值）
     * - lot_no = LOT_ID（由 BARCODE 雜湊推導）
     *
     * 由 BARCODE 雜湊穩定生出：
     *   CARRIERID: "TY" + 4位數字 + "VM"  (e.g. TY9989VM)
     *   LOT_ID   : 2位數字 + 5位英字 + 3位數字 (e.g. 58NFVVV001)
     */
    @Transactional
    public Long createAndEntryRealTrayForLocationAuto(String barcode, Long locationPointId, String coverKind) {
        // --- 0) 由 BARCODE 雜湊穩定推導 carrierId / lotId ---
        IdPair ids = makeIdsFromBarcode(barcode);

        // --- 1) 建立母檔（TRAY） ---
        ContainerMain container = new ContainerMain();
        container.setAliasCode(ids.carrierId);    // ← 由雜湊推導的 CARRIERID
        container.setContainerCode(barcode);      // ← 原始 BARCODE
        container.setContainerType("TRAY");
        container.setLotNo(ids.lotId);            // ← 由雜湊推導的 LOT_ID
        container.setPartNo("4607996101");
        container.setCreatedTime(LocalDateTime.now());
        containerMainRepository.save(container);

        Long containerId = container.getId();
        log.info("[TR2-AUTO] 建立母容器 id={} barcode={} carrierId={} lot={} type=TRAY",
                containerId, barcode, ids.carrierId, ids.lotId);

        // --- 2) 立即建帳（入點位） ---
        locationAccountingService.entry(
                containerId,
                locationPointId,
                EntryType.PLC,
                "system-auto",
                null
        );
        log.info("[TR2-AUTO] 建帳完成 containerId={} → locationPointId={}", containerId, locationPointId);

        // --- 3) content_kind / 厚度屬性 ---
        containerDataRepository.upsertContentKind(containerId, coverKind);
        upsertAttr(containerId, "tray_thickness_mm", "5.62", "mm");

        return containerId;
    }

    // ---------------- helpers ----------------

    /** TRAY_TYPE → container_data.content_kind 的映射（REDTRAY→ALL_COVER；其餘→NORMAL_WITH_COVER；空白→UNKNOWN） */
    private String mapContentKindForL005(String raw) {
        if (StringUtils.isBlank(raw)) return "UNKNOWN";
        String v = raw.trim();
        if ("REDTRAY".equalsIgnoreCase(v)) return "ALL_COVER";
        return "NORMAL_WITH_COVER";
    }

    /** TRAY_TYPE → container_data.content_kind 的映射 */
    private String mapContentKind(String raw) {
        if (StringUtils.isBlank(raw)) return "UNKNOWN";
        String v = raw.trim().toUpperCase();
        if ("NORMAL_WITH_COVER".equals(v) || "WITH_COVER".equals(v)) return "NORMAL_WITH_COVER";
        if ("NORMAL_NO_COVER".equals(v)  || "NO_COVER".equals(v))   return "NORMAL_NO_COVER";
        if ("ALL_COVER".equals(v))                                   return "ALL_COVER";
        if ("EMPTY".equals(v))                                       return "EMPTY";
        return "UNKNOWN";
    }

    /** 解析高度字串成 mm（四捨五入到整數 mm），如 "12.3mm" → 12 */
    private Integer tryParseMillimeters(String raw) {
        if (StringUtils.isBlank(raw)) return null;
        String num = raw.replaceAll("[^0-9.\\-]", "");
        if (StringUtils.isBlank(num)) return null;
        try {
            double v = Double.parseDouble(num);
            long rounded = Math.round(v); // 需保留小數就改成 BigDecimal 並指定 scale
            if (rounded < 0) return null; // 負值視為無效
            return (int) Math.min(rounded, Integer.MAX_VALUE);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 解析高度字串成 mm，保留到小數第二位 */
    private BigDecimal tryParseMillimetersScale2(String raw) {
        if (raw == null || raw.isBlank()) return null;

        // 允許像 "12.3mm"、"12,3 mm"、"1,234.56mm"、"1.234,56 mm" 等寫法
        String n = raw.trim();

        // 只保留數字與小數/負號/分隔符
        n = n.replaceAll("[^0-9,\\.\\-]", "");

        if (n.isBlank()) return null;

        // 小數分隔符正規化：
        // 若同時有 '.' 與 ',' => 視 ',' 為千分位，去掉所有 ',' （例如 1,234.56）
        // 若只有 ',' 沒有 '.' => 將 ',' 當作小數點（例如 12,34 -> 12.34）
        if (n.contains(".") && n.contains(",")) {
            n = n.replace(",", "");
        } else if (n.contains(",") && !n.contains(".")) {
            n = n.replace(',', '.');
        }

        try {
            BigDecimal bd = new BigDecimal(n);
            if (bd.signum() < 0) return null; // 負值視為無效
            return bd.setScale(2, RoundingMode.HALF_UP); // ★ 保留到小數第二位
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void upsertAttr(Long containerId, String key, String value, String unit) {
        ContainerAttr a = new ContainerAttr();
        a.setContainerMainId(containerId);
        a.setAttrKey(key);
        a.setAttrValue(value);
        a.setUnit(unit);
        containerAttrRepository.upsert(a); // 需 UNIQUE KEY (container_main_id, attr_key)
    }

    // 若仍有用到：保留，但 ensureFromL005 已固定寫 TRAY，不再依賴此方法
    @SuppressWarnings("unused")
    private String normalizeType(String raw) {
        if (StringUtils.isBlank(raw)) return "TRAY";
        String up = raw.toUpperCase();
        return ALLOWED_TYPES.contains(up) ? up : "TRAY";
    }

    /* ===========================
   ======== 雜湊小工具 ========
   =========================== */

    /** 用 BARCODE 雜湊穩定生出 CARRIERID / LOT_ID */
    private static IdPair makeIdsFromBarcode(String barcode) {
        byte[] h = hashBytes(barcode == null ? "" : barcode);

        // CARRIERID: "TY" + 4位數字 + "VM"（位數由雜湊提供）
        String carrierIdDigits = hashDigits(h, 4, 0);
        String carrierId = "TY" + carrierIdDigits + "VM";

        // LOT_ID: 2位數字 + 5位英字 + 3位數字（位數/字母由雜湊提供）
        String lot = hashDigits(h, 2, 4) + hashLetters(h, 5, 6) + hashDigits(h, 3, 11);

        return new IdPair(carrierId, lot);
    }

    /** 對輸入做 SHA-256，回傳 32 bytes（穩定、可重現） */
    private static byte[] hashBytes(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            // 極少數情況下發生（理論上不會），退回 UTF-8 bytes 本體避免 NPE
            return (s == null ? new byte[0] : s.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * 由雜湊 bytes 產生指定長度的「數字字串」
     * @param h       雜湊 bytes
     * @param count   位數
     * @param offset  從雜湊陣列哪個索引開始取值（循環使用）
     */
    private static String hashDigits(byte[] h, int count, int offset) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            int b = (h.length == 0) ? 0 : Byte.toUnsignedInt(h[(offset + i) % h.length]);
            sb.append((char) ('0' + (b % 10)));
        }
        return sb.toString();
    }

    /**
     * 由雜湊 bytes 產生指定長度的「大寫英文字串」
     * @param h       雜湊 bytes
     * @param count   字母數
     * @param offset  從雜湊陣列哪個索引開始取值（循環使用）
     */
    private static String hashLetters(byte[] h, int count, int offset) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            int b = (h.length == 0) ? 0 : Byte.toUnsignedInt(h[(offset + i) % h.length]);
            sb.append((char) ('A' + (b % 26)));
        }
        return sb.toString();
    }

    /** 簡單的雙值封裝 */
    private static final class IdPair {
        final String carrierId;
        final String lotId;
        IdPair(String carrierId, String lotId) { this.carrierId = carrierId; this.lotId = lotId; }
    }
}