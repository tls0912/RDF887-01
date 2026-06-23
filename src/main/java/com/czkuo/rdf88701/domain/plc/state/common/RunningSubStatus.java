package com.czkuo.rdf88701.domain.plc.state.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * RunningSubStatus
 *
 * - 定義裝置運作中（RUNNING狀態下）細分的子行為類型
 * - 主要用於更細緻描述設備正在進行的實際動作（例如移動中、抓取中、放置中）
 * - 不同設備類型（Gripper、OCR、Transfer等）可共用此分類
 * - 若無法明確辨識子行為，則標記為 UNKNOWN
 */
@Getter
@RequiredArgsConstructor
public enum RunningSubStatus {

    /** 無法辨識或未定義的子行為 */
    UNKNOWN("Unknown"),

    /** 閒置中 */
    IDLE("Idle"),

    /** 正在移動至目標位置 */
    MOVING("Moving"),

    /** 正在抓取產品 */
    PICKING("Picking"),

    /** 正在放置產品 */
    DROPPING("Dropping");

    /**
     * 對外顯示用的友善名稱
     * - 用於 Log、UI 顯示，而非直接使用 Enum 名稱
     */
    private final String displayName;

    @Override
    public String toString() {
        return displayName;
    }
}
