create table alarm_action_log
(
    id          bigint auto_increment
        primary key,
    global_code int                                     not null,
    action_note varchar(1024) default ''                not null,
    ase_check   varchar(1024)                           null,
    import_time datetime      default CURRENT_TIMESTAMP not null
)
    charset = utf8mb4;

create index idx_global_time
    on alarm_action_log (global_code, import_time);

