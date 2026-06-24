package com.czkuo.rdf88701.infra.event.model.plc.strapping;

import com.czkuo.rdf88701.domain.plc.command.StrappingCommand;
import com.czkuo.rdf88701.domain.plc.state.Strapping.StrappingCommandStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * StrappingCommandUpdatedEvent
 * - 表示單一 Strapping 裝置的控制命令狀態更新事件
 * - 用於事件推播、記錄、或狀態同步流程
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@ToString
@RequiredArgsConstructor
public class StrappingCommandUpdatedEvent {

    /** Strapping 裝置 ID */
    private final int strappingId;

    /** 最新命令狀態資料（完整快照） */
    private final StrappingCommandStatus commandStatus;

    /** 安全取得內部命令物件 */
    private StrappingCommand safeCmd() {
        return commandStatus != null ? commandStatus.getCommand() : null;
    }

    /** 取得任務編號（Strapping No） */
    public int getStrappingNo() {
        StrappingCommand cmd = safeCmd();
        return cmd != null ? cmd.getStrappingNo() : -1;
    }

    /** 取得綁帶次數（Strapping Count） */
    public int getStrappingCount() {
        StrappingCommand cmd = safeCmd();
        return cmd != null ? cmd.getStrappingCount() : -1;
    }

    /** 取得執行模式字串（如 PUSH / STRAP） */
    public String getStrappingMode() {
        StrappingCommand cmd = safeCmd();
        return cmd != null && cmd.getStrappingMode() != null
                ? cmd.getStrappingMode().getModeName()
                : "UNKNOWN";
    }

    /** 是否為 PUSH 模式 */
    public boolean isPushMode() {
        StrappingCommand cmd = safeCmd();
        return cmd != null && cmd.getStrappingMode() != null && cmd.getStrappingMode().isPush();
    }

    /** 是否為 STRAP 模式 */
    public boolean isStrapMode() {
        StrappingCommand cmd = safeCmd();
        return cmd != null && cmd.getStrappingMode() != null && cmd.getStrappingMode().isNeedStrapping();
    }

    /** 判斷是否 PLC 裝置 Ready（快取狀態由外層判斷） */
    public boolean isStrappingReady() {
        return commandStatus != null && commandStatus.isStrappingReady();
    }
}
