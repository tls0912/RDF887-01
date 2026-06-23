create table tool_catalog
(
    tool_name     varchar(200) not null
        primary key,
    default_limit varchar(50)  not null,
    unit          varchar(20)  not null,
    remark        varchar(200) null
)
    charset = utf8mb4;

