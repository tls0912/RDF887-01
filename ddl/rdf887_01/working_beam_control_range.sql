create table working_beam_control_range
(
    id                bigint auto_increment
        primary key,
    working_beam_id   bigint                             not null,
    location_point_id bigint                             not null,
    position_order    int                                not null comment '位移順序（例如由前至後）',
    created_time      datetime default CURRENT_TIMESTAMP null,
    constraint uk_beam_location
        unique (working_beam_id, location_point_id),
    constraint fk_wb_range_beam
        foreign key (working_beam_id) references working_beam (id),
    constraint fk_wb_range_location
        foreign key (location_point_id) references location_point (id)
)
    charset = utf8mb4;

