package com.czkuo.rdf88701.domain.plc.state.gripper;

import com.czkuo.rdf88701.domain.plc.state.common.RunningSubStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;

/**
 * Gripper 裝置可對外推播的簡化快照（Snapshot）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@Builder
@ToString
public class GripperStatusSnapshot {

    private final GripperMainStatus mainStatus;
    private final RunningSubStatus runningSubStatus;
    private final String productId;
    private final Instant snapshotTime;
}
