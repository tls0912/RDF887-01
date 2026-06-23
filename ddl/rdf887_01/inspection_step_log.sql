create table inspection_step_log
(
    id           bigint unsigned auto_increment
        primary key,
    job_id       bigint unsigned                    not null,
    step_no      tinyint                            not null comment '1=FIRST, 2=SECOND',
    station_id   bigint unsigned                    not null,
    action       varchar(32)                        not null comment 'MOVE/TRIGGER/COMPLETE/ERROR',
    camera_state varchar(32)                        null comment 'IDLE/FIRST_IN_PROGRESS/…',
    camera_error varchar(64)                        null,
    count_first  int                                null,
    count_second int                                null,
    count_total  int                                null,
    times        int                                null,
    payload_json text                               null comment '如需存完整回讀',
    created_time datetime default CURRENT_TIMESTAMP not null,
    constraint fk_step_job
        foreign key (job_id) references inspection_job (id)
)
    comment '異物檢步驟追蹤' charset = utf8mb4;

create index idx_step_job
    on inspection_step_log (job_id);

