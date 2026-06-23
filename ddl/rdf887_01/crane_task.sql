create table crane_task
(
    id                 bigint auto_increment
        primary key,
    request_id         bigint                                                                                                                          null,
    crane_id           varchar(50)                                                                                                                     null,
    task_type          enum ('INBOUND', 'OUTBOUND', 'RELOCATE')                                                                                        not null,
    task_status        enum ('PENDING', 'DISPATCHED', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'CANCELLED', 'SKIPPED', 'RETRY') default 'PENDING'         null,
    priority_level     int                                                                                                   default 0                 null,
    container_main_id  bigint                                                                                                                          not null,
    source_location_id bigint                                                                                                                          null,
    target_location_id bigint                                                                                                                          null,
    dispatched_time    datetime                                                                                                                        null,
    completed_time     datetime                                                                                                                        null,
    cancelled_time     datetime                                                                                                                        null,
    done_time          datetime                                                                                                                        null,
    cancelled_reason   varchar(200)                                                                                                                    null,
    remark             text                                                                                                                            null,
    created_time       datetime                                                                                              default CURRENT_TIMESTAMP not null comment '任務建立時間',
    updated_time       datetime                                                                                              default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '最後更新時間',
    constraint crane_task_ibfk_1
        foreign key (request_id) references crane_request (id)
            on delete set null,
    constraint crane_task_ibfk_2
        foreign key (container_main_id) references container_main (id)
            on delete cascade,
    constraint crane_task_ibfk_3
        foreign key (source_location_id) references location_point (id),
    constraint crane_task_ibfk_4
        foreign key (target_location_id) references location_point (id)
)
    charset = utf8mb4;

create index IDX_crane_task_container_main_id_created_time
    on crane_task (container_main_id asc, created_time desc);

create index IDX_crane_task_created_time
    on crane_task (created_time);

create index IDX_crane_task_task_status_done_time
    on crane_task (task_status, done_time);

create index IDX_crane_task_task_status_priority_level_created_time
    on crane_task (task_status asc, priority_level desc, created_time asc);

create index request_id
    on crane_task (request_id);

create index source_location_id
    on crane_task (source_location_id);

create index target_location_id
    on crane_task (target_location_id);

