create table product_main
(
    id                bigint auto_increment
        primary key,
    alias_code        varchar(20)                                                    not null comment '可重複利用的產品顯示/邏輯代號（alias）',
    container_main_id bigint                                                         not null comment '所屬容器主鍵（container_main.id）',
    product_code      varchar(50)                                                    null comment '條碼',
    lot_no            varchar(50)                                                    null comment '批號',
    part_no           varchar(50)                                                    null comment '料號',
    state             enum ('ACTIVE', 'CLOSED', 'ABORTED') default 'ACTIVE'          not null,
    created_time      datetime                             default CURRENT_TIMESTAMP null,
    constraint uk_alias_state
        unique (alias_code, state),
    constraint product_main_ibfk_1
        foreign key (container_main_id) references container_main (id)
            on delete cascade
)
    charset = utf8mb4;

create index container_main_id
    on product_main (container_main_id);

create index idx_alias_time
    on product_main (alias_code, created_time);

