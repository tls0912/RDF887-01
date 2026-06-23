create table tt_record
(
    id           bigint auto_increment
        primary key,
    device_type  varchar(50)  not null,
    device_name  varchar(50)  not null,
    plc_group    varchar(20)  not null,
    tt_index     varchar(50)  not null,
    transfer_no  int          null,
    created_time datetime(3)  not null,
    remark_id    varchar(100) null,
    device_area  varchar(50)  null,
    constraint uk_tt_record_device_index
        unique (device_type, device_name, tt_index)
)
    charset = utf8mb4;

create index IDX_tt_record_created_time_remark_id
    on tt_record (created_time, remark_id);

