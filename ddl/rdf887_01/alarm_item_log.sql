create table alarm_item_log
(
    id          bigint auto_increment
        primary key,
    item_id     bigint                                         null,
    global_code int                                            not null,
    title_zh    varchar(255) default ''                        not null,
    title_en    varchar(255) default ''                        not null,
    event_type  enum ('TRIGGER', 'CLEAR', 'PLC_ON', 'PLC_OFF') not null,
    created_at  datetime     default CURRENT_TIMESTAMP         not null
)
    charset = utf8mb4;

create index IDX_alarm_item_log_created_at
    on alarm_item_log (created_at);

create index idx_event_type
    on alarm_item_log (event_type);

