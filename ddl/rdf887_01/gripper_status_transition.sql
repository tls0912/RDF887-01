create table gripper_status_transition
(
    id                   bigint auto_increment
        primary key,
    gripper_id           varchar(50)                                       not null comment '裝置代號',
    from_status          varchar(30)                                       null comment '來源狀態（如 IDLE、RUNNING）',
    to_status            varchar(30)                                       null comment '目標狀態（如 RUNNING、DONE）',
    sub_status           enum ('UNKNOWN', 'MOVING', 'PICKING', 'DROPPING') null comment 'RUNNING 狀態細分類（子行為）',
    triggered_by_task_id bigint                                            null comment '若為任務觸發，紀錄來源任務 ID',
    snapshot_time        datetime                                          null comment '對應 PLC snapshot 時間',
    duration_ms          bigint                                            null comment '來源狀態持續時間（毫秒）',
    changed_time         datetime default CURRENT_TIMESTAMP                null comment '狀態變更時間'
)
    charset = utf8mb4;

