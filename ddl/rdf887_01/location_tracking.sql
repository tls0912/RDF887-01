create table location_tracking
(
    id                 bigint auto_increment
        primary key,
    container_main_id  bigint                             not null,
    location_point_id  bigint                             not null,
    arrived_time       datetime                           not null comment '抵達時間（建帳時間）',
    last_verified_time datetime                           null comment '最後一次驗證位置的時間（來自 PLC 或人工）',
    updated_time       datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '最後異動時間',
    flow_id            bigint                             null comment '來源 flow 紀錄 ID（參考用途，不加 FK）',
    constraint uq_container_main
        unique (container_main_id),
    constraint location_tracking_ibfk_1
        foreign key (container_main_id) references container_main (id)
            on delete cascade,
    constraint location_tracking_ibfk_2
        foreign key (location_point_id) references location_point (id)
)
    charset = utf8mb4;

create index location_point_id
    on location_tracking (location_point_id);

