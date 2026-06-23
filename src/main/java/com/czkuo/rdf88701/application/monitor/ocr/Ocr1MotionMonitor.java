package com.czkuo.rdf88701.application.monitor.ocr;

import com.czkuo.rdf88701.application.service.PlcAccessService;
import com.czkuo.rdf88701.domain.plc.state.transfer.TransferDeviceStatus;
import com.czkuo.rdf88701.domain.repository.ContainerAttrRepository;
import com.czkuo.rdf88701.domain.repository.ContainerDataRepository;
import com.czkuo.rdf88701.domain.repository.LocationTrackingRepository;
import com.czkuo.rdf88701.infra.cache.TransferStatusCache;
import com.czkuo.rdf88701.infra.entity.ContainerAttr;
import com.czkuo.rdf88701.infra.entity.ContainerData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

import static com.czkuo.rdf88701.application.monitor.ocr.Ocr1Io.*;

/**
 * Ocr1MotionMonitor
 * -----------------------------------------------------------------------------
 * 只負責「PLC 動作」：
 *  - 判斷是否需要 OCR：若需要→將 TR#3 送到 OCR 取像位（Bay=2），並收斂 COMP 交握回到乾淨 IDLE。
 *  - 若不需要 OCR 或無容器或不在 V#5 → 維持平常位（Bay=1），並確保所有寫入位 OFF。
 *  - 不觸發 OCR 任務、不送 Collect（完全不碰辨識流程）。
 *
 * 重要交握原則：
 *  - s==3（W_STATUS）代表 MOVE 完成；通常伴隨 B_COMP_REQ=1 → 必須回 B_COMP_ACK=1；
 *    當 B_COMP_REQ 關閉後再把 B_COMP_ACK 關掉，狀態回 s==1(IDLE)。
 *  - 「下命令」僅能在 Standby=true 且 s==1 時進行；Standby=false 不做 re-arm。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Ocr1MotionMonitor {

    private final PlcAccessService plc;
    private final TransferStatusCache statusCache;
    private final LocationTrackingRepository locationTrackingRepository;
    private final ContainerDataRepository containerDataRepository;
    private final ContainerAttrRepository containerAttrRepository;

    // 狀態旗標
    private Long    currentContainerId = null;
    private boolean loweringInProgress = false;
    private boolean raisingInProgress  = false;

    // 內部時間戳（watchdog）
    private long cmdReqSentAt = 0;
    private long compAckSetAt = 0;

    @Scheduled(fixedDelay = 400)
    public void monitor() {
        try {
            // 0) 先排水 COMP 交握（不看 Standby）：避免殘留卡住
            if (drainCompHandshake()) return;

            // 0.5) CMD_ACK 逾時回收
            watchdog();

            // 1) 無容器 → 清狀態 + 確保寫入位 OFF + 盡量升回（若可下命）
            Optional<Long> cmOpt = locationTrackingRepository.findContainerOnTransfer(TRANSFER_ID);
            if (cmOpt.isEmpty()) {
                clearMotionFlags();
                ensureAllWritesOffSoft();
                idleUp();
                return;
            }
            Long cmId = cmOpt.get();

            // 2) 是否位於 V#5（以 cache level 判斷）
            TransferDeviceStatus ds = statusCache.getLatest(TRANSFER_NAME);
            boolean fresh = ds != null && ds.isValidAndComplete(3);
            Integer lvl = fresh ? safeGetLevel(ds) : null;
            if (!(fresh && lvl != null && lvl == TARGET_LEVEL)) {
                clearMotionFlags();
                ensureAllWritesOffSoft();
                idleUp();
                return;
            }

            // 3) 判斷是否需要 OCR（兩欄都空才需要）
            boolean needOcr = needsOcr(cmId);
            if (!needOcr) {
                // 不需要 → 回歸平常位與乾淨狀態
                clearMotionFlags();
                ensureAllWritesOffSoft();
                idleUp();
                return;
            }

            // 4) 切換容器 → 清移動旗標
            if (!cmId.equals(currentContainerId)) {
                currentContainerId = cmId;
                loweringInProgress = false;
                raisingInProgress  = false;
                log.info("[MOTION] TR#3@V5，新容器 cm#{}", cmId);
            }

            // 5) 正常補償（CMD/COMP 三段）
            if (compensate()) return;

            // 6) 僅在 Standby & IDLE 時才能下命
            if (!deviceIdleAndStandby()) return;

            // 7) 尚未在下降位 → MOVE(DOWN)
            if (!isAtDown()) {
                if (!loweringInProgress) {
                    moveToBay(cmId, BAY_DOWN);
                    loweringInProgress = true;
                }
                return;
            }

            // 8) 已在下降位：等待 COMP 收斂、回到乾淨 IDLE（s==1 且寫入位 OFF）
            if (!isCleanIdle()) return;

            // 到這裡代表：位置正確 & 乾淨 IDLE，交棒給 ResultMonitor 去觸發 OCR 任務
            // 本類不做任何辨識/Collect 動作。
        } catch (Exception e) {
            log.error("[MOTION] Monitor exception", e);
        }
    }

    // ---------- 移動/交握工具 ----------

    /** 無視 Standby 的 COMP 排水：有 B_COMP_REQ 就先回 ACK；REQ 關了再把 ACK 關掉。 */
    private boolean drainCompHandshake() {
        boolean compReq = plc.readBoolean(DEVICE, B_COMP_REQ);
        boolean compAck = plc.readBoolean(DEVICE, B_COMP_ACK);
        if (compReq && !compAck) {
            int ret = plc.readInt32(DEVICE, W_RETCODE);
            switch (ret) {
                case 0x0100 -> log.info("[MOTION] MOVE completed (ret=0x0100).");
                case 0x0800 -> log.warn("[MOTION] MOVE aborted  (ret=0x0800).");
                case 0x0F00 -> log.error("[MOTION] MOVE failed   (ret=0x0F00).");
                default     -> log.warn("[MOTION] MOVE ret=0x{}", Integer.toHexString(ret));
            }
            plc.writeBoolean(DEVICE, B_COMP_ACK, true);
            compAckSetAt = System.currentTimeMillis();
            return true;
        }
        if (!compReq && compAck) {
            if (System.currentTimeMillis() - compAckSetAt > COMP_CLOSE_TIMEOUT_MS) {
                plc.writeBoolean(DEVICE, B_COMP_ACK, false);
                loweringInProgress = false;
                raisingInProgress  = false;
                return true;
            }
        }
        return false;
    }

    /** CMD_ACK 逾時回收，避免卡在半拉位。 */
    private void watchdog() {
        long now = System.currentTimeMillis();
        if (plc.readBoolean(DEVICE, B_CMD_REQ) && !plc.readBoolean(DEVICE, B_CMD_ACK)) {
            if (now - cmdReqSentAt > CMD_ACK_TIMEOUT_MS) {
                log.warn("[MOTION] ⏱️ CMD_ACK timeout; drop CMD_REQ.");
                plc.writeBoolean(DEVICE, B_CMD_REQ, false);
                loweringInProgress = false;
                raisingInProgress  = false;
            }
        }
    }

    /** 平常維持上升位（Bay=1）。僅在 Standby & IDLE 才會下命。 */
    private void idleUp() {
        if (compensate()) return;
        if (!deviceIdleAndStandby()) return;

        int curBay = plc.readInt32(DEVICE, W_POS_BAY);
        if (curBay == BAY_UP || raisingInProgress) return;

        Long cmId = (currentContainerId != null)
                ? currentContainerId
                : locationTrackingRepository.findContainerOnTransfer(TRANSFER_ID).orElse(null);

        int bank  = plc.readInt32(DEVICE, W_POS_BANK);
        int level = plc.readInt32(DEVICE, W_POS_LEVEL);
        int mm100 = (cmId != null) ? readThicknessMm100(cmId) : DEFAULT_THICK_MMx100;
        int qty   = (cmId != null) ? readEstimatedQty(cmId)  : DEFAULT_QTY_WHEN_EMPTY;

        plc.writeBoolean(DEVICE, B_READY, true);
        plc.writeInt32(DEVICE, W_NO, cmId != null ? cmId.intValue() : NO_FOR_EMPTY);
        plc.writeInt32(DEVICE, W_TYPE, packTypeAndQty(TYPE_MOVE, qty));
        plc.writeInt32(DEVICE, W_LOC1_H, mm100);
        plc.writeInt32(DEVICE, W_LOC2_BANK, bank);
        plc.writeInt32(DEVICE, W_LOC3_BAY, BAY_UP);
        plc.writeInt32(DEVICE, W_LOC4_LEVEL, level);
        plc.writeBoolean(DEVICE, B_CMD_REQ, true);

        cmdReqSentAt = System.currentTimeMillis();
        raisingInProgress = true;
        log.info("[MOTION] ⬆️ UP to idle. cm#{} h={} q={} bank={} level={}",
                (cmId == null ? "(none)" : cmId), mm100, qty, bank, level);
    }

    /** 下發 MOVE 到目標 Bay；後續靠 COMP 交握收斂。 */
    private void moveToBay(Long cmId, int bay) {
        int bank  = plc.readInt32(DEVICE, W_POS_BANK);
        int level = plc.readInt32(DEVICE, W_POS_LEVEL);
        int mm100 = (cmId != null) ? readThicknessMm100(cmId) : DEFAULT_THICK_MMx100;
        int qty   = (cmId != null) ? readEstimatedQty(cmId)  : DEFAULT_QTY_WHEN_EMPTY;

        plc.writeBoolean(DEVICE, B_READY, true);
        plc.writeInt32(DEVICE, W_NO, cmId != null ? cmId.intValue() : NO_FOR_EMPTY);
        plc.writeInt32(DEVICE, W_TYPE, packTypeAndQty(TYPE_MOVE, qty));
        plc.writeInt32(DEVICE, W_LOC1_H, mm100);
        plc.writeInt32(DEVICE, W_LOC2_BANK, bank);
        plc.writeInt32(DEVICE, W_LOC3_BAY, bay);
        plc.writeInt32(DEVICE, W_LOC4_LEVEL, level);
        plc.writeBoolean(DEVICE, B_CMD_REQ, true);

        cmdReqSentAt = System.currentTimeMillis();
        log.info("[MOTION] ▶️ MOVE bay={} cm#{} h={} q={} bank={} level={}", bay, cmId, mm100, qty, bank, level);
    }

    /** 正常三段補償（優先回收 CMD_REQ、處理 COMP_REQ→ACK、放掉 COMP_ACK）。 */
    private boolean compensate() {
        boolean cmdAck  = plc.readBoolean(DEVICE, B_CMD_ACK);
        boolean compReq = plc.readBoolean(DEVICE, B_COMP_REQ);
        boolean compAck = plc.readBoolean(DEVICE, B_COMP_ACK);
        int ret = plc.readInt32(DEVICE, W_RETCODE);

        if (cmdAck) {
            if (plc.readBoolean(DEVICE, B_CMD_REQ)) plc.writeBoolean(DEVICE, B_CMD_REQ, false);
            return true;
        }
        if (compReq && !compAck) {
            switch (ret) {
                case 0x0100 -> log.info("[MOTION] MOVE success.");
                case 0x0800 -> log.warn("[MOTION] MOVE abort.");
                case 0x0F00 -> log.error("[MOTION] MOVE fail.");
                default     -> log.warn("[MOTION] MOVE ret=0x{}", Integer.toHexString(ret));
            }
            plc.writeBoolean(DEVICE, B_COMP_ACK, true);
            compAckSetAt = System.currentTimeMillis();
            return true;
        }
        if (!compReq && compAck) {
            if (System.currentTimeMillis() - compAckSetAt > COMP_CLOSE_TIMEOUT_MS) {
                plc.writeBoolean(DEVICE, B_COMP_ACK, false);
                loweringInProgress = false;
                raisingInProgress  = false;
                return true;
            }
        }
        return false;
    }

    /** 僅關閉「我們寫」的位（CMD_REQ/COMP_ACK/COLLECT_REQ）。 */
    private void ensureAllWritesOffSoft() {
        try {
            if (plc.readBoolean(DEVICE, B_CMD_REQ))     plc.writeBoolean(DEVICE, B_CMD_REQ, false);
            if (plc.readBoolean(DEVICE, B_COLLECT_REQ)) plc.writeBoolean(DEVICE, B_COLLECT_REQ, false);
            if (!plc.readBoolean(DEVICE, B_COMP_REQ) && plc.readBoolean(DEVICE, B_COMP_ACK)) {
                plc.writeBoolean(DEVICE, B_COMP_ACK, false);
            }
        } catch (Exception ignore) {}
    }

    private void clearMotionFlags() {
        loweringInProgress = false;
        raisingInProgress  = false;
    }

    // ---------- 判斷工具 ----------

    private boolean deviceIdleAndStandby() {
        if (!plc.readBoolean(DEVICE, B_STANDBY)) {
            return false;
        }
        int s = plc.readInt32(DEVICE, W_STATUS) & 0xF; // 1 Idle / 2 Processing / 3 Complete
        return s == 1;
    }
    private boolean isCleanIdle() {
        if (!deviceIdleAndStandby()) return false;
        if (plc.readBoolean(DEVICE, B_CMD_REQ))     return false;
        if (plc.readBoolean(DEVICE, B_COLLECT_REQ)) return false;
        if (plc.readBoolean(DEVICE, B_COMP_ACK))    return false;
        if (plc.readBoolean(DEVICE, B_COMP_REQ))    return false;
        return true;
    }
    private boolean isAtDown() { return plc.readInt32(DEVICE, W_POS_BAY) == BAY_DOWN; }

    private boolean needsOcr(Long cmId) {
        return containerDataRepository.findByContainerMainId(cmId)
                .map(d -> isBlank(d.getOcrText1()) && isBlank(d.getOcrText2()))
                .orElse(true);
    }
    private int readEstimatedQty(Long cmId) {
        int q = containerDataRepository.findByContainerMainId(cmId)
                .map(ContainerData::getEstimatedQuantity)
                .map(v -> v == null ? 0 : v)
                .orElse(0);
        if (q < 0) q = 0;
        if (q > 9999) q = 9999;
        return q;
    }
    private int readThicknessMm100(Long cmId) {
        for (String k : new String[]{"tray_thickness_mm"}) {
            Optional<ContainerAttr> a = containerAttrRepository.findOne(cmId, k);
            if (a.isPresent()) {
                Integer v = parseMmToMm100(a.get().getAttrValue());
                if (v != null && v > 0) return v;
            }
        }
        return DEFAULT_THICK_MMx100;
    }

    private int packTypeAndQty(int typeDec, int qtyDec) { return (qtyDec << 8) | typeDec; }
    private Integer parseMmToMm100(String mmStr) {
        if (mmStr == null || mmStr.isBlank()) return null;
        try {
            BigDecimal mm = new BigDecimal(mmStr.trim());
            return mm.multiply(new BigDecimal("100")).setScale(0, BigDecimal.ROUND_HALF_UP).intValueExact();
        } catch (Exception e) { return null; }
    }
    private Integer safeGetLevel(TransferDeviceStatus ds) { try { return ds.getLevel(); } catch (Throwable ignore) { return null; } }
    private boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
}
