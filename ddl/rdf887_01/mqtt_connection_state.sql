create table mqtt_connection_state
(
    id                  bigint auto_increment comment '主鍵 ID'
        primary key,
    remote_system       varchar(50)                        not null comment '對方系統代碼（如 SEEC、ASE）',
    connected           tinyint(1)                         not null comment '目前是否連線中（true=連線，false=斷線）',
    last_connected_time datetime                           null comment '最後一次成功建立連線時間（對方主動發出 S001，或我方發出 S001 並收到 ACK 時更新）',
    last_heartbeat_time datetime                           null comment '最後一次收到 S002 ACK 的時間（用於監測心跳是否中斷）',
    created_time        datetime default CURRENT_TIMESTAMP null comment '建立時間',
    updated_time        datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '最後更新時間',
    constraint remote_system
        unique (remote_system)
)
    comment 'MQTT 對外系統連線狀態快照表（每個對方系統僅一筆）' charset = utf8mb4;

