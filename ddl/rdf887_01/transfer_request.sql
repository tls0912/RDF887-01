create table transfer_request
(
    id                   bigint auto_increment
        primary key,
    request_key          varchar(100)                       not null comment '外部識別用唯一鍵',
    version              int      default 1                 not null comment '版本控制（遞增）',
    request_source       enum ('UI', 'SYSTEM')              not null comment '請求來源',
    transfer_id          bigint                             not null comment '指定 Transfer 裝置 ID',
    task_type            enum ('MOVE', 'PICK', 'DROP')      not null comment '任務類型',
    container_main_id    bigint                             null comment '關聯容器（可選）',
    source_location_id   bigint                             null comment '來源位置（可為 NULL，視任務類型而定）',
    target_location_id   bigint                             null comment '目標位置（可為 NULL，視任務類型而定）',
    source_location_name varchar(50)                        null comment '來源位置顯示名稱（選填）',
    target_location_name varchar(50)                        null comment '目標位置顯示名稱（選填）',
    accepted             char     default 'N'               null comment '是否接受請求（Y/N）',
    accept_time          datetime                           null,
    reject_reason        varchar(255)                       null,
    operator             varchar(50)                        null,
    request_time         datetime default CURRENT_TIMESTAMP null comment '請求時間',
    created_time         datetime default CURRENT_TIMESTAMP null,
    updated_time         datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    remark               text                               null,
    raw_payload          text                               null,
    constraint uk_request_key
        unique (request_key),
    constraint fk_transfer_request_container
        foreign key (container_main_id) references container_main (id)
            on delete cascade,
    constraint fk_transfer_request_transfer
        foreign key (transfer_id) references transfer (id)
)
    comment 'Transfer 任務請求' charset = utf8mb4;

create index IDX_transfer_request_created_time
    on transfer_request (created_time);

create index IDX_transfer_request_transfer_id_accepted_created_time
    on transfer_request (transfer_id, accepted, created_time);

create index idx_container_main_id
    on transfer_request (container_main_id);

