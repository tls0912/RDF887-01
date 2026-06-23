create table ocr_task
(
    id                       bigint auto_increment
        primary key,
    ocr_device_id            int                                      not null,
    container_main_id        bigint                                   null,
    status                   varchar(16) default 'QUEUED'             not null,
    created_time             datetime(6)                              not null,
    started_time             datetime(6)                              null,
    completed_time           datetime(6)                              null,
    ocr_text1                varchar(128)                             null comment '第一組 OCR 文字',
    ocr_text2                varchar(128)                             null comment '第二組 OCR 文字',
    error_message            varchar(512)                             null,
    timing_capture_ms        int                                      null,
    timing_ocr_processing_ms int                                      null,
    timing_packaging_ms      int                                      null,
    created_at               datetime(6) default CURRENT_TIMESTAMP(6) not null,
    updated_at               datetime(6) default CURRENT_TIMESTAMP(6) not null on update CURRENT_TIMESTAMP(6),
    constraint fk_ot_container
        foreign key (container_main_id) references container_main (id)
            on delete set null,
    constraint fk_ot_device
        foreign key (ocr_device_id) references ocr_device (id),
    constraint chk_ot_status
        check (`status` in (_utf8mb4\'QUEUED\',_utf8mb4\'RUNNING\',_utf8mb4\'SUCCESS\',_utf8mb4\'FAILED\',_utf8mb4\'DISPATCHED\'))
)
charset=utf8mb4;

create index idx_ot_completed_time
    on ocr_task (completed_time);

create index idx_ot_created_time
    on ocr_task (created_time);

create index idx_ot_device_status
    on ocr_task (ocr_device_id, status);

