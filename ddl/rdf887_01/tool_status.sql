create table tool_status
(
    tool_name     varchar(200) not null
        primary key,
    current_value varchar(50)  null,
    value_time    datetime     null,
    constraint tool_status_ibfk_1
        foreign key (tool_name) references tool_catalog (tool_name)
)
    charset = utf8mb4;

