create table container_data_history
(
    id                 bigint auto_increment
        primary key,
    origin_id          bigint                                                                         not null comment '對應原始 container_data.id',
    container_main_id  bigint                                                                         null,
    ocr_text1          varchar(100)                                                                   null comment '第一組 OCR 掃描結果（容器標示）',
    ocr_text2          varchar(100)                                                                   null comment '第二組 OCR 掃描結果（容器標示）',
    estimated_quantity int                                                                            null,
    verified_quantity  int                                                                            null,
    work_cover_layers  int                                                                            null comment '工蓋層數（可為 NULL 表未知）',
    cover_layers       int                                                                            null comment '上蓋層數（可為 NULL 表未知）',
    product_layers     int                                                                            null comment '產品層數（可為 NULL 表未知）',
    content_kind       enum ('UNKNOWN', 'NORMAL_WITH_COVER', 'NORMAL_NO_COVER', 'ALL_COVER', 'EMPTY') null comment '容器內容型態',
    change_type        enum ('INSERT', 'UPDATE', 'DELETE')                                            not null,
    archived_time      datetime default CURRENT_TIMESTAMP                                             null,
    operator           varchar(50)                                                                    null,
    remark             text                                                                           null
)
    charset = utf8mb4;

create index idx_cdh_archived
    on container_data_history (archived_time);

