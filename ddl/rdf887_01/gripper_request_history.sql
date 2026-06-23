create table gripper_request_history
(
    id                   bigint auto_increment comment '歷史記錄主鍵'
        primary key,
    origin_id            bigint                              not null comment '對應主表 gripper_request.id',
    gripper_id           bigint                              null comment '指定 Gripper 裝置 ID',
    request_key          varchar(100)                        null comment '外部請求識別碼',
    version              int                                 null comment '請求版本控制',
    task_type            enum ('MOVE', 'PICK', 'DROP')       null comment '任務類型',
    request_source       enum ('UI', 'SYSTEM')               null comment '來源系統',
    container_main_id    bigint                              null comment '容器主鍵',
    source_location_id   bigint                              null comment '來源位置 ID',
    source_location_name varchar(50)                         null comment '來源位置顯示名稱',
    target_location_id   bigint                              null comment '目標位置 ID',
    target_location_name varchar(50)                         null comment '目標位置顯示名稱',
    target_height_mm     decimal(6, 2)                       null comment '希望目標高度（參考用）',
    layer_count          int                                 null comment '夾取層數',
    accepted             char                                null comment '是否接受請求（Y/N）',
    accept_time          datetime                            null,
    reject_reason        varchar(255)                        null,
    operator             varchar(50)                         null,
    request_time         datetime                            null,
    remark               text                                null,
    raw_payload          text                                null comment '原始請求內容 JSON（保留擴充用）',
    change_type          enum ('INSERT', 'UPDATE', 'DELETE') not null comment '變更類型',
    archived_time        datetime default CURRENT_TIMESTAMP  null comment '歸檔時間',
    archived_by          varchar(50)                         null comment '紀錄來源（系統或操作人員）'
)
    comment 'Gripper 任務請求歷史記錄' charset = utf8mb4;

create index idx_grh_archived
    on gripper_request_history (archived_time);

create index idx_grh_cm
    on gripper_request_history (container_main_id);

create index idx_grh_gripper_time
    on gripper_request_history (gripper_id, request_time);

create index idx_grh_origin
    on gripper_request_history (origin_id);

