create table transfer_request_history
(
    id                 bigint auto_increment comment '歷史主鍵'
        primary key,
    origin_id          bigint                                                        not null comment '對應原始 transfer_request.id',
    request_key        varchar(100)                                                  not null comment '外部識別用唯一鍵',
    version            int                                                           not null comment '版本控制（遞增）',
    request_source     enum ('UI', 'SYSTEM')                                         not null comment '請求來源',
    transfer_id        bigint                                                        not null comment '指定 Transfer 裝置 ID',
    task_type          enum ('MOVE', 'PICK', 'DROP')                                 not null comment '任務類型',
    container_main_id  bigint                                                        null comment '關聯容器（可選）',
    from_location_id   bigint                                                        null comment '來源位置（PICK 或 MOVE 時適用）',
    to_location_id     bigint                                                        null comment '目標位置（DROP 或 MOVE 時適用）',
    from_location_name varchar(50)                                                   null comment '來源位置顯示名稱（選填）',
    to_location_name   varchar(50)                                                   null comment '目標位置顯示名稱（選填）',
    accepted           char                                default 'N'               null comment '是否接受請求（Y/N）',
    accept_time        datetime                                                      null,
    reject_reason      varchar(255)                                                  null,
    operator           varchar(50)                                                   null,
    request_time       datetime                            default CURRENT_TIMESTAMP null comment '請求時間',
    remark             text                                                          null,
    raw_payload        text                                                          null,
    change_type        enum ('INSERT', 'UPDATE', 'DELETE') default 'INSERT'          null comment '異動類型',
    archived_time      datetime                            default CURRENT_TIMESTAMP null comment '歸檔時間',
    archived_by        varchar(50)                                                   null comment '操作者（系統或人員帳號）',
    archived_remark    text                                                          null comment '歸檔備註'
)
    comment 'Transfer 任務請求歷史紀錄表' charset = utf8mb4;

create index IDX_transfer_request_history_archived_time
    on transfer_request_history (archived_time);

create index idx_container_main_id
    on transfer_request_history (container_main_id);

create index idx_origin_id
    on transfer_request_history (origin_id);

create index idx_transfer_id
    on transfer_request_history (transfer_id);

