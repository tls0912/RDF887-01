create table tt_signal_def
(
    id             bigint auto_increment
        primary key,
    device_type    varchar(50)                              not null,
    device_name    varchar(50)                              not null,
    plc_word       varchar(20)                              not null,
    step_no        int                                      not null,
    step_name      varchar(50)                              not null,
    is_time        tinyint(1)  default 0                    not null,
    unit_divisor   int         default 10                   not null,
    created_at     datetime(3) default CURRENT_TIMESTAMP(3) not null,
    updated_at     datetime(3) default CURRENT_TIMESTAMP(3) not null on update CURRENT_TIMESTAMP(3),
    plc_area       varchar(15)                              null,
    location_point bigint                                   null,
    device_area    varchar(50)                              null,
    constraint uk_tt_signal_def_device_word
        unique (device_type, device_name, plc_word)
)
    charset = utf8mb4;

create index IDX_tt_signal_def_step_no
    on tt_signal_def (step_no);

create index idx_device
    on tt_signal_def (device_type, device_name);

create index idx_plc_word
    on tt_signal_def (plc_word);

