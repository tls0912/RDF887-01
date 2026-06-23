create table ocr_verification
(
    id                          bigint auto_increment
        primary key,
    container_main_id           bigint        not null,
    state                       varchar(20)   not null,
    carrier_id                  varchar(100)  null,
    lot_id                      varchar(100)  null,
    tray_type                   varchar(100)  null,
    ref_site                    varchar(50)   null,
    ref_container_id            bigint        null,
    curr_ocr_text1              varchar(500)  null,
    curr_ocr_text2              varchar(500)  null,
    ref_ocr_text1               varchar(500)  null,
    ref_ocr_text2               varchar(500)  null,
    local_pass                  char          not null,
    bad_ocr                     char          not null,
    part_match                  char          not null,
    ocr1_match                  char          not null,
    ocr2_match                  char          not null,
    s073_tid                    varchar(50)   null,
    s073_status                 varchar(20)   not null,
    s073_result_code            varchar(50)   null,
    s073_sent_time              datetime      null,
    s073_retry_count            int default 0 not null,
    s073_last_retry_time        datetime      null,
    s073_next_retry_time        datetime      null,
    manual_decision             varchar(20)   not null,
    manual_by                   varchar(50)   null,
    manual_time                 datetime      null,
    final_result                varchar(20)   null,
    created_time                datetime      not null,
    updated_time                datetime      not null,
    curr_back_one_light_path    text          null,
    curr_back_three_light_path  text          null,
    curr_front_one_light_path   text          null,
    curr_front_three_light_path text          null,
    ref_back_one_light_path     text          null,
    ref_back_three_light_path   text          null,
    ref_front_one_light_path    text          null,
    ref_front_three_light_path  text          null,
    constraint uq_cm_active
        unique (container_main_id, state)
)
    charset = utf8mb4;

create index idx_container_id_ref_container_id
    on ocr_verification (container_main_id, ref_container_id);

create index idx_created_time
    on ocr_verification (created_time);

create index idx_s073_tid
    on ocr_verification (s073_tid);

create index idx_state_manual_decision
    on ocr_verification (state, manual_decision);

