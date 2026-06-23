package com.czkuo.rdf88701.common.enums;

/**
 * Outbox 事件狀態
 * PENDING   = 待送
 * SENT      = 已送、等待 ACK
 * RETRYING  = 重送中（曾送過失敗）
 * TIMEOUT   = 逾時（可由排程或業務標註）
 * ACKED     = 已收到 ACK（或不需 ACK 直接視為完成）
 * FAILED    = 達最大重試或不可恢復錯誤
 */
public enum MqttEventStatus {
    PENDING, SENT, RETRYING, TIMEOUT, ACKED, FAILED
}