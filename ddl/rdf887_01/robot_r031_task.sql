create table robot_r031_task
(
    id                   bigint auto_increment
        primary key,
    log_id               bigint                                                                                                                   not null comment '對應 mqtt_message_log.id（入站 COMMAND）',
    inbox_id             bigint                                                                                                                   null comment '對應 mqtt_inbox.id（入佇列後回填）',
    tid                  varchar(32)                                                                                                              not null comment '任務識別碼（yyyyMMddHHmmssfff）',
    lot_id               varchar(64)                                                                                                              not null comment '批號',
    carrier_id           varchar(64)                                                                                                              not null comment '載具/容器編號',
    wip_name             varchar(64)                                                                                                              not null comment '來源儲格（WIP/STK slot）',
    source_zone          enum ('ZIPA', 'ZIPB', 'WIP')                                                                                             null comment '來源區（ZIP/WIP）',
    manual_port          varchar(64)                                                                                                              null comment '實際放置 Manual Port 名稱（END 時回報）',
    raw_message_json     text                                                                                                                     null comment '原始 MESSAGE JSON（完整保存入站內容）',
    internal_state       enum ('QUEUED', 'ASSIGNED', 'STARTED', 'MOVING', 'ARRIVED', 'COMPLETED', 'CANCELLED', 'ERROR') default 'QUEUED'          not null comment '內部流程狀態',
    external_last_result enum ('OK', 'START', 'END', 'FAIL', 'CANCEL')                                                                            null comment '對外回覆狀態（MQTT RESULT 值）',
    external_last_time   datetime                                                                                                                 null comment '最後一次回覆時間',
    created_time         datetime                                                                                       default CURRENT_TIMESTAMP not null,
    updated_time         datetime                                                                                       default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    constraint uk_r031_task_log
        unique (log_id),
    constraint fk_r031_task_log
        foreign key (log_id) references mqtt_message_log (id)
            on delete cascade
)
    comment 'R031 任務單（WIP/STK → Manual Port 任務追蹤）' charset = utf8mb4;

create index IDX_robot_r031_task_created_time_id
    on robot_r031_task (created_time, id);

create index idx_r031_task_carrier
    on robot_r031_task (carrier_id);

create index idx_r031_task_lot
    on robot_r031_task (lot_id);

create index idx_r031_task_state
    on robot_r031_task (internal_state);

create index idx_r031_task_tid
    on robot_r031_task (tid);

create index idx_r031_task_wip
    on robot_r031_task (wip_name);

