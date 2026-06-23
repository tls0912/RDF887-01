create table robot_r007_task
(
    id                    bigint auto_increment comment 'PK'
        primary key,
    log_id                bigint                                                                                      not null comment '對應 mqtt_message_log.id（入站 R007）',
    inbox_id              bigint                                                                                      null comment '對應 mqtt_inbox.id（入佇列那筆，可為 NULL）',
    tid                   varchar(30)                                                                                 not null comment 'R007.TID（例：yyyyMMddHHmmssSSS）',
    lot_id                varchar(64)                                                                                 not null comment 'LOT_ID',
    carrier_id            varchar(64)                                                                                 not null comment 'CARRIERID（修正命名）',
    wip_name              varchar(64)                                                                                 not null comment 'WIPNAME（來源儲格/站位）',
    dest_loc              varchar(64)                                                                                 not null comment 'DEST_LOC（目的設備）',
    eqp_port              varchar(32)                                                                                 not null comment 'EQP_PORT（目的 Port）',
    device_name           varchar(64)                                                                                 null comment 'DEVICE_NAME（允許空字串或 NULL）',
    stk_port              varchar(32)                                                                                 null comment 'ZIP 出料 Port（由 Worker 決策後寫入）',
    tray_high             decimal(10, 3)                                                                              null comment 'TRAY_HIGH',
    tray_type             varchar(64)                                                                                 null comment 'TRAY_TYPE（料號）',
    tray_num              int                                                                                         null comment 'TRAY_NUM',
    move_priority         int                                                                                         null comment 'MOVE_PRIORITY',
    mission_trip          varchar(64)                                                                                 null comment 'MISSION_TRIP',
    odo                   decimal(10, 3)                                                                              null comment 'ODO',
    amr_speed             decimal(10, 3)                                                                              null comment 'AMR_SPEED',
    amr_robot_speed       decimal(10, 3)                                                                              null comment 'AMR_ROBOT_SPEED',
    ppkg_body_size        varchar(32)                                                                                 null comment 'PPKG_BODY_SIZE',
    flip                  enum ('Y', 'N')                                                                             null comment 'FLIP（翻轉 Y/N）',
    zip_required          tinyint(1)                                                        default 1                 not null comment '是否需要 ZIP 派單',
    amr_required          tinyint(1)                                                        default 1                 not null comment '是否需要 AMR 轉發',
    zip_state             enum ('PENDING', 'SENT', 'ACCEPTED', 'REJECTED', 'ERROR')         default 'PENDING'         not null comment 'ZIP 派單狀態（同步：PENDING→SENT→ACCEPTED/REJECTED/ERROR）',
    zip_attempts          int                                                               default 0                 not null comment 'ZIP 派單嘗試次數',
    zip_last_attempt_time datetime                                                                                    null comment '上次派單時間',
    zip_accept_time       datetime                                                                                    null comment 'ZIP 接單（Result=0）時間',
    zip_result_code       varchar(32)                                                                                 null comment 'ZIP 回傳 Result/錯誤碼',
    zip_result_message    varchar(255)                                                                                null comment 'ZIP 回傳訊息',
    zip_request_json      json                                                                                        null comment '最後一次派單 Request JSON',
    zip_response_json     json                                                                                        null comment '最後一次派單 Response JSON',
    amr_tid               varchar(30)                                                                                 null comment '轉發給 AMR 的 TID（通常沿用原 R007.TID；若策略不同可另編號）',
    amr_state             enum ('PENDING', 'SENT', 'OK', 'START', 'END', 'FAIL', 'CANCEL')  default 'PENDING'         not null comment 'AMR 轉發狀態（非同步：PENDING→SENT→OK→START→END | FAIL | CANCEL）',
    amr_attempts          int                                                               default 0                 not null comment 'AMR 轉發嘗試次數',
    amr_last_attempt_time datetime                                                                                    null comment '上次轉發時間',
    amr_last_ack_time     datetime                                                                                    null comment '最後一次 ACK 時間',
    amr_result_message    varchar(255)                                                                                null comment '最後一次 ACK 的補充訊息',
    amr_forward_log_id    bigint                                                                                      null comment '對應 mqtt_message_log.id（我方發出的 R007 COMMAND）',
    amr_ack_start_log_id  bigint                                                                                      null comment '收到 START ACK 的 mqtt_message_log.id',
    amr_ack_end_log_id    bigint                                                                                      null comment '收到 END ACK 的 mqtt_message_log.id',
    amr_request_json      json                                                                                        null comment '最後一次轉發給 AMR 的 R007（MESSAGE 部分）',
    amr_last_ack_json     json                                                                                        null comment '最後一次收到的 ACK JSON',
    internal_state        enum ('QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED') default 'QUEUED'          not null comment '內部狀態機（簡化）',
    external_last_result  enum ('OK', 'START', 'END', 'FAIL', 'CANCEL')                                               null comment '對外最後結果',
    external_last_time    datetime                                                                                    null comment '對外最後結果時間',
    fail_reason           text                                                                                        null comment '失敗原因（FAIL 時必填）',
    cancel_reason         text                                                                                        null comment '取消原因（CANCEL 時可填）',
    raw_message_json      json                                                                                        null comment 'R007.MESSAGE 原樣序列化',
    created_time          datetime                                                          default CURRENT_TIMESTAMP null,
    updated_time          datetime                                                          default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint uq_r007_inbox
        unique (inbox_id),
    constraint uq_r007_log
        unique (log_id),
    constraint fk_r007_task_ack_end
        foreign key (amr_ack_end_log_id) references mqtt_message_log (id)
            on delete set null,
    constraint fk_r007_task_ack_start
        foreign key (amr_ack_start_log_id) references mqtt_message_log (id)
            on delete set null,
    constraint fk_r007_task_inbox
        foreign key (inbox_id) references mqtt_inbox (id)
            on delete cascade,
    constraint fk_r007_task_log
        foreign key (log_id) references mqtt_message_log (id)
            on delete cascade,
    constraint fk_r007_task_log_fwd
        foreign key (amr_forward_log_id) references mqtt_message_log (id)
            on delete set null,
    constraint chk_move_pri_nonneg
        check ((`move_priority` is null) or (`move_priority` >= 0)),
    constraint chk_tray_num_nonneg
        check ((`tray_num` is null) or (`tray_num` >= 0))
)
    comment 'R007 任務主表（Worker 決策 STK_PORT；簡化內部狀態 + 對外結果快取）' charset = utf8mb4;

create index IDX_robot_r007_task_carrier_id_updated_time
    on robot_r007_task (carrier_id asc, updated_time desc);

create index IDX_robot_r007_task_created_time_id
    on robot_r007_task (created_time, id);

create index idx_r007_amr_fwdlog
    on robot_r007_task (amr_forward_log_id);

create index idx_r007_amr_tid
    on robot_r007_task (amr_tid);

create index idx_r007_dest
    on robot_r007_task (dest_loc, eqp_port);

create index idx_r007_tid
    on robot_r007_task (tid);

