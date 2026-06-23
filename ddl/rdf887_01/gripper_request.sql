create table gripper_request
(
    id                   bigint auto_increment
        primary key,
    gripper_id           bigint                             null comment '指定 Gripper 裝置 ID',
    request_key          varchar(100)                       not null comment '外部請求識別碼',
    version              int      default 1                 not null comment '請求版本控制',
    task_type            enum ('MOVE', 'PICK', 'DROP')      not null comment '任務類型',
    request_source       enum ('UI', 'SYSTEM')              not null comment '來源系統（人機操作或系統排程）',
    container_main_id    bigint                             null,
    source_location_id   bigint                             null comment '來源位置（如 PICK、MOVE）',
    source_location_name varchar(50)                        null comment '來源位置顯示名稱（選填）',
    target_location_id   bigint                             null comment '目標位置（僅 PLACE、MOVE 使用）',
    target_location_name varchar(50)                        null comment '目標位置顯示名稱（選填）',
    target_height_mm     decimal(6, 2)                      null comment '希望執行的目標高度（參考用）',
    layer_count          int                                null comment '夾取層數（僅 PICK 使用）',
    accepted             char                               null comment '是否接受請求（Y/N）',
    accept_time          datetime                           null,
    reject_reason        varchar(255)                       null,
    operator             varchar(50)                        null,
    request_time         datetime default CURRENT_TIMESTAMP null,
    remark               text                               null,
    raw_payload          text                               null comment '原始請求內容 JSON（保留擴充用）',
    created_time         datetime default CURRENT_TIMESTAMP null,
    updated_time         datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint request_key
        unique (request_key),
    constraint gripper_request_ibfk_1
        foreign key (container_main_id) references container_main (id)
            on delete cascade,
    constraint gripper_request_ibfk_2
        foreign key (source_location_id) references location_point (id),
    constraint gripper_request_ibfk_3
        foreign key (target_location_id) references location_point (id)
)
    charset = utf8mb4;

create index IDX_gripper_request_container_main_id_created_time
    on gripper_request (container_main_id, created_time);

create index IDX_gripper_request_created_time
    on gripper_request (created_time);

create index IDX_gripper_request_gripper_id_accepted_created_time
    on gripper_request (gripper_id, accepted, created_time);

create index idx_target_id_type
    on gripper_request (target_location_id, task_type);

create index source_location_id
    on gripper_request (source_location_id);

