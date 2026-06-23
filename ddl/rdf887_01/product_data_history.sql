create table product_data_history
(
    id                   bigint auto_increment
        primary key,
    origin_id            bigint                               not null comment '對應原始 product_data.id',
    product_main_id      bigint                               null,
    ocr_text             varchar(100)                         null,
    layer_index          int                                  null,
    quality_check_result enum ('OK', 'NG', 'UNKNOWN')         null,
    is_lid               tinyint(1) default 0                 null,
    change_type          enum ('INSERT', 'UPDATE', 'DELETE')  not null,
    archived_time        datetime   default CURRENT_TIMESTAMP null,
    operator             varchar(50)                          null,
    remark               text                                 null
)
    charset = utf8mb4;

create index idx_pdh_archived
    on product_data_history (archived_time);

create index idx_pdh_origin
    on product_data_history (origin_id);

create index idx_pdh_product_archived
    on product_data_history (product_main_id, archived_time);

