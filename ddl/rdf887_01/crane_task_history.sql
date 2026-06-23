create table crane_task_history
(
    id                 bigint auto_increment
        primary key,
    origin_id          bigint                                                                                                                          not null comment '對應 crane_task.id',
    request_id         bigint                                                                                                                          null,
    crane_id           varchar(50)                                                                                                                     null,
    task_type          enum ('INBOUND', 'OUTBOUND', 'RELOCATE')                                                                                        null,
    task_status        enum ('PENDING', 'DISPATCHED', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'CANCELLED', 'SKIPPED', 'RETRY') default 'PENDING'         null,
    priority_level     int                                                                                                                             null,
    container_main_id  bigint                                                                                                                          null,
    source_location_id bigint                                                                                                                          null,
    target_location_id bigint                                                                                                                          null,
    dispatched_time    datetime                                                                                                                        null,
    completed_time     datetime                                                                                                                        null,
    cancelled_time     datetime                                                                                                                        null,
    done_time          datetime                                                                                                                        null,
    cancelled_reason   varchar(200)                                                                                                                    null,
    remark             text                                                                                                                            null,
    created_time       datetime                                                                                              default CURRENT_TIMESTAMP not null comment '任務建立時間',
    updated_time       datetime                                                                                                                        null comment '對應主表 updated_time（最後一次變更時間）',
    change_type        enum ('INSERT', 'UPDATE', 'DELETE')                                                                   default 'INSERT'          null comment '異動類型',
    archived_time      datetime                                                                                              default CURRENT_TIMESTAMP null
)
    charset = utf8mb4;

create index IDX_crane_task_history_archived_time
    on crane_task_history (archived_time);

create index IDX_crane_task_history_container_source_target
    on crane_task_history (container_main_id, source_location_id, target_location_id);

create index origin_id
    on crane_task_history (origin_id);

create index request_id
    on crane_task_history (request_id);

