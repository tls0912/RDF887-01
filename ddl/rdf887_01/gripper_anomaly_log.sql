create table gripper_anomaly_log
(
    id              bigint auto_increment
        primary key,
    gripper_id      varchar(50)                                          not null comment '裝置代號',
    anomaly_type    enum ('TIMEOUT', 'DUPLICATE_STATE', 'LOST_PROGRESS') not null comment '異常類型',
    description     text                                                 null comment '詳細異常說明',
    related_task_id bigint                                               null comment '若異常與任務有關，記錄任務 ID',
    occurred_time   datetime default CURRENT_TIMESTAMP                   null comment '異常發生時間'
)
    charset = utf8mb4;

