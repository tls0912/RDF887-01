create table mqtt_event_status_log
(
    id            bigint auto_increment comment '主鍵，自動遞增'
        primary key,
    event_id      bigint                                not null comment '對應 mqtt_event_log.id（或TID）',
    from_status   varchar(16)                           null comment '變更前狀態（第一次建立時可為NULL）',
    to_status     varchar(16)                           not null comment '變更後狀態',
    changed_by    varchar(50) default 'system'          null comment '異動者（如 system, user_admin, schedule 等）',
    change_reason varchar(255)                          null comment '異動原因或備註（如逾時自動補償、人工處理等）',
    change_time   datetime    default CURRENT_TIMESTAMP null comment '異動時間'
)
    comment 'MQTT事件狀態變更歷程記錄表' charset = utf8mb4;

create index idx_event
    on mqtt_event_status_log (event_id);

create index idx_status_time
    on mqtt_event_status_log (to_status, change_time);

