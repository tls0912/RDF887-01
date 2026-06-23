create table location_flow
(
    id                bigint auto_increment
        primary key,
    container_main_id bigint                                                            not null,
    location_point_id bigint                                                            not null,
    entry_type        enum ('PLC', 'MANUAL', 'EXTERNAL', 'REBUILD')                     not null comment '帳務建立方式',
    exit_type         enum ('NORMAL', 'MANUAL', 'FORCE_REMOVED', 'TIMEOUT', 'PLC_LOST') null comment '帳務離開方式',
    arrived_time      datetime                                                          not null comment '進入時間',
    left_time         datetime                                                          null comment '離開時間（NULL 表示尚未離開）',
    entry_operator    varchar(50)                                                       null comment '進帳操作者',
    exit_operator     varchar(50)                                                       null comment '出帳操作者',
    source_task_id    bigint                                                            null comment '來源任務 ID（如有）',
    remark            text                                                              null comment '備註',
    archived_time     datetime default CURRENT_TIMESTAMP                                null comment '歸檔時間',
    constraint location_flow_ibfk_1
        foreign key (container_main_id) references container_main (id)
            on delete cascade,
    constraint location_flow_ibfk_2
        foreign key (location_point_id) references location_point (id)
)
    charset = utf8mb4;

create index IDX_location_flow_archived_time
    on location_flow (archived_time);

create index idx_container_time
    on location_flow (container_main_id asc, arrived_time desc);

create index idx_flow_lookup_NoLocationId
    on location_flow (container_main_id asc, left_time asc, arrived_time desc);

create index idx_flow_lookup_full
    on location_flow (container_main_id asc, location_point_id asc, left_time asc, arrived_time desc);

create index location_point_id
    on location_flow (location_point_id);

