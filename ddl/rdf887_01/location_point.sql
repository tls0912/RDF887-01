create table location_point
(
    id               bigint auto_increment comment '位置主鍵'
        primary key,
    zone_code        varchar(10)                        not null comment '所屬邏輯區域（如 A/B 倉）',
    code             varchar(50)                        null comment '位置代碼',
    name             varchar(100)                       null comment '位置名稱（人性化顯示）',
    coordinate_x     decimal(10, 3)                     null,
    coordinate_y     decimal(10, 3)                     null,
    coordinate_z     decimal(10, 3)                     null,
    bank             int                                null,
    bay              int                                null,
    level            int                                null,
    location_type    varchar(50)                        not null comment '地點類型（如 STORAGE, SITE）',
    enabled          char                               not null,
    is_occupied      char                               not null,
    is_locked        char                               not null,
    is_reserved      char                               not null,
    lock_reason      varchar(100)                       null,
    preferred_status enum ('OK', 'NG', 'ANY')           null comment '偏好產品狀態',
    created_time     datetime default CURRENT_TIMESTAMP null,
    updated_time     datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP
)
    charset = utf8mb4;

create index IDX_location_point_location_type
    on location_point (location_type);

create index IDX_location_point_name
    on location_point (name);

create index idx_location_point_findAvailableStorage
    on location_point (enabled, location_type, is_locked, is_reserved, is_occupied);

