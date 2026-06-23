create table safety_status_snapshot
(
    point_id         bigint   not null
        primary key,
    is_triggered     char     not null,
    last_change_time datetime not null,
    last_poll_time   datetime not null,
    constraint safety_status_snapshot_ibfk_1
        foreign key (point_id) references safety_point (id)
)
    charset = utf8mb4;

