create table safety_point
(
    id         bigint auto_increment
        primary key,
    group_word varchar(16)      not null,
    bit_hex    char             not null,
    addr_expr  varchar(32)      not null,
    type_code  varchar(32)      not null,
    point_name varchar(128)     not null,
    remark     varchar(255)     null,
    enabled    char default 'Y' not null,
    constraint uq_point_addr
        unique (addr_expr)
)
    charset = utf8mb4;

create index IDX_safety_point_enabled
    on safety_point (enabled);

