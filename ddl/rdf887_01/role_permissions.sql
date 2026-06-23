create table role_permissions
(
    role_id       bigint not null,
    permission_id bigint not null,
    constraint `PRIMARY`
        primary key (role_id, permission_id),
    constraint role_permissions_ibfk_1
        foreign key (role_id) references roles (id)
            on delete cascade,
    constraint role_permissions_ibfk_2
        foreign key (permission_id) references permissions (id)
            on delete cascade
)
    charset = utf8mb4;

create index permission_id
    on role_permissions (permission_id);

