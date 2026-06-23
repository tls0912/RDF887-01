create table container_main
(
    id             bigint auto_increment
        primary key,
    alias_code     varchar(100)                                                   not null comment '可重複利用的容器顯示/邏輯代號（alias）',
    container_type enum ('TRAY', 'CASSETTE', 'FOUP', 'BOX')                       not null comment '實體容器類型',
    container_code varchar(50)                                                    null comment '條碼',
    lot_no         varchar(50)                                                    null comment '批號',
    part_no        varchar(50)                                                    null comment '料號',
    state          enum ('ACTIVE', 'CLOSED', 'ABORTED') default 'ACTIVE'          not null,
    closed_time    datetime                                                       null,
    created_time   datetime                             default CURRENT_TIMESTAMP null,
    updated_time   datetime                             default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint uk_alias_state
        unique (alias_code, state)
)
    charset = utf8mb4;

create index idx_alias_time
    on container_main (alias_code, created_time);

create index idx_container_main_lot_no_id
    on container_main (lot_no asc, id desc);

create index idx_state_time
    on container_main (state, created_time);

