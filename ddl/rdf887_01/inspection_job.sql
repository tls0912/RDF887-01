create table inspection_job
(
    id                bigint unsigned auto_increment
        primary key,
    job_key           varchar(64)                          not null comment 'UUID',
    gripper_id        bigint unsigned                      not null,
    container_main_id bigint unsigned                      null comment '當時夾爪上帳（便於追蹤）',
    origin_site_name  varchar(50)                          null comment '觸發異物檢的來源站（如 Site#35）',
    first_station_id  bigint unsigned                      not null,
    second_station_id bigint unsigned                      not null,
    camera_id         bigint unsigned                      not null,
    status            varchar(32)                          not null comment 'CREATED/MOVE_TO_FIRST/WAIT_AT_FIRST/…/DONE/FAILED',
    is_closed         tinyint(1) default 0                 not null comment '0=進行中, 1=關閉(成功或失敗)',
    fail_reason       varchar(255)                         null,
    created_time      datetime   default CURRENT_TIMESTAMP not null,
    updated_time      datetime   default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    active_gripper_id bigint as (if((`is_closed` = 0), `gripper_id`, NULL)) stored,
    constraint uk_active_per_gripper
        unique (active_gripper_id)
)
    comment '異物檢工作流主檔（保證每支夾爪同時僅一筆進行中）' charset = utf8mb4;

create index IDX_inspection_job_gripper_id_is_closed
    on inspection_job (gripper_id, is_closed);

create index IDX_inspection_job_id_is_closed_created_time
    on inspection_job (id, is_closed, created_time);

create index IDX_inspection_job_status_created_time
    on inspection_job (status, created_time);

create index idx_inspection_job_isclosed_createdtime_id
    on inspection_job (is_closed, created_time, id);

create index idx_job_camera
    on inspection_job (camera_id);

