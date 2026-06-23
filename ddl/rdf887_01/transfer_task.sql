create table transfer_task
(
    id                bigint auto_increment comment '主鍵 ID'
        primary key,
    request_id        bigint                                                                                                                          not null comment '對應的請求 ID',
    transfer_id       bigint                                                                                                                          not null comment 'Transfer 裝置 ID',
    task_type         enum ('MOVE', 'PICK', 'DROP')                                                                                                   not null comment '任務類型',
    container_main_id bigint                                                                                                                          null comment '關聯容器（可選）',
    from_location_id  bigint                                                                                                                          null comment '來源位置 ID',
    to_location_id    bigint                                                                                                                          null comment '目標位置 ID',
    task_status       enum ('PENDING', 'DISPATCHED', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'CANCELLED', 'SKIPPED', 'RETRY') default 'PENDING'         null comment '任務狀態',
    priority_level    int                                                                                                   default 0                 null comment '任務優先級',
    dispatched_time   datetime                                                                                                                        null comment '下發時間',
    completed_time    datetime                                                                                                                        null comment '完成時間',
    cancelled_time    datetime                                                                                                                        null comment '取消時間',
    done_time         datetime                                                                                                                        null comment '任務實際結束時間（完成/取消皆可能）',
    cancelled_reason  varchar(200)                                                                                                                    null comment '取消原因',
    remark            text                                                                                                                            null comment '備註',
    created_time      datetime                                                                                              default CURRENT_TIMESTAMP not null comment '建立時間',
    updated_time      datetime                                                                                              default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新時間'
)
    comment 'Transfer 任務執行表' charset = utf8mb4;

create index IDX_transfer_task_transfer_id_from_location_id_to_location_id
    on transfer_task (transfer_id, from_location_id, to_location_id);

create index IDX_transfer_task_transfer_id_task_status_done_time
    on transfer_task (transfer_id, task_status, done_time);

create index idx_container_id_created_time
    on transfer_task (container_main_id asc, created_time desc);

create index idx_container_main_id
    on transfer_task (container_main_id);

create index idx_request_id
    on transfer_task (request_id);

