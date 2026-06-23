create table door_access_info
(
    id                 bigint auto_increment comment 'PK'
        primary key,
    tid                varchar(32)                                                                            not null comment '對應 S011/S012 的 TID（yyyyMMddHHmmssSSS），冪等鍵',
    door_no            int                                                                                    not null comment '門號（1..N）',
    req_value          tinyint                                                                                not null comment '請求值：1=OPEN(開門), 2=CLOSE(關門)',
    status             enum ('PENDING', 'ACK_OK', 'ACK_NG', 'TIMEOUT', 'CANCELLED') default 'PENDING'         not null comment '請求狀態：PENDING=已送出待回覆；ACK_OK/ACK_NG=已回；TIMEOUT=逾時；CANCELLED=取消',
    ack_result         enum ('OK', 'NG')                                                                      null comment 'ACK 結果：OK/NG（回覆後填）',
    ack_message        varchar(255)                                                                           null comment 'ACK 結果說明（如 NG 的原因）',
    staff_list         json                                                                                   null comment '通過驗證的人員工號清單（JSON array）',
    ack_at             datetime                                                                               null comment '收到 ACK 的時間',
    retries            int                                                          default 0                 not null comment '已重送次數（如需）',
    last_error         varchar(500)                                                                           null comment '最後一次錯誤訊息（如逾時標記或其他）',
    writeback_status   enum ('WAITING', 'WRITTEN', 'FAILED')                        default 'WAITING'         not null comment '寫 PLC 狀態：WAITING=等寫、WRITTEN=已寫、FAILED=寫失敗',
    writeback_attempts int                                                          default 0                 not null comment '寫 PLC 嘗試次數',
    writeback_error    varchar(500)                                                                           null comment '寫 PLC 失敗最後錯誤',
    written_at         datetime                                                                               null comment '實際寫入 PLC 的時間',
    created_at         datetime                                                     default CURRENT_TIMESTAMP not null comment '建立時間',
    updated_at         datetime                                                     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '最後更新時間',
    constraint uq_door_access_tid
        unique (tid) comment '冪等：同一 TID 僅能存在一次'
)
    comment '安全門開/關檢核資訊' charset = utf8mb4;

create index idx_status_created
    on door_access_info (status, created_at)
    comment '查 PENDING/逾時時好用';

create index idx_writeback
    on door_access_info (writeback_status, created_at)
    comment 'PLC writer 撈取用';

