package com.czkuo.rdf88701.domain.event;

import lombok.Getter;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */


public record HistoryEvent<T>(T entity, String changeType) {

}
