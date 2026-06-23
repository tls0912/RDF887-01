create table transfer_task_history
(
    id                bigint auto_increment comment '歷史主鍵'
        primary key,
    origin_id         bigint                                                                                                                          not null comment '對應原始 transfer_task.id',
    request_id        bigint                                                                                                                          not null comment '對應的請求 ID',
    transfer_id       bigint                                                                                                                          not null comment 'Transfer 裝置 ID',
    container_main_id bigint                                                                                                                          null comment '關聯容器（可選）',
    task_type         enum ('MOVE', 'PICK', 'DROP')                                                                                                   null comment '任務類型',
    from_location_id  bigint                                                                                                                          null comment '來源位置 ID',
    to_location_id    bigint                                                                                                                          null comment '目標位置 ID',
    task_status       enum ('PENDING', 'DISPATCHED', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'CANCELLED', 'SKIPPED', 'RETRY') default 'PENDING'         null comment '任務狀態',
    priority_level    int                                                                                                   default 0                 null comment '任務優先級',
    dispatched_time   datetime                                                                                                                        null,
    completed_time    datetime                                                                                                                        null,
    cancelled_time    datetime                                                                                                                        null,
    done_time         datetime                                                                                                                        null,
    cancelled_reason  varchar(200)                                                                                                                    null,
    remark            text                                                                                                                            null,
    change_type       enum ('INSERT', 'UPDATE', 'DELETE')                                                                   default 'INSERT'          null comment '異動類型',
    archived_time     datetime                                                                                              default CURRENT_TIMESTAMP null comment '歸檔時間',
    archived_by       varchar(50)                                                                                                                     null comment '操作者（系統或人員帳號）',
    archived_remark   text                                                                                                                            null comment '歸檔備註'
)
    comment 'Transfer 任務執行歷史紀錄表' charset = utf8mb4;

create index IDX_transfer_task_history_archived_time
    on transfer_task_history (archived_time);

create index idx_container_main_id
    on transfer_task_history (container_main_id);

create index idx_origin_id
    on transfer_task_history (origin_id);

create index idx_request_id
    on transfer_task_history (request_id);

create index idx_transfer_id
    on transfer_task_history (transfer_id);

