package com.czkuo.rdf88701.application.service.ocr;

import com.czkuo.rdf88701.domain.dto.ocr.*;
import com.czkuo.rdf88701.domain.repository.OcrAlarmRepository;
import com.czkuo.rdf88701.domain.repository.OcrDeviceRepository;
import com.czkuo.rdf88701.domain.repository.OcrTaskRepository;
import com.czkuo.rdf88701.infra.entity.OcrAlarm;
import com.czkuo.rdf88701.infra.entity.OcrDevice;
import com.czkuo.rdf88701.infra.entity.OcrTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

/**
 * OCR 回呼處理：把事件映射到最小資料表（ocr_device / ocr_task / ocr_alarm）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OcrEventService {

    private final OcrTaskRepository ocrTaskRepository;
    private final OcrDeviceRepository ocrDeviceRepository;
    // 允許沒有 alarm repo 的情況（尚未實作也不會 NPE）
    private final Optional<OcrAlarmRepository> ocrAlarmRepository;

    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED  = "FAILED";
    private static final String DEV_IDLE  = "IDLE";
    private static final String DEV_BUSY  = "BUSY";
    private static final String DEV_ERROR = "ERROR";
    private static final String DEV_MAINT = "MAINTENANCE";
    private static final String DEV_OFF   = "OFFLINE";

    /** 任務開始：upsert 任務、更新裝置（BUSY） */
    @Transactional
    public void onTaskStarted(OcrTaskStartedBody body) {
        log.info("[OCR] onTaskStarted: {}", body);

        // 基本欄位保護
        if (body == null || body.getTaskId() == null || body.getOcrDeviceId() == null) {
            log.warn("[OCR] TaskStarted 缺少必填欄位，忽略。body={}", body);
            return;
        }

        final Long taskId = body.getTaskId();
        final Integer deviceId = body.getOcrDeviceId();
        final LocalDateTime startTime = nz(body.getStartTime(), LocalDateTime.now());

        // 未知任務保護
        Optional<OcrTask> existedOpt = ocrTaskRepository.findById(taskId);
        if (existedOpt.isEmpty()) {
            log.warn("[OCR] TaskStarted 收到未知 taskId={}，已忽略（acceptUnknownTaskCallback=false）。", taskId);
            // 仍可刷新裝置狀態（避免 UI 卡住）
            upsertDevice(deviceId, DEV_BUSY, startTime, false);
            return;
        }

        // 1) 任務 upsert
        OcrTask task = existedOpt.orElseGet(() -> {
            OcrTask t = new OcrTask();
            t.setId(taskId);
            t.setOcrDeviceId(deviceId);
            // 如果沒有 createdTime，只能先用 startTime 當基準
            t.setCreatedTime(startTime != null ? startTime : LocalDateTime.now());
            return t;
        });
        task.setOcrDeviceId(deviceId);
        task.setStatus(STATUS_RUNNING);
        // 單調性：startedTime 取較早（避免回退）
        task.setStartedTime(min(task.getStartedTime(), startTime));

        if (ocrTaskRepository.findById(taskId).isEmpty()) {
            ocrTaskRepository.save(task);
        } else {
            ocrTaskRepository.update(task);
        }

        // 2) 裝置 upsert：標記 BUSY / acceptingTask=false
        upsertDevice(deviceId, DEV_BUSY, startTime, false);
    }

    /** 任務完成：依 SUCCESS/FAILED 更新任務；裝置更新 lastActive（並預設回 IDLE） */
    @Transactional
    public void onTaskCompleted(OcrTaskCompletedBody body) {
        log.info("[OCR] onTaskCompleted: {}", body);

        // 基本欄位保護
        if (body == null || body.getTaskId() == null || body.getOcrDeviceId() == null || body.getStatus() == null) {
            log.warn("[OCR] TaskCompleted 缺少必填欄位，忽略。body={}", body);
            return;
        }

        final Long taskId = body.getTaskId();
        final Integer deviceId = body.getOcrDeviceId();
        final LocalDateTime completed = nz(body.getCompletedTime(), LocalDateTime.now());
        final String status = normalize(body.getStatus());

        // 未知任務保護
        Optional<OcrTask> existedOpt = ocrTaskRepository.findById(taskId);
        if (existedOpt.isEmpty()) {
            log.warn("[OCR] TaskCompleted 收到未知 taskId={}（status={}），已忽略（acceptUnknownTaskCallback=false）。", taskId, status);
            // 預設讓裝置回 IDLE，可接案
            upsertDevice(deviceId, DEV_IDLE, completed, true);
            return;
        }

        // 1) 任務 upsert
        OcrTask task = existedOpt.orElseGet(() -> {
            OcrTask t = new OcrTask();
            t.setId(taskId);
            t.setOcrDeviceId(deviceId);
            // 若前面沒「任務開始」，至少建一筆，created_time 退而求其次用 completed
            t.setCreatedTime(completed != null ? completed : LocalDateTime.now());
            return t;
        });

        // 亂序 / 重複防護：若已終態且新完成時間不晚於舊完成時間、且狀態相同 → 視作重送，略過任務更新
        if (isTerminal(task.getStatus())
                && task.getCompletedTime() != null
                && !completed.isAfter(task.getCompletedTime())
                && status.equalsIgnoreCase(task.getStatus())) {
            log.info("[OCR] TaskCompleted 重複/過期回呼忽略：taskId={}, old(status={}, completed={}), new(status={}, completed={})",
                    taskId, task.getStatus(), task.getCompletedTime(), status, completed);
            // 仍可刷新裝置（避免 UI 卡住）
            upsertDevice(deviceId, DEV_IDLE, completed, true);
            return;
        }

        task.setOcrDeviceId(deviceId);
        task.setContainerMainId(body.getContainerId());
        task.setStatus(status);
        // 單調性：completedTime 取較晚（避免回退）
        task.setCompletedTime(max(task.getCompletedTime(), completed));

        if (STATUS_SUCCESS.equals(status)) {
            task.setOcrText1(body.getOcrText1());
            task.setOcrText2(body.getOcrText2());
            if (body.getTimingBreakdown() != null) {
                task.setTimingCaptureMs(body.getTimingBreakdown().getCaptureTime());
                task.setTimingOcrProcessingMs(body.getTimingBreakdown().getOcrProcessing());
                task.setTimingPackagingMs(body.getTimingBreakdown().getResultPackaging());
            } else {
                task.setTimingCaptureMs(null);
                task.setTimingOcrProcessingMs(null);
                task.setTimingPackagingMs(null);
            }
            task.setErrorMessage(null);
        } else if (STATUS_FAILED.equals(status)) {
            task.setOcrText1(null);
            task.setOcrText2(null);
            task.setTimingCaptureMs(null);
            task.setTimingOcrProcessingMs(null);
            task.setTimingPackagingMs(null);
            task.setErrorMessage(body.getErrorMessage());
        }

        if (ocrTaskRepository.findById(taskId).isEmpty()) {
            ocrTaskRepository.save(task);
        } else {
            ocrTaskRepository.update(task);
        }

        // 2) 裝置：刷新 lastActive；預設回 IDLE 可接案（若你想嚴格以裝置事件為準，可把這行改成 BUSY→不動）
        upsertDevice(deviceId, DEV_IDLE, completed, true);
    }

    /** 設備狀態變更：upsert ocr_device */
    @Transactional
    public void onDeviceStatusChanged(OcrDeviceStatusChangedBody body) {
        log.info("[OCR] onDeviceStatusChanged: {}", body);

        // 基本欄位保護
        if (body == null || body.getOcrDeviceId() == null || body.getStatus() == null) {
            log.warn("[OCR] DeviceStatusChanged 缺少必填欄位，忽略。body={}", body);
            return;
        }

        final Integer deviceId = body.getOcrDeviceId();
        final String status = normalizeDev(body.getStatus());
        final LocalDateTime ts = nz(body.getTimestamp(), LocalDateTime.now());

        // 規則：IDLE=可接案(true)，其餘=false；若你要保留遠端的 acceptingTask，可改成 null=不覆蓋
        final boolean accepting = DEV_IDLE.equals(status);
        upsertDevice(deviceId, status, ts, accepting);
    }

    /** 警報推送：插入 ACTIVE（若已存在同筆唯一鍵則略過） */
    @Transactional
    public void onAlarmRaised(OcrAlarmRaisedBody body) {
        log.info("[OCR] onAlarmRaised: {}", body);

        // 基本欄位保護
        if (body == null || body.getOcrDeviceId() == null || body.getAlarmCode() == null || body.getTimestamp() == null) {
            log.warn("[OCR] AlarmRaised 缺少必填欄位，忽略。body={}", body);
            return;
        }

        if (ocrAlarmRepository.isEmpty()) {
            log.warn("[OCR] OcrAlarmRepository 未註冊，僅記錄 log，不落 DB");
            return;
        }

        try {
            OcrAlarm alarm = new OcrAlarm();
            alarm.setOcrDeviceId(body.getOcrDeviceId());
            alarm.setAlarmCode(body.getAlarmCode());
            alarm.setMessage(body.getMessage());
            alarm.setStatus("ACTIVE");
            // schema 用 occurred_time；對應 DTO 的 timestamp
            alarm.setOccurredTime(body.getTimestamp());
            alarm.setClearedTime(null);
            ocrAlarmRepository.get().save(alarm);
        } catch (DataIntegrityViolationException dup) {
            // 命中 UNIQUE(ocr_device_id, alarm_code, occurred_time) 即代表重覆上報，同筆略過即可
            //log.debug("[OCR] alarm duplicate ignored (device={}, code={}, ts={})",
//                    body.getOcrDeviceId(), body.getAlarmCode(), body.getTimestamp());
        }
    }

    /** 警報解除：插入 CLEARED（若需回填 ACTIVE 的 cleared_time，待 repo 提供查詢/更新 API 再升級） */
    @Transactional
    public void onAlarmCleared(OcrAlarmClearedBody body) {
        log.info("[OCR] onAlarmCleared: {}", body);

        // 基本欄位保護
        if (body == null || body.getOcrDeviceId() == null || body.getAlarmCode() == null || body.getClearedTime() == null) {
            log.warn("[OCR] AlarmCleared 缺少必填欄位，忽略。body={}", body);
            return;
        }
        if (ocrAlarmRepository.isEmpty()) {
            log.warn("[OCR] OcrAlarmRepository 未註冊，僅記錄 log，不落 DB");
            return;
        }

        try {
            // 簡化作法：另存一筆 CLEARED 記錄（事件型）；避免對 schema 有依賴
            OcrAlarm cleared = new OcrAlarm();
            cleared.setOcrDeviceId(body.getOcrDeviceId());
            cleared.setAlarmCode(body.getAlarmCode());
            cleared.setMessage(body.getMessage());   // 選填
            cleared.setStatus("CLEARED");
            cleared.setOccurredTime(null);           // 解除事件不設定 occurred_time
            cleared.setClearedTime(body.getClearedTime());
            ocrAlarmRepository.get().save(cleared);
        } catch (DataIntegrityViolationException dup) {
            // 若你有 UNIQUE(device, alarm_code, cleared_time) 或其他唯一鍵，重送會到這裡
            //log.debug("[OCR] alarm cleared duplicate ignored (device={}, code={}, ts={})",
//                    body.getOcrDeviceId(), body.getAlarmCode(), body.getClearedTime());
        }

        // 可選：若你想在「最後一個 ACTIVE 被解除」時把裝置狀態回復，
        // 建議由 /ocr-device-status-changed 主導；或在 repo 增加 hasActiveAlarmsByDevice(...) 後再於此決策。
        // 例如：
        // if (ocrAlarmRepository.get().countActiveByDevice(body.getOcrDeviceId()) == 0) {
        //     upsertDevice(body.getOcrDeviceId(), DEV_IDLE, body.getTimestamp(), true);
        // }
    }

    // ------------------------ Helper ------------------------

    private void upsertDevice(Integer deviceId, String status, LocalDateTime when, Boolean accepting) {
        if (deviceId == null) return;
        OcrDevice dev = ocrDeviceRepository.findById(deviceId).orElseGet(() -> {
            OcrDevice d = new OcrDevice();
            d.setId(deviceId);
            return d;
        });
        dev.setStatus(status);
        // ★ 單調性：lastActiveTime 取較晚（避免回退）
        LocalDateTime original = dev.getLastActiveTime();
        LocalDateTime candidate = (when != null ? when : LocalDateTime.now());
        dev.setLastActiveTime(max(original, candidate));
        if (accepting != null) {
            // Entity 欄位若為 Boolean（建議），可直接 set；若是 Integer，改成 setAcceptingTask(accepting ? 1 : 0)
            dev.setAcceptingTask(accepting);
        }
        if (ocrDeviceRepository.findById(deviceId).isEmpty()) {
            ocrDeviceRepository.save(dev);
        } else {
            ocrDeviceRepository.update(dev);
        }
    }

    private static String normalize(String s) {
        if (s == null) return STATUS_FAILED;
        String v = s.trim().toUpperCase(Locale.ROOT);
        return (STATUS_SUCCESS.equals(v) || STATUS_FAILED.equals(v)) ? v : STATUS_FAILED;
    }

    private static String normalizeDev(String s) {
        if (s == null) return DEV_OFF;
        String v = s.trim().toUpperCase(Locale.ROOT);
        return switch (v) {
            case DEV_IDLE, DEV_BUSY, DEV_ERROR, DEV_MAINT, DEV_OFF -> v;
            default -> DEV_OFF;
        };
    }

    // ★ 小工具：null-first 的擇一
    private static LocalDateTime nz(LocalDateTime... candidates) {
        for (LocalDateTime c : candidates) if (c != null) return c;
        return null;
    }

    // ★ 小工具：時間取最小/最大，避免回退
    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isBefore(b) ? a : b;
    }

    private static LocalDateTime max(LocalDateTime a, LocalDateTime b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }

    // ★ 小工具：是否終態
    private static boolean isTerminal(String s) {
        if (s == null) return false;
        String v = s.trim().toUpperCase(Locale.ROOT);
        return STATUS_SUCCESS.equals(v) || STATUS_FAILED.equals(v);
    }
}
