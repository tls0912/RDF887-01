create table l005_session
(
    id                    bigint auto_increment comment '流水號'
        primary key,
    barcode               varchar(128)                                                                    not null comment '條碼（trim 後）',
    tid                   varchar(64)                                                                     not null comment 'L005 會話唯一識別',
    internal_state        enum ('INIT', 'SENT', 'ACKED', 'COMPLETED', 'FAILED') default 'INIT'            not null comment '內部狀態機（簡化）',
    ack_deadline_at       datetime                                                                        null comment '等待 ACK 截止時間（逾時轉 FAILED）',
    external_last_result  enum ('OK', 'START', 'END', 'FAIL', 'CANCEL')                                   null comment '對外最後結果',
    external_last_time    datetime                                                                        null comment '對外最後結果時間',
    fail_reason           text                                                                            null comment '失敗原因（FAIL/FAILED 時建議填寫）',
    peer_result           enum ('PASS', 'FAIL', '')                             default ''                not null comment '對方條碼檢核結果',
    peer_result_msg       varchar(512)                                          default ''                not null comment '對方結果訊息',
    peer_carrier_id       varchar(128)                                                                    null,
    peer_lot_id           varchar(128)                                                                    null,
    peer_tray_high        varchar(64)                                                                     null,
    peer_tray_type        varchar(128)                                                                    null,
    peer_msg_type         varchar(32)                                                                     null,
    peer_ack_at           datetime                                                                        null,
    peer_ack_payload_json json                                                                            null,
    is_valid              tinyint(1)                                            default 1                 not null comment '是否現役（1=有效, 0=失效）',
    invalid_by_tid        varchar(64)                                                                     null comment '被哪個新 TID 取代（僅被取代時填）',
    created_at            datetime                                              default CURRENT_TIMESTAMP not null,
    updated_at            datetime                                              default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    constraint uk_tid
        unique (tid)
)
    comment 'L005 會話：對方檢核結果與我方進度分欄；只註記失效' charset = utf8mb4;

create index IDX_l005_session_barcode_is_valid_created_at
    on l005_session (barcode asc, is_valid asc, created_at desc);

create index IDX_l005_session_created_at
    on l005_session (created_at);

create index idx_peer_carrier_id_id
    on l005_session (peer_carrier_id asc, id desc);

create index idx_updated
    on l005_session (updated_at);

