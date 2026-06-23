create table working_beam_request
(
    id              bigint auto_increment
        primary key,
    request_key     varchar(100)                       not null comment '外部識別用唯一鍵',
    version         int      default 1                 not null comment '版本控制（遞增）',
    request_source  enum ('UI', 'SYSTEM')              not null comment '請求來源',
    working_beam_id bigint                             not null comment '指定 WorkingBeam 裝置',
    direction       enum ('IN', 'OUT')                 not null comment '移動方向（IN=向內，OUT=向外）',
    accepted        char     default 'N'               null comment '是否接受請求（Y/N）',
    accept_time     datetime                           null,
    reject_reason   varchar(255)                       null,
    operator        varchar(50)                        null,
    request_time    datetime default CURRENT_TIMESTAMP null comment '請求時間',
    created_time    datetime default CURRENT_TIMESTAMP null,
    updated_time    datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    remark          text                               null,
    raw_payload     text                               null,
    constraint uk_request_key
        unique (request_key),
    constraint fk_wb_request_beam
        foreign key (working_beam_id) references working_beam (id)
)
    comment 'WorkingBeam 任務請求' charset = utf8mb4;

create index IDX_working_beam_request_accepted_created_time
    on working_beam_request (accepted, created_time);

create index IDX_working_beam_request_created_time
    on working_beam_request (created_time);

create index idx_working_beam_id
    on working_beam_request (working_beam_id);

