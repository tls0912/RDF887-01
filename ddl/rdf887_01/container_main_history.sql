create table container_main_history
(
    id             bigint auto_increment
        primary key,
    origin_id      bigint                                   not null comment '對應原始 container_main.id',
    alias_code     varchar(20)                              null,
    container_type enum ('TRAY', 'CASSETTE', 'FOUP', 'BOX') null,
    container_code varchar(50)                              null,
    lot_no         varchar(50)                              null,
    part_no        varchar(50)                              null,
    state          enum ('ACTIVE', 'CLOSED', 'ABORTED')     null,
    closed_time    datetime                                 null,
    change_type    enum ('INSERT', 'UPDATE', 'DELETE')      not null,
    archived_time  datetime default CURRENT_TIMESTAMP       null,
    operator       varchar(50)                              null,
    remark         text                                     null
)
    charset = utf8mb4;

create index idx_cmh_archived
    on container_main_history (archived_time);

