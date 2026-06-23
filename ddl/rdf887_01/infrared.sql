create table infrared
(
    id           bigint auto_increment
        primary key,
    name         varchar(50)                          not null comment '設備名稱，如 Infrared#1',
    enabled      tinyint(1) default 1                 null comment '是否啟用（1=啟用，0=停用）',
    created_time datetime   default CURRENT_TIMESTAMP null comment '建立時間',
    constraint name
        unique (name)
)
    charset = utf8mb4;

