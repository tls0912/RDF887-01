create table safety_device_type
(
    id        bigint auto_increment
        primary key,
    type_code varchar(32) not null,
    type_name varchar(64) not null,
    constraint type_code
        unique (type_code)
)
    charset = utf8mb4;

