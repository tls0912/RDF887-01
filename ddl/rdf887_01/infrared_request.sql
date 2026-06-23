create table infrared_request
(
    id                bigint auto_increment
        primary key,
    request_key       varchar(100)                       not null comment '外部識別用唯一鍵',
    version           int      default 1                 not null comment '版本控制（遞增）',
    request_source    enum ('UI', 'SYSTEM')              not null comment '請求來源',
    infrared_id       bigint                             not null comment '指定 Infrared 裝置',
    container_main_id bigint                             null comment '對應的容器主檔',
    task_type         enum ('MEASURE')                   not null comment '任務類型，目前僅 MEASURE',
    accepted          char     default 'N'               null comment '是否接受請求（Y/N）',
    accept_time       datetime                           null,
    reject_reason     varchar(255)                       null,
    operator          varchar(50)                        null,
    request_time      datetime default CURRENT_TIMESTAMP null comment '請求時間',
    created_time      datetime default CURRENT_TIMESTAMP null,
    updated_time      datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    remark            text                               null,
    raw_payload       text                               null,
    constraint uk_request_key
        unique (request_key),
    constraint fk_ir_request_container
        foreign key (container_main_id) references container_main (id)
            on delete cascade,
    constraint fk_ir_request_infrared
        foreign key (infrared_id) references infrared (id)
)
    comment 'Infrared 任務請求' charset = utf8mb4;

create index IDX_infrared_request_accepted_created_time
    on infrared_request (accepted, created_time);

create index IDX_infrared_request_created_time
    on infrared_request (created_time);

create index idx_container_main_id
    on infrared_request (container_main_id);

create index idx_infrared_id
    on infrared_request (infrared_id);

