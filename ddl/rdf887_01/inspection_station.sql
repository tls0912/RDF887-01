create table inspection_station
(
    id                bigint unsigned auto_increment
        primary key,
    location_point_id bigint unsigned                      not null comment '對應 location_point.id',
    name              varchar(50)                          not null comment 'VIRTUAL#6 / 7 / 8 / 9',
    shot_order        tinyint                              not null comment '1=FIRST, 2=SECOND',
    camera_id         bigint unsigned                      not null comment 'camera_device.id',
    gripper_id        bigint unsigned                      not null comment '此站點由哪支夾爪進站拍照（如 4 or 5）',
    enabled           tinyint(1) default 1                 not null,
    remark            varchar(255)                         null,
    created_time      datetime   default CURRENT_TIMESTAMP not null,
    updated_time      datetime   default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    constraint uk_station_name
        unique (name),
    constraint fk_ins_station_camera
        foreign key (camera_id) references camera_device (id)
)
    comment '異物檢虛擬站（含拍照順序與綁定相機/夾爪）' charset = utf8mb4;

create index idx_station_camera
    on inspection_station (camera_id);

create index idx_station_gripper
    on inspection_station (gripper_id);

