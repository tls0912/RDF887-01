create table ocr_manual_log
(
    id                bigint auto_increment
        primary key,
    container_main_id bigint       not null,
    curr_ocr_text1    varchar(500) null,
    curr_ocr_text2    varchar(500) null,
    ref_site          varchar(50)  null,
    ref_container_id  bigint       null,
    ref_ocr_text1     varchar(500) null,
    ref_ocr_text2     varchar(500) null,
    bad_ocr           char         null,
    part_match        char         null,
    ocr1_match        char         null,
    ocr2_match        char         null,
    manual_decision   varchar(20)  not null,
    manual_by         varchar(50)  null,
    manual_time       datetime     null
)
    charset = utf8mb4;

create index IDX_ocr_manual_log_container_main_id
    on ocr_manual_log (container_main_id);

create index IDX_ocr_manual_log_manual_time
    on ocr_manual_log (manual_time desc);

