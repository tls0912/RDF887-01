create table mqtt_connection_log
(
    id            bigint auto_increment comment '主鍵 ID'
        primary key,
    remote_system varchar(50)                        not null comment '對方系統代碼（如 SEEC、ASE）',
    status        enum ('CONNECTED', 'DISCONNECTED') not null comment '連線狀態（CONNECTED=建立連線，DISCONNECTED=判斷斷線）',
    event_time    datetime                           not null comment '事件發生時間（如接收到 S001 / 判斷為中斷）',
    reason        text                               null comment '原因或附註說明（可記錄 timeout、retry 次數等）',
    created_time  datetime default CURRENT_TIMESTAMP null comment '建立時間'
)
    comment 'MQTT 連線與斷線事件歷程表（可用於日誌、統計、通知）' charset = utf8mb4;

create index IDX_mqtt_connection_log_created_time
    on mqtt_connection_log (created_time);

