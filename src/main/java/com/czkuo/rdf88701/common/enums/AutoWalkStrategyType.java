package com.czkuo.rdf88701.common.enums;

import lombok.Getter;

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
