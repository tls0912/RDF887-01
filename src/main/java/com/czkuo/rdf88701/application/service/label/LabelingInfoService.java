package com.czkuo.rdf88701.application.service.label;

import com.czkuo.rdf88701.common.dto.mqtt.command.S065CommandPayload;
import com.czkuo.rdf88701.common.dto.mqtt.command.S066CommandPayload;
import com.czkuo.rdf88701.domain.repository.LabelingInfoRepository;
import com.czkuo.rdf88701.infra.entity.LabelingInfo;
import com.czkuo.rdf88701.infra.event.model.labeling.LabelingInfoReadyEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * LabelingInfo 具體服務
 * ---------------------------------------------------------
 * 目標：
 * 1) 接 S065/S066 指令：將資料「標準化 + 落 DB（payload=JSON字串）」並標記為 READY。
 * 2) 送 S020(事件2003) 前，先記錄該站點的「水位線 watermark」（以 labeling_info.id 最大值為界）。
 *    之後僅領取「id > watermark」的第一筆 READY 作為本輪要印的貼標資訊。
 * 3) 提供原子性「領取」方法（select ... FOR UPDATE + 綁定站點/容器/標籤號）。
 * 4) 從 payload 解析出 ZPL 版型需要的 7 個欄位（sch/qty/pass/bga/bbi/mark/tpi），
 *    並新增 extractLabelVars() 回傳更完整的列印欄位（含 S066 全欄位）。
 *
 * 設計說明：
 * - payload 用 String 存入 MySQL JSON 欄位：由 ObjectMapper 產生，確保是有效 JSON。
 * - 與 PLC 的握手（典型流程）：
 *   a) 發現 PLC ReportReq=1 且尚未 Ack：先 markWatermarkForSite(site) → 送 S020(事件2003)。
 *   b) 之後週期輪詢呼叫 claimFirstReadyAfter(site, containerId, 1)。
 *   c) 一旦對方送來 S065/S066 落 DB，即可領到最新一筆 → 印標 → 回 Ack → markUsed(id)。
 *
 * 併發/一致性：
 * - claim 系列方法有 @Transactional，對應 Mapper 查詢使用 FOR UPDATE（在 XML 內），可避免多節點重複領取。
 * - watermark 存在記憶體（ConcurrentHashMap），服務重啟會失；如需跨重啟，可改小表持久化（非必須）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LabelingInfoService {

    // ===== 依賴 =====
    private final LabelingInfoRepository repo;
    private final ApplicationEventPublisher publisher;
    private final ObjectMapper objectMapper;

    // ===== 站點水位線：siteCode -> labeling_info 當時最大 id =====
    // 用來把「S020 前」與「S020 後」的 S065/S066 切開；只領取 S020 後（id 更大）的第一筆。
    private final ConcurrentMap<String, Long> siteWatermark = new ConcurrentHashMap<>();

    // =========================================================
    // ================ S065 / S066 寫入（UPSERT） ==============
    // =========================================================

    /**
     * 將 S065 指令內的 MESSAGE 清單轉成 LabelingInfo（多筆）
     * - requestKey = tid#index，避免重複（同一 TID 多筆）
     * - payload 結構統一為 { type, data, norm }，其中 norm 是印表所需的 7 欄
     * - 狀態預設 READY，expiresAt 預設 +10 分鐘（可視需求調整/改回配置）
     *
     * @param tid  S065 的 TID（yyyyMMddHHmmssSSS）
     * @param list S065.MESSAGE 清單
     * @param raw  保留擴充（目前未用，可存放完整原文或額外資訊）
     * @return 落庫/更新後的實體清單（方便後續發事件或除錯）
     */
    public List<LabelingInfo> upsertFromS065(
            String tid,
            List<S065CommandPayload.TagInfo> list,
            JsonNode raw
    ) {
        List<LabelingInfo> out = new ArrayList<>();
        if (list == null || list.isEmpty()) return out;

        for (int i = 0; i < list.size(); i++) {
            S065CommandPayload.TagInfo t = list.get(i);
            String key = tid + "#" + i; // 同一 TID 下的序號

            // 以 requestKey 去重（若已存在則更新）
            LabelingInfo po = findByRequestKey(key).orElseGet(LabelingInfo::new);
            po.setRequestKey(key);
            po.setSourceCmdId("S065");
            po.setTid(tid);

            // 建立統一版型的 norm（ZPL 會用到）
            ObjectNode norm = objectMapper.createObjectNode();
            norm.put("sch",  nz(t.getSch()));
            norm.put("qty",  toInt(t.getQty()));
            norm.put("pass", toInt(t.getPass()));
            norm.put("bga",  toInt(t.getBga()));
            norm.put("bbi",  toInt(t.getBbi()));
            norm.put("mark", toInt(t.getMark()));
            norm.put("tpi",  toInt(t.getTpi()));

            // payload = { type, data(原始), norm(歸一化) }
            ObjectNode root = objectMapper.createObjectNode();
            root.put("type", "S065");
            root.set("data", objectMapper.valueToTree(t));
            root.set("norm", norm);

            po.setPayload(toCompactJson(root)); // String（有效 JSON）
            po.setStatus("READY");
            po.setExpiresAt(LocalDateTime.now().plusMinutes(10));

            upsert(po);
            out.add(po);
        }
        return out;
    }

    /**
     * 將 S066 指令內的 MESSAGE 清單轉成 LabelingInfo（多筆）
     * - S066 欄位與 ZPL 7 欄不完全對應：此處提供「合理映射」：
     *   lotid → sch；bintotal → qty；binqty → pass；其餘先以 0 補位
     * - 之後可依實務，在這裡調整 norm 的對應邏輯
     *
     * @param tid  S066 的 TID（yyyyMMddHHmmssSSS）
     * @param list S066.MESSAGE 清單
     * @param raw  保留擴充（目前未用）
     * @return 落庫/更新後的實體清單
     */
    public List<LabelingInfo> upsertFromS066(
            String tid,
            List<S066CommandPayload.Message> list,
            JsonNode raw
    ) {
        List<LabelingInfo> out = new ArrayList<>();
        if (list == null || list.isEmpty()) return out;

        for (int i = 0; i < list.size(); i++) {
            S066CommandPayload.Message m = list.get(i);
            String key = tid + "#" + i;

            LabelingInfo po = findByRequestKey(key).orElseGet(LabelingInfo::new);
            po.setRequestKey(key);
            po.setSourceCmdId("S066");
            po.setTid(tid);

            // ★ 映射修正：qty=總數(bintotal)，pass=該 bin 數(binqty)
            ObjectNode norm = objectMapper.createObjectNode();
            norm.put("sch",  nz(m.getLotId()));        // lotid → sch
            norm.put("qty",  toInt(m.getBinTotal()));  // 所有 Bin 的總數量
            norm.put("pass", toInt(m.getBinQty()));    // 此 Bin 的數量
            norm.put("bga",  0);
            norm.put("bbi",  0);
            norm.put("mark", 0);
            norm.put("tpi",  0);

            ObjectNode root = objectMapper.createObjectNode();
            root.put("type", "S066");
            root.set("data", objectMapper.valueToTree(m)); // 保留 S066 全欄位
            root.set("norm", norm);

            po.setPayload(toCompactJson(root));
            po.setStatus("READY");
            po.setExpiresAt(LocalDateTime.now().plusMinutes(10));

            upsert(po);
            out.add(po);
        }
        return out;
    }

    // =========================================================
    // ================== Watermark（站點） ====================
    // =========================================================

    /**
     * 在「送 S020(事件2003) 之前」呼叫，將該站點目前的 labeling_info 最大 id
     * 設為 watermark。之後領取只會挑 id > watermark 的資料。
     *
     * 若服務重啟、記憶體 watermark 遺失，可以在送 S020 前重新標記即可。
     *
     * @param siteCode 站點（例：Site#30 / Site#37）
     * @return 當下的 maxId（便於除錯/記錄）
     */
    public long markWatermarkForSite(String siteCode) {
        Long maxId = repo.selectMaxId();
        if (maxId == null) maxId = 0L;
        siteWatermark.put(siteCode, maxId);
        //log.debug("[Labeling] watermark set: site={}, maxId={}", siteCode, maxId);
        return maxId;
    }

    /**
     * 清除某站點的 watermark（建議在列印 + Ack 成功後呼叫，避免殘留影響下次配對）。
     *
     * @param siteCode 站點
     */
    public void clearWatermarkForSite(String siteCode) {
        if (siteCode != null) {
            siteWatermark.remove(siteCode);
            //log.debug("[Labeling] watermark cleared: site={}", siteCode);
        }
    }

    // =========================================================
    // ================== 領取 / 查詢（含鎖） =================
    // =========================================================

    /**
     * 只領取「watermark 之後（id > watermark）」的第一筆 READY，
     * 並在同一交易中綁定站點/容器/標籤號，避免並發重複領取。
     *
     * 注意：此方法需要在 PLC ReportReq=1 且尚未 Ack 的期間被輪詢呼叫；
     * 一旦對方送來 S065/S066 落 DB，即可在下一輪領取到。
     *
     * @param siteCode         站點
     * @param containerMainId  容器 ID
     * @param preferredLabelNo 建議的貼標號（null 則預設 1）
     */
    @Transactional
    public Optional<LabelingInfo> claimFirstReadyAfter(String siteCode, Long containerMainId, Integer preferredLabelNo) {
        long afterId = siteWatermark.getOrDefault(siteCode, 0L);

        // Mapper 對應 SQL：id > afterId AND status=READY ... FOR UPDATE LIMIT 1
        LabelingInfo picked = repo.selectReadyAfterId(siteCode, afterId).orElse(null);
        if (picked == null) return Optional.empty();

        Integer labelNo = (preferredLabelNo != null) ? preferredLabelNo : 1;

        // 綁定站點/容器/標籤號（只允許 READY 狀態更新）
        boolean ok = repo.bindToSiteAndContainer(picked.getId(), siteCode, containerMainId, labelNo);
        if (!ok) return Optional.empty(); // 競態下被其他節點搶走

        picked.setSiteCode(siteCode);
        picked.setContainerMainId(containerMainId);
        picked.setLabelNo(labelNo);
        return Optional.of(picked);
    }

    /**
     * 一般領取：找該站點「最早」的一筆 READY（不看 watermark），
     * 以 select ... for update 鎖住，然後綁定站點/容器/標籤號。
     *
     * 若你的流程不是透過 watermark，就用這支。
     */
    @Transactional
    public Optional<LabelingInfo> claimNextReadyForSite(String siteCode, Long containerMainId, Integer preferredLabelNo) {
        LabelingInfo picked = repo.selectReadyForClaim(siteCode).orElse(null);
        if (picked == null) return Optional.empty();

        Integer labelNo = (preferredLabelNo != null) ? preferredLabelNo : 1;
        boolean ok = repo.bindToSiteAndContainer(picked.getId(), siteCode, containerMainId, labelNo);
        if (!ok) return Optional.empty();

        picked.setSiteCode(siteCode);
        picked.setContainerMainId(containerMainId);
        picked.setLabelNo(labelNo);
        return Optional.of(picked);
    }

    /**
     * 依 container/site 找一筆 READY（無鎖，純查詢）
     */
    public Optional<LabelingInfo> findReady(Long containerMainId, String siteCode) {
        return repo.findReady(containerMainId, siteCode);
    }

    /**
     * 標記為 USED（印後回 Ack 成功就呼叫，避免重印）
     */
    public void markUsed(Long id) {
        repo.updateStatus(id, "USED");
    }

    /**
     * （可選）當前時間超過 expiresAt 時將狀態由 READY → EXPIRED。
     * 可在排程或補償流程中調用，避免 READY 卡住。
     */
    public void expireIfNeeded(LabelingInfo po) {
        if (po == null) return;
        if (po.getExpiresAt() != null
                && po.getExpiresAt().isBefore(LocalDateTime.now())
                && "READY".equals(po.getStatus())) {
            repo.updateStatus(po.getId(), "EXPIRED");
            log.info("[Labeling] expired: id={}, requestKey={}", po.getId(), po.getRequestKey());
        }
    }

    // =========================================================
    // ================= 事件：喚醒/補償用 ====================
    // =========================================================

    /**
     * 發佈「某站點/容器有可印標資訊」事件。
     * - 你可以在 S065/S066 upsert 後，或其他地方呼叫它。
     * - 監聽端（例如 LabelingMonitor 的 listener）在收到事件時，
     *   若 PLC 當下 ReportReq=1 且未 Ack，即可嘗試 claim + 印。
     *
     * ★ 注意：使用 ApplicationEvent 版本，需要帶 source（這裡用 this）
     */
    public void publishReadyEvent(String siteCode, Long containerMainId, Integer labelNo) {
        if (siteCode == null && containerMainId == null) return;
        publisher.publishEvent(new LabelingInfoReadyEvent(this, siteCode, containerMainId, labelNo));
    }

    // =========================================================
    // ================== ZPL 欄位解析 ========================
    // =========================================================

    /**
     * 仍保留舊版 API：只回 7 個 ZPL 欄位。
     * 內部改為呼叫 extractLabelVars()，完全相容既有使用端。
     */
    public ZplVars extractZplVars(LabelingInfo info) {
        LabelVars lv = extractLabelVars(info);
        return new ZplVars(lv.getSch(), lv.getQty(), lv.getPass(), lv.getBga(), lv.getBbi(), lv.getMark(), lv.getTpi());
    }

    /**
     * 新版：解析 payload(JSON字串) → 回傳完整列印欄位（含 S066 所有欄位）。
     *
     * payload 結構預期為：
     * {
     *   "type": "S065" | "S066",
     *   "data": { 原始欄位... },   // S065: TagInfo；S066: Message
     *   "norm": {
     *     "sch": "...",
     *     "qty": 100, "pass": 80, "bga": 10, "bbi": 1, "mark": 1, "tpi": 8
     *   }
     * }
     *
     * @param info LabelingInfo（DB 實體）
     * @return LabelVars（可直接餵給 ZPL 模板服務）
     */
    public LabelVars extractLabelVars(LabelingInfo info) {
        String s = info.getPayload();
        if (StringUtils.isBlank(s)) return LabelVars.empty();

        try {
            JsonNode root = objectMapper.readTree(s);
            String type = root.path("type").asText("");

            // 先取統一的 7 欄（norm）
            JsonNode norm = root.path("norm");
            String sch = norm.path("sch").asText("");
            int qty  = norm.path("qty").asInt(0);
            int pass = norm.path("pass").asInt(0);
            int bga  = norm.path("bga").asInt(0);
            int bbi  = norm.path("bbi").asInt(0);
            int mark = norm.path("mark").asInt(0);
            int tpi  = norm.path("tpi").asInt(0);

            // 預設空的 S066 欄位
            String lotId    = "";
            String binType  = "";
            String binCode  = "";
            String binQty   = "";
            String binTotal = "";
            String binRemark= "";
            String binClass = "";
            String barCode  = "";
            String testType = "";
            String pkg      = "";
            String snBall   = "";
            String flux     = "";
            String reflow   = "";
            String rework   = "";
            String marking  = "";
            String userTime = "";
            String spec     = "";

            JsonNode data = root.path("data");

            // 若為 S066，補齊所有可列印資訊（欄位名稱依 @JsonProperty）
            if ("S066".equalsIgnoreCase(type)) {
                lotId    = data.path("lotid").asText("");
                binType  = data.path("bintype").asText("");
                binCode  = data.path("bincode").asText("");
                binQty   = data.path("binqty").asText("");
                binTotal = data.path("bintotal").asText("");
                binRemark= data.path("binremark").asText("");
                binClass = data.path("binclass").asText("");
                barCode  = data.path("BarCode").asText(""); // 注意大小寫：BarCode
                testType = data.path("TT").asText("");      // TT
                pkg      = data.path("PKG").asText("");     // PKG
                snBall   = data.path("SNBall").asText("");  // SNBall
                flux     = data.path("Flux").asText("");    // Flux
                reflow   = data.path("Reflow").asText("");  // Reflow
                rework   = data.path("Rework").asText("");  // Rework
                marking  = data.path("Marking").asText(""); // Marking
                userTime = data.path("UserTime").asText(""); // UserTime
                spec     = data.path("Spec").asText("");     // Spec
            }

            return new LabelVars(
                    type,
                    sch, qty, pass, bga, bbi, mark, tpi,
                    lotId, binType, binCode, binQty, binTotal, binRemark, binClass,
                    barCode, testType, pkg, snBall, flux, reflow, rework, marking, userTime, spec
            );
        } catch (Exception e) {
            log.warn("extractLabelVars(): payload parse failed, payload={}", s, e);
            return LabelVars.empty();
        }
    }

    /** 回傳給 ZPL 的七個欄位（沿用舊介面，不用 record，避免 JDK 限制） */
    public static class ZplVars {
        private final String sch;
        private final int qty, pass, bga, bbi, mark, tpi;
        public ZplVars(String sch, int qty, int pass, int bga, int bbi, int mark, int tpi) {
            this.sch = sch; this.qty = qty; this.pass = pass; this.bga = bga; this.bbi = bbi; this.mark = mark; this.tpi = tpi;
        }
        public String getSch() { return sch; }
        public int getQty() { return qty; }
        public int getPass() { return pass; }
        public int getBga() { return bga; }
        public int getBbi() { return bbi; }
        public int getMark() { return mark; }
        public int getTpi() { return tpi; }
    }

    /**
     * 新增：完整列印欄位（含 S066 所有文字欄位）
     * - 若來源為 S065，S066 欄位會是空字串。
     * - 若來源為 S066，七個 norm 欄位與 S066 詳細欄位都會被填入。
     */
    public static class LabelVars {
        // 來源型別：S065 / S066
        private final String type;

        // 統一 7 欄（供舊版模板或簡單標籤使用）
        private final String sch;
        private final int qty, pass, bga, bbi, mark, tpi;

        // S066 詳細欄位（若 type=S065，則為空字串）
        private final String lotId, binType, binCode, binQty, binTotal, binRemark, binClass;
        private final String barCode, testType, pkg, snBall, flux, reflow, rework, marking, userTime, spec;

        public LabelVars(String type,
                         String sch, int qty, int pass, int bga, int bbi, int mark, int tpi,
                         String lotId, String binType, String binCode, String binQty, String binTotal, String binRemark, String binClass,
                         String barCode, String testType, String pkg, String snBall, String flux, String reflow, String rework, String marking, String userTime, String spec) {
            this.type = nz(type);
            this.sch = nz(sch);
            this.qty = qty; this.pass = pass; this.bga = bga; this.bbi = bbi; this.mark = mark; this.tpi = tpi;
            this.lotId = nz(lotId); this.binType = nz(binType); this.binCode = nz(binCode); this.binQty = nz(binQty);
            this.binTotal = nz(binTotal); this.binRemark = nz(binRemark); this.binClass = nz(binClass);
            this.barCode = nz(barCode); this.testType = nz(testType); this.pkg = nz(pkg); this.snBall = nz(snBall);
            this.flux = nz(flux); this.reflow = nz(reflow); this.rework = nz(rework); this.marking = nz(marking);
            this.userTime = nz(userTime); this.spec = nz(spec);
        }

        /** 正確補齊 25 個參數的空物件（避免 "Expected 25 arguments but found 23"） */
        public static LabelVars empty() {
            return new LabelVars(
                    "",          // type
                    "",          // sch
                    0, 0, 0, 0, 0, 0, // qty, pass, bga, bbi, mark, tpi
                    "", "", "", "", "", "", "",       // lotId, binType, binCode, binQty, binTotal, binRemark, binClass
                    "", "", "", "", "", "", "", "", "", "" // barCode, testType, pkg, snBall, flux, reflow, rework, marking, userTime, spec
            );
        }

        // getters
        public String getType() { return type; }

        public String getSch() { return sch; }
        public int getQty() { return qty; }
        public int getPass() { return pass; }
        public int getBga() { return bga; }
        public int getBbi() { return bbi; }
        public int getMark() { return mark; }
        public int getTpi() { return tpi; }

        public String getLotId() { return lotId; }
        public String getBinType() { return binType; }
        public String getBinCode() { return binCode; }
        public String getBinQty() { return binQty; }
        public String getBinTotal() { return binTotal; }
        public String getBinRemark() { return binRemark; }
        public String getBinClass() { return binClass; }
        public String getBarCode() { return barCode; }
        public String getTestType() { return testType; }
        public String getPkg() { return pkg; }
        public String getSnBall() { return snBall; }
        public String getFlux() { return flux; }
        public String getReflow() { return reflow; }
        public String getRework() { return rework; }
        public String getMarking() { return marking; }
        public String getUserTime() { return userTime; }
        public String getSpec() { return spec; }
    }

    // =========================================================
    // ======================== 私有 ===========================
    // =========================================================

    /** 依 requestKey 查既有資料（避免重複） */
    private Optional<LabelingInfo> findByRequestKey(String key) {
        return repo.findByRequestKey(key);
    }

    /**
     * 簡單 UPSERT：先查 requestKey 有沒有，有則 update，否則 insert
     * - 注意：因為 payload 為字串，你要確保 toCompactJson() 產出的字串是有效 JSON。
     * - MyBatis-Plus BaseMapper insert/update 會把有效 JSON 字串寫入 JSON 欄位（MySQL 會校驗）。
     */
    private void upsert(LabelingInfo po) {
        if (po.getId() == null) {
            var existed = findByRequestKey(po.getRequestKey());
            if (existed.isPresent()) {
                po.setId(existed.get().getId());
                repo.update(po);
            } else {
                repo.save(po);
            }
        } else {
            repo.update(po);
        }
    }

    /** 將 JsonNode 序列化為緊湊 JSON 字串（無空白/換行），確保是有效 JSON */
    private String toCompactJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            log.error("toCompactJson failed", e);
            return "{}";
        }
    }

    /** null-safe 的字串 */
    private static String nz(String s) { return s == null ? "" : s; }

    /** 將數字字串安全轉 int（空值/格式錯誤回 0） */
    private static int toInt(String s) {
        try { return s == null ? 0 : Integer.parseInt(s.trim()); }
        catch (Exception e) { return 0; }
    }
}
