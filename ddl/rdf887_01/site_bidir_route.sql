create table site_bidir_route
(
    pair_code     varchar(32)                           not null comment '站位對組（例：SITE15_16）'
        primary key,
    active_target varchar(32)                           not null comment '當前目標站位（例：Site#15 或 Site#16）',
    updated_by    varchar(50) default 'system'          null comment '最後異動者',
    updated_time  datetime    default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '最後異動時間'
)
    comment '雙向站位路徑選擇（告知 walker 出到哪個站位）' charset = utf8mb4;

