create table robot_r029_task
(
    id                   bigint auto_increment comment 'PK'
        primary key,
    log_id               bigint                                                                                      not null comment '對應 mqtt_message_log.id（入站 R029）',
    inbox_id             bigint                                                                                      null comment '對應 mqtt_inbox.id（可為 NULL）',
    tid                  varchar(30)                                                                                 not null comment 'R029.TID（例：yyyyMMddHHmmssSSS）',
    piece_per_lot        int                                                                                         not null comment 'COUNT（每顆要拆幾片）',
    tray_type            varchar(64)                                                                                 null,
    tray_desc            varchar(128)                                                                                null,
    crane_speed          decimal(10, 3)                                                                              null comment 'CRANE_SPEED（STK 內枒杈速度）',
    fork_speed           decimal(10, 3)                                                                              null comment 'FORK_SPEED（STK 內枒杈速度）',
    priority             int                                                               default 5                 not null,
    lane                 enum ('MAIN', 'SUB')                                                                        null comment 'Walker 決策的流道（整張單一致）',
    internal_state       enum ('QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED') default 'QUEUED'          not null comment '內部狀態機（簡化）',
    external_last_result enum ('OK', 'START', 'END', 'NG', 'CANCEL')                                                 null comment '對外最後結果',
    external_last_time   datetime                                                                                    null comment '對外最後結果時間',
    fail_reason          text                                                                                        null comment 'NG/FAILED 原因（可選）',
    raw_message_json     json                                                                                        null comment '整筆 R029（或彙整後）快照',
    active_lane          varchar(4) as (if((`internal_state` = _utf8mb4'PROCESSING'), `lane`, NULL)) stored,
    created_time         datetime                                                          default CURRENT_TIMESTAMP null,
    updated_time         datetime                                                          default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint uq_r029_inbox
        unique (inbox_id),
    constraint uq_r029_log
        unique (log_id),
    constraint uq_running_per_lane
        unique (active_lane),
    constraint fk_r029_task_inbox
        foreign key (inbox_id) references mqtt_inbox (id)
            on delete cascade,
    constraint fk_r029_task_log
        foreign key (log_id) references mqtt_message_log (id)
            on delete cascade,
    constraint chk_r029_piece_pos
        check (`piece_per_lot` > 0),
    constraint chk_r029_pri_nonneg
        check (`priority` >= 0)
)
    comment 'R029 任務主表：單一流道；同流道同時僅一筆 PROCESSING' charset = utf8mb4;

create index IDX_robot_r029_task_internal_state
    on robot_r029_task (internal_state);

create index idx_created_time_id
    on robot_r029_task (created_time, id);

create index idx_r029_lane
    on robot_r029_task (lane);

create index idx_r029_last_result
    on robot_r029_task (external_last_result);

create index idx_r029_state
    on robot_r029_task (internal_state);

create index idx_r029_tid
    on robot_r029_task (tid);

