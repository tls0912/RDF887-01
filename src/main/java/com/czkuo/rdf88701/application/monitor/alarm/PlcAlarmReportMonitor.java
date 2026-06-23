package com.czkuo.rdf88701.application.monitor.alarm;

import com.czkuo.rdf88701.application.service.PlcAccessService;
import com.czkuo.rdf88701.application.service.mqtt.MqttCommandService;
import com.czkuo.rdf88701.application.service.process.DeviceProcessStateReader;
import com.czkuo.rdf88701.common.dto.MqttSendResult;
import com.czkuo.rdf88701.common.enums.ProcessStatus;
import com.czkuo.rdf88701.domain.repository.AlarmItemLogRepository;
import com.czkuo.rdf88701.domain.repository.AlarmItemRepository;
import com.czkuo.rdf88701.infra.entity.AlarmItem;
import com.czkuo.rdf88701.infra.entity.AlarmItemLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * PLC→System：Alarm/Warning 現況同步（1W 一筆；第32筆為 index）
 * <p>
 * 讀取 32W（31 code + index）→ 差異化更新 DB → 把讀到的 index 原值回寫到「我們的 ACK 位址」
 * - Alarm 區：資料 W10C0..W10DF，ACK 回寫 W00FE
 * - Warning 區：資料 W10E0..W10FF，ACK 回寫 W00FF
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlcAlarmReportMonitor {

    private static final String PLC = "PLC-Main";

    // ===== 資料區（各 32W）=====
    private static final String disenable_ALARM_DATA_BASE = "W1060"; // W10C0..W10DF（32W）
    private static final String ALARM_DATA_BASE = "W10C0"; // W10C0..W10DF（32W）
    private static final String WARN_DATA_BASE = "W10E0"; // W10E0..W10FF（32W）

    // ===== 我方 ACK 位址（回填 PLC 給的 index 原值）=====
    private static final String ALARM_ACK_ADDR = "W00FE";
    private static final String WARN_ACK_ADDR = "W00FF";

    private static final int WORDS_PER_BLOCK = 32; // 31 筆 + index
    private static final int MAX_CODES = 31;

    /**
     * Omron/Mitsubishi 常見大端；若底層已處理可不理此設定
     */
    private static final boolean BIG_ENDIAN = false;

    private final PlcAccessService plc;
    private final AlarmItemRepository itemRepo;
    private final AlarmItemLogRepository logRepo;
    private final MqttCommandService mqtt; // 發 S007/S008

    /**
     * 狀態讀取器，從快取取設備狀態
     */
    private final DeviceProcessStateReader stateReader;

    /**
     * 是否啟用 MQTT 推送；可快速關閉（預設 true）
     */
    @Value("${alarm.push.enabled:true}")
    private boolean pushEnabled;

    /**
     * 目標系統（seec/ase），預設 ase
     */
    @Value("${mqtt.alarm.target:ase}")
    private String alarmTargetSystem;

    private final AtomicBoolean busy = new AtomicBoolean(false);

    /**
     * 只在 index 改變時處理（記憶體快取；若需跨重啟請改存 DB/Redis）
     */
    private volatile int lastAlarmIndex = -1;
    private volatile int lastWarnIndex = -1;
    private final Map<String, AlarmFreezeState> alarmMember = new ConcurrentHashMap<String, AlarmFreezeState>(Map.of(
            "拆併區", new AlarmFreezeState(),
            "WIP", new AlarmFreezeState(),
            "ZIPA", new AlarmFreezeState(),
            "ZIPB", new AlarmFreezeState()
    ));

    @Scheduled(fixedDelay = 200, initialDelay = 1000)
    public void tick() {
        if (!busy.compareAndSet(false, true)) return;
        try {
            byte[] temp = plc.readBytes(PLC, disenable_ALARM_DATA_BASE, 2);
            if (temp == null || temp.length < 2)
                return;
            int index = toWords(temp)[0];
            if (index > 0)
                return;

            processArea("ALARM", ALARM_DATA_BASE, ALARM_ACK_ADDR, true);
            processArea("WARNING", WARN_DATA_BASE, WARN_ACK_ADDR, false);
        } catch (Exception e) {
            log.error("[PLC→SYS] monitor error: {}", e.getMessage(), e);
        } finally {
            busy.set(false);
        }
    }

    @Transactional
    protected void processArea(String typeName, String dataBase, String ackAddr, boolean isAlarm) {
        // 讀整塊 32W（64 bytes）
        byte[] block = plc.readBytes(PLC, dataBase, WORDS_PER_BLOCK * 2);
        if (block == null || block.length < WORDS_PER_BLOCK * 2) return;

        // 解析前 31 筆為 unsigned 16-bit；第 32 筆為 index
        int[] words = toWords(block);
        int index = words[WORDS_PER_BLOCK - 1] & 0xFFFF;
        alarmMember.forEach((area, state) -> {
            if (!shouldSuppressTriggerByDeviceState(area)) {
                state.frozen = false;
            }
            state.stopLatched = false;
        });

        // 若 index 與上次相同，跳過
        int last = isAlarm ? lastAlarmIndex : lastWarnIndex;
        if (index == last) return;

        // 目前 ON 的 codes（非 0）；去重保持順序
        Set<Integer> currentOn = new LinkedHashSet<>();
        for (int i = 0; i < MAX_CODES; i++) {
            int code = words[i] & 0xFFFF;
            if (code != 0) currentOn.add(code);
        }

        // 取出 DB 目前 is_triggered=1 的同型別清單做差異
        List<AlarmItem> snap = itemRepo.findTriggeredSnapshot(32_000);
        Set<Integer> prevOn = snap.stream()
                .filter(x -> isAlarm ? "ALARM".equals(x.getType()) : "WARNING".equals(x.getType()))
                .map(AlarmItem::getLocalCode)
                .collect(Collectors.toSet());

        List<AlarmItemLog> logs = new ArrayList<>();
        final List<Runnable> afterCommitSends = new ArrayList<>(); // 交易完成後才送
        // 新觸發（TRIGGER）
        for (Integer code : diff(currentOn, prevOn)) {
            Optional<AlarmItem> opt = itemRepo.findByLocalCode(code);
            if (opt.isEmpty()) continue;

            AlarmItem it = opt.get();

            // 設備 STOP 時，新 TRIGGER 不記錄、不發、不更新 triggered
            String dev = resolveDeviceNameForState(it);
            AlarmFreezeState state = alarmMember.computeIfAbsent(dev, k -> new AlarmFreezeState());
            if (shouldSuppressTriggerByDeviceState(dev)) {
                state.stopLatched = true;
                if (state.frozen) {
                    log.info("[PLC→SYS] suppress TRIGGER because device STOP: type={}, code={}, device={}",
                            (isAlarm ? "ALARM" : "WARNING"), code, dev);
                    continue;
                }
                log.info("[PLC→SYS] suppress TRIGGER because device STOP report first round : type={}, code={}, device={}",
                        (isAlarm ? "ALARM" : "WARNING"), code, dev);
            }

            if (itemRepo.setTriggeredIfChanged(code + 10000, true)) {
                logs.add(buildLog(it, "TRIGGER"));
                enqueueMqttIfAllowed(afterCommitSends, it, isAlarm, true);
            }
        }

        // 清除（CLEAR）
        for (Integer code : diff(prevOn, currentOn)) {
            if (itemRepo.setTriggeredIfChanged(code + 10000, false)) {
                itemRepo.findByLocalCode(code).ifPresent(it -> {
                    logs.add(buildLog(it, "CLEAR"));
                    enqueueMqttIfAllowed(afterCommitSends, it, isAlarm, false);
                });
            }
        }
        if (!logs.isEmpty()) logRepo.saveBatch(logs);

        alarmMember.forEach((area, state) -> {
            if (shouldSuppressTriggerByDeviceState(area))
                state.stopLatched = true;
            if (state.stopLatched)
                state.frozen = true;
        });
        // 回寫 ACK：把 PLC 的 index 原值寫到我們的 ACK 位址
        writeWordU16(PLC, ackAddr, index);

        // 記錄 index
        if (isAlarm) lastAlarmIndex = index;
        else lastWarnIndex = index;

        log.info("[PLC→SYS] {} handled idx={} onCodes={}", typeName, index, currentOn);

        // 在交易提交後才真正送 MQTT（避免回滾造成誤報）
        // if (pushEnabled && !afterCommitSends.isEmpty()) {
        //     final List<Runnable> toRun = new ArrayList<>(afterCommitSends); // ★ final 複製，避免 lambda 抱怨
        //     TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        //         @Override public void afterCommit() {
        //             toRun.forEach(r -> {
        //                 try { r.run(); } catch (Exception e) {
        //                     log.error("[MQTT] send after-commit failed", e);
        //                 }
        //             });
        //         }
        //     });
        // }
    }

    // ===== helpers =====

    private int[] toWords(byte[] bytes) {
        int n = bytes.length / 2;
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            int b0 = bytes[i * 2] & 0xFF;
            int b1 = bytes[i * 2 + 1] & 0xFF;
            out[i] = BIG_ENDIAN ? ((b0 << 8) | b1) : ((b1 << 8) | b0);
        }
        return out;
    }

    private void writeWordU16(String device, String addr, int value) {
        byte[] b = new byte[2];
        if (BIG_ENDIAN) {
            b[0] = (byte) ((value >>> 8) & 0xFF);
            b[1] = (byte) (value & 0xFF);
        } else {
            b[0] = (byte) (value & 0xFF);
            b[1] = (byte) ((value >>> 8) & 0xFF);
        }
        plc.writeBytes(device, addr, b);
    }

    private static <T> Set<T> diff(Set<T> a, Set<T> b) {
        Set<T> r = new LinkedHashSet<>(a);
        r.removeAll(b);
        return r;
    }

    private static AlarmItemLog buildLog(AlarmItem it, String evt) {
        AlarmItemLog row = new AlarmItemLog();
        row.setItemId(it.getId());
        row.setGlobalCode(it.getGlobalCode());
        row.setTitleZh(it.getTitleZh());
        row.setTitleEn(it.getTitleEn());
        row.setEventType(evt); // TRIGGER / CLEAR
        return row;
    }

    // ===== MQTT 發送封裝 =====

    /**
     * 允許字串/數字/布林等多型真值判斷（1/Y/true）
     */
    private static boolean truthy(Object v) {
        if (v == null) return false;
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) return false;
        return !(s.equalsIgnoreCase("0") || s.equalsIgnoreCase("N") || s.equalsIgnoreCase("false"));
    }

    private static String nz(String s, String def) {
        return (s == null || s.isBlank()) ? def : s;
    }

    private void enqueueMqttIfAllowed(List<Runnable> out, AlarmItem it, boolean isAlarm, boolean isTrigger) {
        if (!pushEnabled) return;
        if (!truthy(it.getEnabled()) || !truthy(it.getAllowPlcTrigger())) return;

        final String suffix = isTrigger ? " (TRIGGER)" : " (CLEAR)";
        final String zhRaw = it.getTitleZh();
        final String enRaw = it.getTitleEn();
        final String zhText = (zhRaw != null && !zhRaw.isBlank()) ? zhRaw + suffix : zhRaw;
        final String enText = (enRaw != null && !enRaw.isBlank()) ? enRaw + suffix : enRaw;

        final String alid = String.valueOf(it.getGlobalCode());
        final String device = nz(it.getEquipment(), PLC);
        final String alarmState = isTrigger ? "START" : "END"; // S007/S008 都要帶 START/END
        final String target = alarmTargetSystem; // 欄位抓成 local final，避免 IDE 抱怨

        if (isAlarm) {
            MqttSendResult r = mqtt.sendS007(target, device, alid, nz(enText, ""), nz(zhText, ""), alarmState);
            log.info("[MQTT] S007 sent: alid={}, state={}, device={}, tid={}",
                    alid, alarmState, device, (r != null ? r.getTid() : "-"));
        } else {
            MqttSendResult r = mqtt.sendS008(target, device, alid, nz(enText, ""), nz(zhText, ""), alarmState);
            log.info("[MQTT] S008 sent: alid={}, state={}, tid={}",
                    alid, alarmState, (r != null ? r.getTid() : "-"));
        }

        // if (isAlarm) {
        //     out.add(() -> {
        //         // 明確型別，避免低版 JDK 下 lambda + var 的小毛病
        //         MqttSendResult r = mqtt.sendS007(target, device, alid, nz(enText, ""), nz(zhText, ""), alarmState);
        //         log.info("[MQTT] S007 sent: alid={}, state={}, device={}, tid={}",
        //                 alid, alarmState, device, (r != null ? r.getTid() : "-"));
        //     });
        // } else {
        //     out.add(() -> {
        //         MqttSendResult r = mqtt.sendS008(target, alid, nz(enText, ""), nz(zhText, ""), alarmState);
        //         log.info("[MQTT] S008 sent: alid={}, state={}, tid={}",
        //                 alid, alarmState, (r != null ? r.getTid() : "-"));
        //     });
        // }
    }

    private boolean shouldSuppressTriggerByDeviceState(String deviceName) {
        // STOP -> suppress trigger (NoData 視為 STOP/不擋 取決於你的策略；你目前 getBestEffort 預設 STOP)
        return stateReader.getBestEffort(deviceName).getStatus() == ProcessStatus.STOP;
    }

    /**
     * 針對 AlarmItem 決定用哪個 deviceName 去查狀態：
     * - 有填 equipment 就用它
     * - 沒填就用 PLC 預設（PLC-Main）
     * <p>
     * 你也可以改成用 it.getEquipmentGroup()/area 之類的欄位，只要最後能映射到 cache key。
     * String eq = nz(it.getEquipment(), "");
     */
    private String resolveDeviceNameForState(AlarmItem it) {
        // 表內 equipment 只有：WIP/ZIPA/ZIPB/FSK6001
        String eq = nz(it.getEquipment(), "");
        if ("FSK6001".equalsIgnoreCase(eq)) return "拆併區";
        return eq; // WIP / ZIPA / ZIPB
    }

    static class AlarmFreezeState {
        boolean frozen = false;
        boolean stopLatched = false;
        int lastIndex = -1;
    }
}
