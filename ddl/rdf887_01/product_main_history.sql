create table product_main_history
(
    id                bigint auto_increment
        primary key,
    origin_id         bigint                               not null comment '對應原始 product_main.id',
    alias_code        varchar(20)                          null comment '可重複利用的產品顯示/邏輯代號（alias）',
    state             enum ('ACTIVE', 'CLOSED', 'ABORTED') null comment '產品狀態（與 product_main 同步）',
    container_main_id bigint                               null,
    product_code      varchar(50)                          null,
    lot_no            varchar(50)                          null,
    part_no           varchar(50)                          null,
    change_type       enum ('INSERT', 'UPDATE', 'DELETE')  not null,
    archived_time     datetime default CURRENT_TIMESTAMP   null,
    operator          varchar(50)                          null,
    remark            text                                 null
)
    charset = utf8mb4;

create index container_main_id
    on product_main_history (container_main_id);

create index idx_pmh_alias_archived
    on product_main_history (alias_code, archived_time);

create index idx_pmh_archived
    on product_main_history (archived_time);

create index idx_pmh_origin
    on product_main_history (origin_id);

