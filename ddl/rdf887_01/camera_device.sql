create table camera_device
(
    id             bigint unsigned auto_increment comment '相機ID'
        primary key,
    name           varchar(50)                          not null comment 'Cam1 / Cam2 ...',
    modbus_unit_id int                                  not null comment 'Modbus unitId（如有分站）',
    description    varchar(255)                         null,
    enabled        tinyint(1) default 1                 not null,
    created_time   datetime   default CURRENT_TIMESTAMP not null,
    updated_time   datetime   default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    constraint uk_camera_name
        unique (name)
)
    comment '相機裝置主檔' charset = utf8mb4;

