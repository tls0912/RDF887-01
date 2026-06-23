package com.czkuo.rdf88701.domain.plc.state.gripper;

import com.czkuo.rdf88701.domain.plc.state.common.RunningSubStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;

/**
 * Gripper 裝置可對外推播的簡化快照（Snapshot）
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
