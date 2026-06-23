create table crane_request
(
    id                   bigint auto_increment
        primary key,
    request_key          varchar(100)                             not null comment '外部識別用唯一鍵',
    version              int      default 1                       not null comment '版本控制（遞增）',
    request_type         enum ('INBOUND', 'OUTBOUND', 'RELOCATE') not null,
    request_source       enum ('UI', 'ASE', 'SYSTEM')             not null comment '請求來源',
    source_request_ref   varchar(100)                             null comment '來源系統傳入之請求參考編號',
    container_main_id    bigint                                   not null,
    source_location_id   bigint                                   null,
    target_location_id   bigint                                   null,
    source_location_name varchar(50)                              null comment '外部傳入的 Source Location Name',
    target_location_name varchar(50)                              null comment '外部傳入的 Target Location Name',
    accepted             char     default 'N'                     null comment '是否接受請求（Y/N）',
    accept_time          datetime                                 null,
    reject_reason        varchar(255)                             null,
    operator             varchar(50)                              null,
    request_time         datetime default CURRENT_TIMESTAMP       null comment '請求時間（外部傳入或系統建立時間）',
    created_time         datetime default CURRENT_TIMESTAMP       null comment '建立時間',
    updated_time         datetime default CURRENT_TIMESTAMP       null on update CURRENT_TIMESTAMP comment '最後更新時間',
    remark               text                                     null,
    raw_payload          text                                     null comment '原始請求內容（JSON 格式）',
    constraint request_key
        unique (request_key),
    constraint crane_request_ibfk_1
        foreign key (container_main_id) references container_main (id)
            on delete cascade,
    constraint crane_request_ibfk_2
        foreign key (source_location_id) references location_point (id),
    constraint crane_request_ibfk_3
        foreign key (target_location_id) references location_point (id),
    constraint crane_request_chk_1
        check (`accepted` in (_utf8mb4\'Y\',_utf8mb4\'N\'))
)
charset=utf8mb4;

create index IDX_crane_request_accepted
    on crane_request (accepted);

create index IDX_crane_request_container_source_target
    on crane_request (container_main_id, source_location_id, target_location_id);

create index IDX_crane_request_created_time
    on crane_request (created_time);

create index source_location_id
    on crane_request (source_location_id);

create index target_location_id
    on crane_request (target_location_id);

