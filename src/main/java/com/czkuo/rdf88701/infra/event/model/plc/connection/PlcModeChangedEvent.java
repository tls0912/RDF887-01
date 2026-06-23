package com.czkuo.rdf88701.infra.event.model.plc.connection;

import com.czkuo.rdf88701.common.enums.ConnectionMode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * 表示 PLC 裝置連線模式變更事件
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlcModeChangedEvent implements Serializable {

    private String deviceName;            // 裝置名稱（唯一識別）
    private Instant timestamp;            // 模式變更時間
    private ConnectionMode oldMode;       // 原本的連線模式
    private ConnectionMode newMode;       // 新的連線模式
    private String reason;                // 變更理由（由操作端或系統提供）

    /**
     * 範例場景：
     * - 使用者透過 UI 將模式從 MANUAL 切為 AUTO：reason="使用者切換"
     * - 系統初始化自動設為 OFF：reason="系統預設"
     * - 外部 API 指定切換為 OFF：reason="API 指定"
     */
}
