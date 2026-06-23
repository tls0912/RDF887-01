create table inspection_route_map
(
    id                bigint unsigned auto_increment
        primary key,
    gripper_id        bigint unsigned                      not null,
    first_station_id  bigint unsigned                      not null comment 'inspection_station.id (shot_order=1)',
    second_station_id bigint unsigned                      not null comment 'inspection_station.id (shot_order=2)',
    camera_id         bigint unsigned                      not null comment '冗餘存一份，便於查詢',
    enabled           tinyint(1) default 1                 not null,
    remark            varchar(255)                         null,
    created_time      datetime   default CURRENT_TIMESTAMP not null,
    updated_time      datetime   default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    constraint uk_route_gripper
        unique (gripper_id),
    constraint fk_route_camera
        foreign key (camera_id) references camera_device (id),
    constraint fk_route_first_station
        foreign key (first_station_id) references inspection_station (id),
    constraint fk_route_second_station
        foreign key (second_station_id) references inspection_station (id)
)
    comment '異物檢路線對應：夾爪→(FIRST站, SECOND站, 相機)' charset = utf8mb4;

create index IDX_inspection_route_map_gripper_enabled
    on inspection_route_map (gripper_id, enabled);

