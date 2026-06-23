create table working_beam_task
(
    id               bigint auto_increment
        primary key,
    request_id       bigint                                                                                                                          not null comment '對應的請求',
    working_beam_id  bigint                                                                                                                          not null comment 'WorkingBeam 裝置 ID',
    direction        enum ('IN', 'OUT')                                                                                                              not null comment '移動方向（IN=向內，OUT=向外）',
    task_status      enum ('PENDING', 'DISPATCHED', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'CANCELLED', 'SKIPPED', 'RETRY') default 'PENDING'         null,
    priority_level   int                                                                                                   default 0                 null comment '任務優先級，數值越高優先權越高',
    dispatched_time  datetime                                                                                                                        null comment '下派時間',
    completed_time   datetime                                                                                                                        null comment '完成時間',
    cancelled_time   datetime                                                                                                                        null comment '取消時間',
    done_time        datetime                                                                                                                        null comment '實際結束時間（完成、取消或失敗）',
    cancelled_reason varchar(200)                                                                                                                    null comment '取消原因',
    remark           text                                                                                                                            null comment '備註',
    created_time     datetime                                                                                              default CURRENT_TIMESTAMP not null comment '任務建立時間',
    updated_time     datetime                                                                                              default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '最後更新時間',
    constraint fk_wb_task_beam
        foreign key (working_beam_id) references working_beam (id),
    constraint fk_wb_task_request
        foreign key (request_id) references working_beam_request (id)
)
    comment 'WorkingBeam 任務執行' charset = utf8mb4;

create index IDX_working_beam_task_created_time
    on working_beam_task (created_time);

create index IDX_working_beam_task_request_id_created_time
    on working_beam_task (request_id, created_time);

create index IDX_working_beam_task_working_beam_id_task_status_created_time
    on working_beam_task (working_beam_id, task_status, created_time);

