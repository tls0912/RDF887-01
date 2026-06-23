create table container_data
(
    id                 bigint auto_increment
        primary key,
    container_main_id  bigint                                                                                                   not null comment '對應 container_main.id',
    ocr_text1          varchar(100)                                                                                             null comment '第一組 OCR 掃描結果（容器標示）',
    ocr_text2          varchar(100)                                                                                             null comment '第二組 OCR 掃描結果（容器標示）',
    estimated_quantity int                                                                                                      null comment '預估層數',
    verified_quantity  int                                                                                                      null comment '驗證層數',
    work_cover_layers  int                                                                                                      null comment '工蓋層數（可為 NULL 表未知）',
    cover_layers       int                                                                                                      null comment '上蓋層數（可為 NULL 表未知）',
    product_layers     int                                                                                                      null comment '產品層數（可為 NULL 表未知）',
    content_kind       enum ('UNKNOWN', 'NORMAL_WITH_COVER', 'NORMAL_NO_COVER', 'ALL_COVER', 'EMPTY') default 'UNKNOWN'         not null comment '容器內容型態',
    created_time       datetime                                                                       default CURRENT_TIMESTAMP null,
    updated_time       datetime                                                                       default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint container_data_ibfk_1
        foreign key (container_main_id) references container_main (id)
            on delete cascade
)
    charset = utf8mb4;

create index IDX_container_data_content_kind
    on container_data (content_kind);

create index container_main_id
    on container_data (container_main_id);

