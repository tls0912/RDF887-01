create table mqtt_inbox
(
    id                bigint auto_increment comment 'PK'
        primary key,
    log_id            bigint                                                                                                                       not null comment '對應 mqtt_message_log.id（入站 COMMAND）',
    tid               varchar(30)                                                                                                                  not null comment '沿用來源 TID',
    cmd_id            varchar(20)                                                                                                                  not null comment 'R007/R008/R029/R031',
    sender            varchar(50)                                                                                                                  not null comment '來源（例 ase）',
    receiver          varchar(50)                                                                                                                  not null comment '接收方（例 saa）',
    topic             varchar(100)                                                                                                                 not null comment '接收的 topic',
    recv_time         datetime                                                                                                                     not null comment '收到時間（來自 mqtt_message_log.timestamp）',
    process_state     enum ('RECEIVED', 'VALIDATED', 'PARSED', 'QUEUED', 'IN_PROGRESS', 'DONE', 'REJECTED', 'CANCELLED') default 'RECEIVED'        not null comment '內部處理狀態',
    process_errors    text                                                                                                                         null comment '錯誤訊息（驗證/解析/業務拒收）',
    processed_time    datetime                                                                                                                     null comment '結案時間（DONE/REJECTED/CANCELLED）',
    lock_owner        varchar(64)                                                                                                                  null comment '鎖持有者（節點/執行緒）',
    lock_until        datetime                                                                                                                     null comment '鎖到期（避免卡死）',
    priority          tinyint                                                                                            default 5                 not null comment '優先權（1高→9低）',
    attempts          int                                                                                                default 0                 not null comment '處理嘗試次數',
    next_attempt_time datetime                                                                                           default CURRENT_TIMESTAMP not null comment '下次嘗試時間（退避）',
    mapped_task_type  varchar(32)                                                                                                                  null comment '對應內部任務類型（如 TRANSFER/DISMANTLE）',
    mapped_task_id    bigint                                                                                                                       null comment '對應內部任務 id',
    created_time      datetime                                                                                           default CURRENT_TIMESTAMP null,
    updated_time      datetime                                                                                           default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint uq_inbox_log
        unique (log_id),
    constraint fk_inbox_log
        foreign key (log_id) references mqtt_message_log (id)
            on delete cascade
)
    comment '入站 COMMAND 處理佇列表（獨立於 mqtt_message_log）' charset = utf8mb4;

create index idx_cmd_id
    on mqtt_inbox (cmd_id);

create index idx_cmd_id_state_priority_recvtime_id
    on mqtt_inbox (cmd_id, process_state, priority, recv_time, id);

create index idx_lock
    on mqtt_inbox (process_state, lock_until);

create index idx_pick
    on mqtt_inbox (process_state, priority, next_attempt_time, recv_time);

create index idx_tid
    on mqtt_inbox (tid);

