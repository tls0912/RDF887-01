create table mqtt_event_log
(
    id                bigint auto_increment comment '主鍵，自動遞增'
        primary key,
    event_type        varchar(32)                          not null comment '事件類型（如 ALARM, TASK_DONE, STATUS 等）',
    tid               varchar(32)                          not null comment '事件追蹤碼，每筆唯一，對應原始訊息 TID',
    topic             varchar(100)                         not null comment 'MQTT 發送 Topic 名稱',
    target_system     varchar(50)                          not null comment '目標系統（如 SEEC、ASE）',
    require_ack       tinyint(1) default 1                 not null comment '是否需要等待ACK（1=需，0=不需）',
    status            varchar(16)                          not null comment '狀態（PENDING=待送, SENT=已送, TIMEOUT=逾時, RETRYING=補償中, ACKED=已收到回覆, FAILED=補發失敗）',
    event_time        datetime                             not null comment '事件發生時間（如：設備異常發生時間）',
    send_time         datetime                             null comment '實際發送MQTT的時間',
    ack_time          datetime                             null comment '收到ACK的時間（有回覆才寫）',
    retry_count       int        default 0                 null comment '重發次數（每補償重送一次+1）',
    payload           json                                 not null comment '完整MQTT事件內容（原始JSON）',
    result_message    text                                 null comment '補充說明（如失敗原因、ACK內容等）',
    next_attempt_time datetime   default CURRENT_TIMESTAMP not null comment '下一次嘗試時間（排程取用）',
    created_time      datetime   default CURRENT_TIMESTAMP null comment '建立時間',
    updated_time      datetime   default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '最後更新時間'
)
    comment 'MQTT事件可靠推送/補償事件記錄表' charset = utf8mb4;

create index IDX_mqtt_event_log_created_time
    on mqtt_event_log (created_time);

create index IDX_mqtt_event_log_status_require_ack_next_attempt_time
    on mqtt_event_log (status, require_ack, next_attempt_time);

create index idx_status_next_time
    on mqtt_event_log (status, next_attempt_time);

create index idx_tid
    on mqtt_event_log (tid);

