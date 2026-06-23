create table alarm_item
(
    id                bigint auto_increment
        primary key,
    global_code       int                                     not null,
    type              enum ('ALARM', 'WARNING')               not null,
    equipment         enum ('WIP', 'ZIPA', 'ZIPB', 'FSK6001') not null,
    local_code        int                                     not null,
    title_zh          varchar(255) default ''                 not null,
    title_en          varchar(255) default ''                 not null,
    enabled           tinyint(1)   default 1                  not null,
    allow_plc_trigger tinyint(1)   default 0                  not null,
    is_triggered      tinyint(1)   default 0                  not null,
    want_plc_trigger  tinyint(1)   default 0                  not null,
    updated_at        datetime     default CURRENT_TIMESTAMP  not null on update CURRENT_TIMESTAMP,
    constraint uk_global
        unique (global_code),
    constraint uk_scope
        unique (type, equipment, local_code)
)
    charset = utf8mb4;

create index IDX_alarm_item_equipment
    on alarm_item (equipment);

create index IDX_alarm_item_is_triggered_updated_at
    on alarm_item (is_triggered asc, updated_at desc);

create index idx_plc_todo
    on alarm_item (want_plc_trigger, allow_plc_trigger, enabled);

create index idx_trigger
    on alarm_item (is_triggered);

