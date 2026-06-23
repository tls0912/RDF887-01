create table hmi_display_task
(
    id         bigint auto_increment comment 'PK'
        primary key,
    tid        varchar(32)                                                     not null comment 'S019 的 TID（yyyyMMddHHmmssSSS），冪等用',
    msg_ch     varchar(255)                                                    not null comment '中文訊息（入庫保存用，不寫 PLC）',
    msg_en     varchar(255)                                                    not null comment '英文訊息（實際要寫入 PLC 的內容）',
    status     enum ('PENDING', 'SENT', 'FAILED') default 'PENDING'            not null comment '任務狀態：PENDING=待寫、SENT=已寫入成功、FAILED=寫入失敗',
    attempts   int                                default 0                    not null comment '已嘗試寫入次數（重試用）',
    last_error varchar(500)                                                    null comment '最後一次錯誤訊息（失敗時紀錄）',
    created_at datetime(3)                        default CURRENT_TIMESTAMP(3) not null comment '建立時間（入列時間）',
    updated_at datetime(3)                        default CURRENT_TIMESTAMP(3) not null on update CURRENT_TIMESTAMP(3) comment '最後更新時間',
    sent_at    datetime(3)                                                     null comment '實際成功寫入 PLC 的時間（SENT 時填）',
    constraint uq_plc_hmi_display_tid
        unique (tid) comment '冪等：同一 TID 僅能入列一次'
)
    comment 'HMI 訊息（中/英）' charset = utf8mb4;

create index IDX_hmi_display_task_created_at
    on hmi_display_task (created_at);

create index IDX_hmi_display_task_status_created_at
    on hmi_display_task (status asc, created_at desc);

