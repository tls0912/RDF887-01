create table strapping_log
(
    id            bigint auto_increment
        primary key,
    machine_pos   tinyint                            not null comment '打帶機位置：1/2/3',
    machine_id    varchar(20) as ((case `machine_pos`
                                       when 1 then _utf8mb4'STRAP#1'
                                       when 2 then _utf8mb4'STRAP#2'
                                       when 3 then _utf8mb4'STRAP#3'
                                       else concat(_utf8mb4'STRAP#', `machine_pos`) end)) stored,
    seq_index     int                                not null comment 'PLC 流水號 index（每次+1）',
    seq_epoch     int      default 0                 not null comment 'seq_index 重滾輪次（軟體偵測）',
    product_id    varchar(50)                        not null comment '由 25 words 解碼出的字串（≤50字元）',
    result        tinyint                            not null comment '1=OK, 2=NG, 3=NotReady',
    strapping_pos tinyint                            not null comment '原始 Strapping Position（=機台號 1/2/3）',
    event_time    datetime                           not null comment 'PLC 時間戳 (YYMM/DDhh/mmss)',
    created_time  datetime default CURRENT_TIMESTAMP not null,
    constraint UK_strapping_log_id_machine_pos_event_time
        unique (id, machine_pos, event_time)
)
    charset = utf8mb4;

create index IDX_strapping_log_event_time
    on strapping_log (event_time);

create index idx_day_agg
    on strapping_log (event_time, machine_pos, result);

create index idx_machine_time
    on strapping_log (machine_pos, event_time);

