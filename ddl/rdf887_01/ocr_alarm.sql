create table ocr_alarm
(
    id            bigint auto_increment
        primary key,
    ocr_device_id int                                      not null,
    alarm_code    varchar(64)                              not null,
    message       varchar(512)                             null,
    status        varchar(8)  default 'ACTIVE'             not null,
    occurred_time datetime(6)                              not null,
    cleared_time  datetime(6)                              null,
    created_at    datetime(6) default CURRENT_TIMESTAMP(6) not null,
    updated_at    datetime(6) default CURRENT_TIMESTAMP(6) not null on update CURRENT_TIMESTAMP(6),
    constraint uk_alarm_unique
        unique (ocr_device_id, alarm_code, occurred_time),
    constraint fk_oa_device
        foreign key (ocr_device_id) references ocr_device (id)
            on delete cascade
)
    charset = utf8mb4;

create index idx_oa_device_time
    on ocr_alarm (ocr_device_id, occurred_time);

create index idx_oa_status
    on ocr_alarm (status);

