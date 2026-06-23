create table gripper_task_history
(
    id                bigint auto_increment
        primary key,
    origin_id         bigint                                                                                                not null comment '對應主表 gripper_task.id',
    gripper_id        bigint                                                                                                null comment '執行任務的 Gripper 裝置 ID',
    task_type         enum ('MOVE', 'PICK', 'DROP')                                                                         null comment '任務動作類型',
    task_status       enum ('PENDING', 'DISPATCHED', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'CANCELLED', 'SKIPPED', 'RETRY') null comment '任務狀態',
    priority_level    int      default 0                                                                                    null comment '任務優先級',
    container_main_id bigint                                                                                                null,
    from_location_id  bigint                                                                                                null comment '來源位置（僅 PICK、MOVE 使用）',
    to_location_id    bigint                                                                                                null comment '目標位置（僅 PLACE、MOVE 使用）',
    target_height_mm  decimal(6, 2)                                                                                         null,
    layer_count       int                                                                                                   null,
    dispatched_time   datetime                                                                                              null,
    completed_time    datetime                                                                                              null,
    done_time         datetime                                                                                              null comment '任務實際結束時間（完成或取消皆可能）',
    cancelled_time    datetime                                                                                              null,
    cancelled_reason  varchar(200)                                                                                          null,
    operator          varchar(50)                                                                                           null,
    remark            text                                                                                                  null,
    change_type       enum ('INSERT', 'UPDATE', 'DELETE')                                                                   not null,
    archived_time     datetime default CURRENT_TIMESTAMP                                                                    null,
    archived_by       varchar(50)                                                                                           null comment '紀錄來源（系統或操作人員）'
)
    charset = utf8mb4;

create index idx_gth_archived
    on gripper_task_history (archived_time);

create index idx_gth_cm
    on gripper_task_history (container_main_id);

create index idx_gth_from_to
    on gripper_task_history (from_location_id, to_location_id);

create index idx_gth_gripper_time
    on gripper_task_history (gripper_id, done_time);

create index idx_gth_origin
    on gripper_task_history (origin_id);

