create table infrared_task
(
    id                bigint auto_increment
        primary key,
    request_id        bigint                                                                                                                          null,
    infrared_id       bigint                                                                                                                          not null comment 'Infrared 裝置 ID',
    container_main_id bigint                                                                                                                          null comment '對應的容器主檔',
    task_type         enum ('MEASURE')                                                                                                                not null comment '任務類型，目前僅 MEASURE',
    task_status       enum ('PENDING', 'DISPATCHED', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'CANCELLED', 'SKIPPED', 'RETRY') default 'PENDING'         null comment '任務狀態',
    priority_level    int                                                                                                   default 0                 null comment '任務優先級，數值越高優先權越高',
    dispatched_time   datetime                                                                                                                        null comment '下派時間',
    completed_time    datetime                                                                                                                        null comment '完成時間',
    cancelled_time    datetime                                                                                                                        null comment '取消時間',
    done_time         datetime                                                                                                                        null comment '實際結束時間（完成、取消或失敗）',
    cancelled_reason  varchar(200)                                                                                                                    null comment '取消原因',
    remark            text                                                                                                                            null comment '備註',
    created_time      datetime                                                                                              default CURRENT_TIMESTAMP not null comment '任務建立時間',
    updated_time      datetime                                                                                              default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '最後更新時間',
    constraint fk_ir_task_container
        foreign key (container_main_id) references container_main (id)
            on delete cascade,
    constraint fk_ir_task_infrared
        foreign key (infrared_id) references infrared (id),
    constraint fk_ir_task_request
        foreign key (request_id) references infrared_request (id)
            on delete set null
)
    comment 'Infrared 任務執行' charset = utf8mb4;

create index IDX_infrared_task_infrared_id_task_status_done_time
    on infrared_task (infrared_id, task_status, done_time);

create index idx_infrared_id
    on infrared_task (infrared_id);

create index idx_ir_task_container_main_id
    on infrared_task (container_main_id);

create index idx_request_id
    on infrared_task (request_id);

