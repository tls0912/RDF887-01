create table product_data
(
    id                   bigint auto_increment
        primary key,
    product_main_id      bigint                                                 not null comment '對應 product_main.id',
    ocr_text             varchar(100)                                           null comment '單片 OCR 結果',
    layer_index          int                                                    null comment '所在容器中的層數索引（從下至上）',
    quality_check_result enum ('OK', 'NG', 'UNKNOWN') default 'UNKNOWN'         null comment '異物檢結果',
    is_lid               tinyint(1)                   default 0                 null comment '是否為上蓋片',
    created_time         datetime                     default CURRENT_TIMESTAMP null,
    constraint product_data_ibfk_1
        foreign key (product_main_id) references product_main (id)
            on delete cascade
)
    charset = utf8mb4;

create index product_main_id
    on product_data (product_main_id);

