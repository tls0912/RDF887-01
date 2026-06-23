create table crane_task_follow_up_record
(
    id                bigint auto_increment
        primary key,
    root_task_id      bigint                               not null comment '源頭任務 ID（即最初失敗任務）',
    original_task_id  bigint                               not null comment '此次補償所根據的任務 ID',
    reason_code       varchar(50)                          not null comment '補償原因代碼',
    reason_desc       varchar(255)                         null comment '補償原因描述',
    follow_up_task_id bigint                               null comment '補償產生的新任務 ID',
    handled           tinyint(1) default 0                 null comment '是否已處理完成',
    handled_time      datetime                             null comment '標記為已處理的時間',
    created_time      datetime   default CURRENT_TIMESTAMP null comment '建立時間',
    updated_time      datetime   default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新時間'
)
    charset = utf8mb4;

create index idx_created_time
    on crane_task_follow_up_record (created_time);

create index idx_follow_up_task
    on crane_task_follow_up_record (follow_up_task_id);

create index idx_handled_time
    on crane_task_follow_up_record (handled, handled_time);

create index idx_original_task
    on crane_task_follow_up_record (original_task_id);

create index idx_root_task
    on crane_task_follow_up_record (root_task_id);

