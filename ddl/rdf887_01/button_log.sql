create table button_log
(
    id           bigint auto_increment
        primary key,
    area         varchar(20)                        not null comment '來源區域：WIP / PACK(拆併) 等',
    seq_index    int                                not null comment 'PLC 流水號 index（每次+1）',
    button_id    tinyint                            not null comment '按鈕 ID：1=啟動 2=停止 3=異常復歸 4=手自動切換 5=拆併區維修門 6=打帶機#1維修門 7=打帶機#2維修門 8=打帶機#3維修門 9=貼標機維修門',
    return_code  tinyint                            not null comment '1=OK, 2=NG',
    event_time   datetime                           not null comment 'PLC 時間戳 (YYMM/DDhh/mmss)',
    created_time datetime default CURRENT_TIMESTAMP not null,
    constraint uk_area_idx
        unique (area, seq_index)
)
    charset = utf8mb4;

create index IDX_button_log_created_time
    on button_log (created_time);

create index idx_area_button_time
    on button_log (area, button_id, event_time);

create index idx_day_agg
    on button_log (event_time, area, button_id, return_code);

