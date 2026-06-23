create table labeling_info
(
    id                bigint auto_increment
        primary key,
    request_key       varchar(100)                                                not null comment '去重鍵：TID#index',
    source_cmd_id     varchar(10)                                                 not null comment 'S065 / S066',
    tid               varchar(40)                                                 not null,
    container_main_id bigint                                                      null,
    site_code         varchar(32)                                                 null,
    label_no          int                                                         null,
    payload           json                                                        not null comment '原始/歸一化資料',
    status            enum ('READY', 'USED', 'EXPIRED') default 'READY'           not null,
    expires_at        datetime                                                    null,
    created_time      datetime                          default CURRENT_TIMESTAMP null,
    updated_time      datetime                          default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint uk_request_key
        unique (request_key)
)
    charset = utf8mb4;

create index IDX_labeling_info_created_time
    on labeling_info (created_time);

create index idx_container_status
    on labeling_info (container_main_id, status);

create index idx_site_status
    on labeling_info (site_code, status);

