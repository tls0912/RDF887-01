create table gripper_task
(
    id                bigint auto_increment
        primary key,
    request_id        bigint                                                                                                                          null,
    request_version   int                                                                                                                             null comment '對應請求版本',
    gripper_id        bigint                                                                                                                          not null comment '執行任務的 Gripper 裝置 ID',
    task_type         enum ('MOVE', 'PICK', 'DROP')                                                                                                   not null comment '任務動作類型',
    task_status       enum ('PENDING', 'DISPATCHED', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'CANCELLED', 'SKIPPED', 'RETRY') default 'PENDING'         null comment '任務狀態',
    priority_level    int                                                                                                   default 0                 null comment '任務優先級',
    container_main_id bigint                                                                                                                          null,
    from_location_id  bigint                                                                                                                          null comment '來源位置（僅 PICK、MOVE 使用）',
    to_location_id    bigint                                                                                                                          null comment '目標位置（僅 PLACE、MOVE 使用）',
    target_height_mm  decimal(6, 2)                                                                                                                   not null comment '實際執行目標高度（建立時固定）',
    layer_count       int                                                                                                                             null comment '夾取層數（僅 PICK 使用）',
    dispatched_time   datetime                                                                                                                        null comment '任務派發時間',
    completed_time    datetime                                                                                                                        null comment '任務完成時間',
    done_time         datetime                                                                                                                        null comment '任務實際結束時間（完成或取消皆可能）',
    cancelled_time    datetime                                                                                                                        null comment '任務取消時間',
    cancelled_reason  varchar(200)                                                                                                                    null,
    operator          varchar(50)                                                                                                                     null,
    remark            text                                                                                                                            null,
    created_time      datetime                                                                                              default CURRENT_TIMESTAMP null,
    updated_time      datetime                                                                                              default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint gripper_task_ibfk_1
        foreign key (request_id) references gripper_request (id)
            on delete set null,
    constraint gripper_task_ibfk_2
        foreign key (container_main_id) references container_main (id)
            on delete cascade,
    constraint gripper_task_ibfk_3
        foreign key (from_location_id) references location_point (id)
)
    charset = utf8mb4;

create index IDX_gripper_task_container_main_id_created_time
    on gripper_task (container_main_id asc, created_time desc);

create index IDX_gripper_task_gripper_id
    on gripper_task (gripper_id);

create index IDX_gripper_task_task_status_done_time
    on gripper_task (task_status, done_time);

create index idx_gripper_id_locationid_type_status
    on gripper_task (gripper_id, to_location_id, task_type, task_status);

create index request_id
    on gripper_task (request_id);

create index source_location_id
    on gripper_task (from_location_id);

