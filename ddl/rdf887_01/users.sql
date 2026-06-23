create table users
(
    id         bigint auto_increment
        primary key,
    username   varchar(100)                        not null,
    password   varchar(255)                        not null,
    role_id    bigint                              not null,
    created_at timestamp default CURRENT_TIMESTAMP null,
    updated_at timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint username
        unique (username),
    constraint users_ibfk_1
        foreign key (role_id) references roles (id)
            on delete cascade
)
    charset = utf8mb4;

create index role_id
    on users (role_id);

