create table working_beam
(
    id           bigint auto_increment
        primary key,
    name         varchar(50)                          not null comment '設備名稱，如 WB#1',
    enabled      tinyint(1) default 1                 null,
    created_time datetime   default CURRENT_TIMESTAMP null,
    constraint name
        unique (name)
)
    charset = utf8mb4;

