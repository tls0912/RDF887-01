create table permissions
(
    id          bigint auto_increment
        primary key,
    name        varchar(50) not null,
    description text        null
)
    charset = utf8mb4;

