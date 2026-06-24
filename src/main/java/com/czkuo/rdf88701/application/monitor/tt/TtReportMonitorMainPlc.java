package com.czkuo.rdf88701.application.monitor.tt;

import com.czkuo.rdf88701.application.service.PlcAccessService;
import com.czkuo.rdf88701.common.util.PlcDataCodec;
import com.czkuo.rdf88701.domain.repository.TtRecordItemRepository;
import com.czkuo.rdf88701.domain.repository.TtRecordRepository;
import com.czkuo.rdf88701.domain.repository.TtSignalDefRepository;
import com.czkuo.rdf88701.infra.entity.TtRecord;
import com.czkuo.rdf88701.infra.entity.TtRecordItem;
import com.czkuo.rdf88701.infra.entity.TtSignalDef;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteOrder;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已修改，// 註解已依現有實作校正。
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class TtReportMonitorMainPlc {

    /**
     * 對應 PlcAccessService 設定的 PLC 名稱（如不同請改）
     */
    private static final String PLC = "PLC-Main";

    /**
     * 每輪掃描間隔（可調）
     */
    private static final long FIXED_RATE_MS = 500;

    /**
     * 防止 scheduler re-entry
     */
    private final AtomicBoolean busy = new AtomicBoolean(false);

    private final PlcAccessService plc;
    private final TtSignalDefRepository defRepo;
    private final TtRecordRepository recordRepo;
    private final TtRecordItemRepository itemRepo;

    /**
     * 每台設備的 runtime config
     */
    private final Map<String, DeviceRuntime> devices = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // 目前依 PLC 區域載入全部 TT signal 定義。
        List<TtSignalDef> defs = defRepo.findByPlcArea(PLC);
        if (defs == null || defs.isEmpty()) {
            log.warn("[TT] no tt_signal_def found, monitor will do nothing");
            return;
        }

        // 2) 分組 (device_type, device_name)
        Map<String, List<TtSignalDef>> byDevice = new HashMap<>();
        for (TtSignalDef d : defs) {
            String key = keyOf(d.getDeviceType(), d.getDeviceName());
            byDevice.computeIfAbsent(key, k -> new ArrayList<>()).add(d);
        }

        // 3) 建立 runtime config
        for (var entry : byDevice.entrySet()) {
            List<TtSignalDef> list = entry.getValue();
            list.sort(Comparator.comparingInt(TtSignalDef::getStepNo));

            String deviceType = nz(list.get(0).getDeviceType());
            String deviceName = nz(list.get(0).getDeviceName());
            String deviceArea = nz(list.get(0).getDeviceArea());

            // 找 Index / TransferNo
            TtSignalDef indexDef = list.stream()
                    .filter(x -> "Index".equalsIgnoreCase(nz(x.getStepName())))
                    .findFirst()
                    .orElse(null);
            if (indexDef == null) {
                log.warn("[TT] skip {}: Index def not found", entry.getKey());
                continue;
            }

            TtSignalDef transferNoDef = list.stream()
                    .filter(x -> "Transfer No.".equalsIgnoreCase(nz(x.getStepName())))
                    .findFirst()
                    .orElse(null);

            // baseAddr = 最小 Wxxxx（你這張表每台設備基本都是連續 10 或 16 words）
            String baseAddr = list.stream()
                    .map(TtSignalDef::getPlcWord)
                    .min(Comparator.comparingInt(TtReportMonitorMainPlc::plcWordToInt))
                    .orElse(null);
            if (baseAddr.isEmpty()) {
                log.warn("[TT] skip {}: base addr not found", entry.getKey());
                continue;
            }

            // wordsCount：用 max(step_no)（避免你沒塞空白 step 時出錯）
            int maxStepNo = list.stream().mapToInt(TtSignalDef::getStepNo).max().orElse(0);
            if (maxStepNo <= 0) {
                log.warn("[TT] skip {}: invalid maxStepNo={}", entry.getKey(), maxStepNo);
                continue;
            }

            // lastIndex：從 DB 對齊（避免重啟重灌）
            String lastIndex = recordRepo.findLastIndex(deviceType, deviceName).orElse("0");
            if (lastIndex.split("_").length > 1)
                lastIndex = lastIndex.split("_")[1];

            DeviceRuntime rt = new DeviceRuntime(
                    deviceType, deviceName,
                    baseAddr, maxStepNo,
                    transferNoDef, indexDef,
                    list, Integer.parseInt(lastIndex), deviceArea
            );

            devices.put(entry.getKey(), rt);
            log.info("[TT] init {} base={} words={} lastIndex={}",
                    entry.getKey(), baseAddr, maxStepNo, lastIndex);
        }
    }

    @Scheduled(fixedRate = FIXED_RATE_MS, initialDelay = 1000)
    public void tick() {
        if (!busy.compareAndSet(false, true)) return;
        try {
            for (DeviceRuntime d : devices.values()) {
                processOneDevice(d);
            }
        } catch (Exception e) {
            log.error("[TT] monitor error", e);
        } finally {
            busy.set(false);
        }
    }

    private void processOneDevice(DeviceRuntime d) {
        // 1) 讀 Index
        int currIndex = plc.readInt16(PLC, d.indexDef.getPlcWord());
        if (currIndex < 0 || currIndex == d.lastIndex) return;

        // 2) 讀整段 block
        byte[] block = plc.readBytes(PLC, d.baseAddr, d.wordsCount * 2);
        if (block == null || block.length < d.wordsCount * 2) {
            log.warn("[TT-{}-{}] read block fail base={} words={} len={}",
                    d.deviceType, d.deviceName, d.baseAddr, d.wordsCount,
                    (block == null ? -1 : block.length));
            return;
        }
        int[] words = PlcDataCodec.bytesToWords(block);
        String strIndex = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "_" + currIndex;
        // 3) 防重（重啟/抖動/Index回跳）
        if (recordRepo.existsByDeviceAndIndex(d.deviceType, d.deviceName, strIndex)) {
            d.lastIndex = currIndex;
            return;
        }

        // 4) transfer_no（可為 null）
        Integer transferNo = null;
        if (d.transferNoDef != null) {
            int pos = d.transferNoDef.getStepNo() - 1;
            if (pos >= 0 && pos < words.length) {
                transferNo = words[pos] & 0xFFFF;
            }
        }
        byte[] data = PlcDataCodec.wordsToBytes(words);
        String remarkId = null;
        remarkId = PlcDataCodec.decodeString(Arrays.copyOfRange(data, 0, 40), ByteOrder.LITTLE_ENDIAN);
        // 5) 寫 tt_record
        TtRecord rec = new TtRecord();
        rec.setDeviceType(d.deviceType);
        rec.setDeviceName(d.deviceName);
        rec.setPlcGroup(d.baseAddr);         // 你原表第一個 word 當 group
        rec.setTtIndex(strIndex);
        rec.setTransferNo(transferNo);
        rec.setCreatedTime(LocalDateTime.now());
        rec.setDeviceArea(d.deviceArea);
        if (!remarkId.isEmpty())
            rec.setRemarkId(remarkId);
        if (!recordRepo.save(rec)) {
            log.warn("[TT-{}-{}] save tt_record fail index={}", d.deviceType, d.deviceName, strIndex);
            return;
        }
        // rec.getId() 需靠 MyBatis-Plus 回填（通常會回填）
        Long recordId = rec.getId();
        if (recordId == null) {
            log.warn("[TT-{}-{}] tt_record id not generated, skip items. index={}", d.deviceType, d.deviceName, strIndex);
            d.lastIndex = currIndex;
            return;
        }
//        remarkId = null;
        // 6) 寫 items：略過 Transfer No./Index/空白 step_name
        List<TtRecordItem> items = new ArrayList<>();
        for (TtSignalDef def : d.defs) {
            String stepName = nz(def.getStepName());
            if (stepName.isEmpty()) continue;
            if ("Transfer No.".equalsIgnoreCase(stepName)) continue;
            if ("Index".equalsIgnoreCase(stepName)) continue;

            int pos = def.getStepNo() - 1;
            if (pos < 0 || pos >= words.length) continue;
            if ("ID".equals(stepName)) {
//                data = PlcDataCodec.wordsToBytes(words);
//                remarkId = PlcDataCodec.decodeString(Arrays.copyOfRange(data, pos * 2, 40), ByteOrder.BIG_ENDIAN);
                continue;
            }

            int raw = words[pos] & 0xFFFF;
            // 你定義：DEC 100=10.0s → /10
            BigDecimal timeSec = BigDecimal.valueOf(raw)
                    .divide(BigDecimal.TEN, 3, RoundingMode.HALF_UP);

            TtRecordItem it = new TtRecordItem();
            it.setRecordId(recordId);
            it.setStepNo(def.getStepNo());
            it.setStepName(stepName);
            it.setRawValue(raw);
            it.setTimeSec(timeSec);
            if (remarkId != null && !remarkId.isEmpty())
                it.setRemarkId(remarkId);
            items.add(it);
        }

        itemRepo.saveBatch(items);

        log.info("[TT-{}-{}] new record index={} transferNo={} items={}",
                d.deviceType, d.deviceName, strIndex, transferNo, items.size());

        // 7) 更新 lastIndex
        d.lastIndex = currIndex;
    }

    private static String keyOf(String deviceType, String deviceName) {
        return nz(deviceType) + "|" + nz(deviceName);
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    /**
     * W220A 轉 int 用來排序（十六進位）
     */
    private static int plcWordToInt(String plcWord) {
        if (plcWord == null) return 0;
        String s = plcWord.trim().toUpperCase(Locale.ROOT);
        if (!s.startsWith("W")) return 0;
        String hex = s.substring(1);
        try {
            return Integer.parseInt(hex, 16);
        } catch (Exception e) {
            return 0;
        }
    }

    private static class DeviceRuntime {
        final String deviceType;
        final String deviceName;
        final String baseAddr;
        final int wordsCount;
        final TtSignalDef transferNoDef;
        final TtSignalDef indexDef;
        final List<TtSignalDef> defs;
        final String deviceArea;

        volatile int lastIndex;

        DeviceRuntime(String deviceType,
                      String deviceName,
                      String baseAddr,
                      int wordsCount,
                      TtSignalDef transferNoDef,
                      TtSignalDef indexDef,
                      List<TtSignalDef> defs,
                      int lastIndex,
                      String deviceArea) {
            this.deviceType = deviceType;
            this.deviceName = deviceName;
            this.baseAddr = baseAddr;
            this.wordsCount = wordsCount;
            this.transferNoDef = transferNoDef;
            this.indexDef = indexDef;
            this.defs = defs;
            this.lastIndex = lastIndex;
            this.deviceArea = deviceArea;
        }
    }
}
