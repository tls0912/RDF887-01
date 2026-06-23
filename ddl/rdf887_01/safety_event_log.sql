create table safety_event_log
(
    id             bigint auto_increment
        primary key,
    point_id       bigint   not null,
    from_triggered char     not null,
    to_triggered   char     not null,
    change_time    datetime not null,
    snapshot_after json     null,
    constraint safety_event_log_ibfk_1
        foreign key (point_id) references safety_point (id)
)
    charset = utf8mb4;

create index IDX_safety_event_log_change_time
    on safety_event_log (change_time desc);

create index idx_point_time
    on safety_event_log (point_id, change_time);

