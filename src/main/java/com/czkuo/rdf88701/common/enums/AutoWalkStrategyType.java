package com.czkuo.rdf88701.common.enums;

import lombok.Getter;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Getter
public enum AutoWalkStrategyType {

    RANDOM("隨機儲位搬運"),
    SEQUENTIAL("依儲位順序搬運"),
    SINGLE_CONTAINER("單容器反覆搬運"),
    LOCATION_PAIR("特定儲位對搬運"),
    EVEN_DISTRIBUTION("平均分散策略"),
    CLUSTER_BASED("群集優化策略");

    private final String description;

    AutoWalkStrategyType(String description) {
        this.description = description;
    }
}
