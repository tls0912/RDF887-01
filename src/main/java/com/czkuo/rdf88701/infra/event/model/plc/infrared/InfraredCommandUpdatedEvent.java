package com.czkuo.rdf88701.infra.event.model.plc.infrared;

import com.czkuo.rdf88701.domain.plc.command.InfraredCommand;
import com.czkuo.rdf88701.domain.plc.state.infrared.InfraredCommandStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * InfraredCommandUpdatedEvent
 * - 表示單一紅外線設備的指令狀態更新事件
 * - 用於事件推播、指令記錄、或狀態同步流程
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@ToString
@RequiredArgsConstructor
public class InfraredCommandUpdatedEvent {

    /** 紅外線設備 ID */
    private final int infraredId;

    /** 最新指令狀態（Bit + Word 合併後快照） */
    private final InfraredCommandStatus commandStatus;

    /** 安全取得內部指令物件 */
    private InfraredCommand safeCmd() {
        return commandStatus != null ? commandStatus.getCommand() : null;
    }

    /** 取得 Infrared No（紅外線指令序號） */
    public int getInfraredNo() {
        InfraredCommand cmd = safeCmd();
        return cmd != null ? cmd.getInfraredNo() : -1;
    }

    /** 取得托盤厚度（mm，保留兩位小數） */
    public double getTrayThicknessMm() {
        InfraredCommand cmd = safeCmd();
        return cmd != null ? cmd.getTrayThickness() / 100.0 : 0.0;
    }

    /** 判斷是否 Infrared Ready 狀態（Bit） */
    public boolean isInfraredReady() {
        return commandStatus != null && commandStatus.isInfraredReady();
    }
}
