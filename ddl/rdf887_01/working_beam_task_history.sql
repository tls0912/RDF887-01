create table working_beam_task_history
(
    id               bigint auto_increment
        primary key,
    origin_id        bigint                                                                                                                          not null comment '對應 working_beam_task.id',
    request_id       bigint                                                                                                                          null comment '對應 working_beam_request.id',
    working_beam_id  bigint                                                                                                                          null comment '對應 working_beam.id',
    direction        enum ('IN', 'OUT')                                                                                                              null comment '移動方向（IN=向內，OUT=向外）',
    task_status      enum ('PENDING', 'DISPATCHED', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'CANCELLED', 'SKIPPED', 'RETRY') default 'PENDING'         null,
    priority_level   int                                                                                                                             null comment '任務優先級',
    dispatched_time  datetime                                                                                                                        null comment '下派時間',
    completed_time   datetime                                                                                                                        null comment '完成時間',
    cancelled_time   datetime                                                                                                                        null comment '取消時間',
    done_time        datetime                                                                                                                        null comment '實際結束時間（完成、取消或失敗）',
    cancelled_reason varchar(200)                                                                                                                    null comment '取消原因',
    remark           text                                                                                                                            null comment '備註',
    created_time     datetime                                                                                              default CURRENT_TIMESTAMP not null comment '任務建立時間',
    updated_time     datetime                                                                                                                        null comment '對應主表 updated_time（最後一次變更時間）',
    change_type      enum ('INSERT', 'UPDATE', 'DELETE')                                                                   default 'INSERT'          null comment '異動類型',
    archived_time    datetime                                                                                              default CURRENT_TIMESTAMP null comment '歸檔時間',
    constraint fk_wb_task_history_beam
        foreign key (working_beam_id) references working_beam (id),
    constraint fk_wb_task_history_origin
        foreign key (origin_id) references working_beam_task (id),
    constraint fk_wb_task_history_request
        foreign key (request_id) references working_beam_request (id)
)
    comment 'WorkingBeam 任務歷史記錄' charset = utf8mb4;

create index IDX_working_beam_task_history_archived_time
    on working_beam_task_history (archived_time);

create index idx_origin_id
    on working_beam_task_history (origin_id);

create index idx_request_id
    on working_beam_task_history (request_id);

create index idx_working_beam_id
    on working_beam_task_history (working_beam_id);

