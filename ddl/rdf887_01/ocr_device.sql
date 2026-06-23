create table ocr_device
(
    id               int                                      not null
        primary key,
    name             varchar(64)                              null,
    status           varchar(16) default 'OFFLINE'            not null,
    accepting_task   tinyint(1)  default 0                    not null,
    last_active_time datetime(6)                              null,
    created_at       datetime(6) default CURRENT_TIMESTAMP(6) not null,
    updated_at       datetime(6) default CURRENT_TIMESTAMP(6) not null on update CURRENT_TIMESTAMP(6)
)
    charset = utf8mb4;

create index idx_ocr_device_last_active
    on ocr_device (last_active_time);

create index idx_ocr_device_status
    on ocr_device (status);

