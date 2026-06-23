create table tool_limit_override
(
    tool_name        varchar(200)                         not null
        primary key,
    override_limit   varchar(50)                          not null,
    unit             varchar(20)                          not null,
    is_active        tinyint(1) default 1                 not null,
    updated_from_tid varchar(40)                          null,
    updated_time     datetime   default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP
)
    charset = utf8mb4;

