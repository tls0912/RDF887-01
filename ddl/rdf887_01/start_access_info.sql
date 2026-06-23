create table start_access_info
(
    id                 bigint auto_increment comment 'PK'
        primary key,
    tid                varchar(32)                                                                            not null comment '對應 S013 的 TID（yyyyMMddHHmmssSSS），冪等鍵',
    target_code        varchar(16)                                                                            not null comment '啟動對象：WIP / ZIPA / ZIPB / FSK6001A',
    req_value          smallint unsigned                                                                      not null comment '請求值：1=START, 256=RESET',
    status             enum ('PENDING', 'ACK_OK', 'ACK_NG', 'TIMEOUT', 'CANCELLED') default 'PENDING'         not null comment '狀態：送出待回覆/已回/逾時/取消',
    ack_result         enum ('OK', 'NG')                                                                      null comment 'ACK 結果：OK/NG',
    ack_message        varchar(255)                                                                           null comment 'ACK 結果說明（如 NG 原因）',
    staff_list         json                                                                                   null comment '通過驗證的人員工號清單（JSON array）',
    ack_at             datetime                                                                               null comment '收到 ACK 的時間',
    retries            int                                                          default 0                 not null comment '重送次數（若有）',
    last_error         varchar(500)                                                                           null comment '最後一筆錯誤資訊',
    writeback_status   enum ('WAITING', 'WRITTEN', 'FAILED')                        default 'WAITING'         not null comment '寫 PLC 狀態',
    writeback_attempts int                                                          default 0                 not null comment '寫 PLC 嘗試次數',
    writeback_error    varchar(500)                                                                           null comment '最後一次寫 PLC 的錯誤',
    written_at         datetime                                                                               null comment '成功寫 PLC 的時間',
    created_at         datetime                                                     default CURRENT_TIMESTAMP not null comment '建立時間',
    updated_at         datetime                                                     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '最後更新時間',
    constraint uq_start_access_tid
        unique (tid) comment '冪等：同一 TID 只允許一筆',
    constraint chk_req_value
        check (`req_value` in (1, 256))
)
    comment 'RESET/START 驗證資訊' charset = utf8mb4;

create index idx_status_created
    on start_access_info (status, created_at);

create index idx_writeback
    on start_access_info (writeback_status, created_at);

