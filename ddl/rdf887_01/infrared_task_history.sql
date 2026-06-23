create table infrared_task_history
(
    id                bigint auto_increment
        primary key,
    origin_id         bigint                                                                                                                          not null comment '對應 infrared_task.id',
    request_id        bigint                                                                                                                          null comment '對應 infrared_request.id',
    infrared_id       bigint                                                                                                                          null comment '對應 infrared.id',
    container_main_id bigint                                                                                                                          null comment '對應的容器主檔',
    task_type         enum ('MEASURE')                                                                                                                null comment '任務類型，目前僅 MEASURE',
    task_status       enum ('PENDING', 'DISPATCHED', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'CANCELLED', 'SKIPPED', 'RETRY') default 'PENDING'         null comment '任務狀態',
    priority_level    int                                                                                                                             null comment '任務優先級',
    dispatched_time   datetime                                                                                                                        null comment '下派時間',
    completed_time    datetime                                                                                                                        null comment '完成時間',
    cancelled_time    datetime                                                                                                                        null comment '取消時間',
    done_time         datetime                                                                                                                        null comment '實際結束時間（完成、取消或失敗）',
    cancelled_reason  varchar(200)                                                                                                                    null comment '取消原因',
    remark            text                                                                                                                            null comment '備註',
    created_time      datetime                                                                                              default CURRENT_TIMESTAMP not null comment '任務建立時間',
    updated_time      datetime                                                                                                                        null comment '對應主表 updated_time（最後一次變更時間）',
    change_type       enum ('INSERT', 'UPDATE', 'DELETE')                                                                   default 'INSERT'          null comment '異動類型',
    archived_time     datetime                                                                                              default CURRENT_TIMESTAMP null comment '歸檔時間'
)
    comment 'Infrared 任務歷史記錄' charset = utf8mb4;

create index idx_infrared_id
    on infrared_task_history (infrared_id);

create index idx_irth_archived_time
    on infrared_task_history (archived_time);

create index idx_irth_container_main_id
    on infrared_task_history (container_main_id);

create index idx_irth_done_time
    on infrared_task_history (done_time);

create index idx_origin_id
    on infrared_task_history (origin_id);

create index idx_request_id
    on infrared_task_history (request_id);

