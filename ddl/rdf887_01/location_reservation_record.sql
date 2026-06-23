create table location_reservation_record
(
    id                bigint auto_increment comment '主鍵'
        primary key,
    container_main_id bigint                               not null comment '預定放置的容器主 ID',
    location_point_id bigint                               not null comment '預定儲位位置 ID',
    reserved_by       varchar(50)                          not null comment '預約來源（如: AUTO_WALK、UI_MANUAL、SYSTEM_INTERNAL 等）',
    reserved_reason   varchar(255)                         null comment '預約原因（選填）',
    reserved_time     datetime   default CURRENT_TIMESTAMP not null comment '預約建立時間',
    expired_time      datetime                             null comment '預期過期時間（NULL 表示永不過期）',
    fulfilled         tinyint(1) default 0                 not null comment '是否已完成（0=尚未放置, 1=容器已放入）',
    fulfilled_time    datetime                             null comment '實際完成時間',
    cancelled         tinyint(1) default 0                 not null comment '是否已取消（0=否, 1=是）',
    cancelled_time    datetime                             null comment '取消時間',
    cancelled_reason  varchar(255)                         null comment '取消原因',
    expired           tinyint(1) default 0                 not null comment '是否已過期（系統排程標記用，不直接刪除）',
    constraint uk_active_loc
        unique (((case
                      when ((`fulfilled` = 0) and (`cancelled` = 0) and (`expired` = 0)) then `location_point_id`
                      else NULL end))),
    constraint fk_reservation_container
        foreign key (container_main_id) references container_main (id)
            on delete cascade,
    constraint fk_reservation_location
        foreign key (location_point_id) references location_point (id)
)
    comment '儲位預約主表' charset = utf8mb4;

create index idx_container_main
    on location_reservation_record (container_main_id);

create index idx_effective_reservation
    on location_reservation_record (fulfilled, cancelled, expired, expired_time);

create index idx_location_point
    on location_reservation_record (location_point_id);

