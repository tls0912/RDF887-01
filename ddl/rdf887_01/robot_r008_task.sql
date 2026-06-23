create table robot_r008_task
(
    id                   bigint auto_increment comment 'PK'
        primary key,
    log_id               bigint                                                                                      not null comment '對應 mqtt_message_log.id（入站 R008）',
    inbox_id             bigint                                                                                      null comment '對應 mqtt_inbox.id（入佇列那筆，可為 NULL）',
    tid                  varchar(30)                                                                                 not null comment 'R008.TID（例：yyyyMMddHHmmssSSS）',
    lot_id               varchar(64)                                                                                 not null comment 'LOT_ID',
    carrier_id           varchar(64)                                                                                 not null comment 'CARRIERID',
    wip_name             varchar(64)                                                                                 null comment 'WIPNAME（目標儲位，可為 NULL）',
    dest_loc             varchar(64)                                                                                 not null comment 'DEST_LOC（來源機台）',
    eqp_port             varchar(32)                                                                                 not null comment 'EQP_PORT（來源機台 Port）',
    device_name          varchar(64)                                                                                 null comment 'DEVICE_NAME（允許空/NULL）',
    stk_port             varchar(32)                                                                                 null comment '內部 SAA→SEEC 才會有；ASE→廠商禁止',
    tray_high            decimal(10, 3)                                                                              null comment 'TRAY_HIGH',
    tray_type            varchar(64)                                                                                 null comment 'TRAY_TYPE（料號）',
    bin_type             enum ('G', 'B', 'E')                                                                        null comment 'BIN_TYPE（G=GOOD, B=BAD, E=EMPTY）',
    tray_num             int                                                                                         null comment 'TRAY_NUM',
    move_priority        int                                                                                         null comment 'MOVE_PRIORITY（越大越高）',
    mission_trip         varchar(64)                                                                                 null comment 'MISSION_TRIP（本任務里程，文字格式）',
    odo                  decimal(10, 3)                                                                              null comment 'ODO（累積里程）',
    amr_speed            decimal(10, 3)                                                                              null comment 'AMR_SPEED（底車速度）',
    amr_robot_speed      decimal(10, 3)                                                                              null comment 'AMR_ROBOT_SPEED（機械手臂速度）',
    ppkg_body_size       varchar(32)                                                                                 null comment 'PPKG_BODY_SIZE',
    internal_state       enum ('QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED') default 'QUEUED'          not null comment '內部狀態機（簡化）',
    external_last_result enum ('OK', 'START', 'END', 'FAIL', 'CANCEL')                                               null comment '對外最後結果',
    external_last_time   datetime                                                                                    null comment '對外最後結果時間',
    fail_reason          text                                                                                        null comment '失敗原因（FAIL 時必填）',
    cancel_reason        text                                                                                        null comment '取消原因（CANCEL 時可填）',
    raw_message_json     json                                                                                        null comment 'R008.MESSAGE 原樣序列化',
    created_time         datetime                                                          default CURRENT_TIMESTAMP null,
    updated_time         datetime                                                          default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint uq_r008_inbox
        unique (inbox_id),
    constraint uq_r008_log
        unique (log_id),
    constraint fk_r008_task_inbox
        foreign key (inbox_id) references mqtt_inbox (id)
            on delete cascade,
    constraint fk_r008_task_log
        foreign key (log_id) references mqtt_message_log (id)
            on delete cascade,
    constraint chk_r008_move_pri_nonneg
        check ((`move_priority` is null) or (`move_priority` >= 0)),
    constraint chk_r008_tray_num_nonneg
        check ((`tray_num` is null) or (`tray_num` >= 0))
)
    comment 'R008 任務主表（可計算欄位 + 狀態機 + 對外結果快取）' charset = utf8mb4;

create index IDX_robot_r008_task_carrier_id
    on robot_r008_task (carrier_id);

create index idx_r008_bin_type
    on robot_r008_task (bin_type);

create index idx_r008_created_time_id
    on robot_r008_task (created_time, id);

create index idx_r008_dest
    on robot_r008_task (dest_loc, eqp_port);

create index idx_r008_last_result
    on robot_r008_task (external_last_result);

create index idx_r008_tid
    on robot_r008_task (tid);

