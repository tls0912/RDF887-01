create table mqtt_message_log
(
    id             bigint auto_increment comment '主鍵，自動遞增'
        primary key,
    tid            varchar(30)                        not null comment '同一組 command/ack 使用相同 TID',
    cmd_id         varchar(20)                        not null comment '指令編號（如 S001, R007）',
    message_type   enum ('COMMAND', 'ACK')            not null comment '訊息類型（COMMAND / ACK）',
    id_desc        varchar(50)                        null comment '指令說明（如 PC_LINK）',
    topic          varchar(100)                       not null comment 'MQTT topic 名稱',
    sender         varchar(50)                        not null comment '訊息發送方（如 ase / seec）',
    receiver       varchar(50)                        not null comment '訊息接收方（如 saa）',
    timestamp      datetime                           not null comment '訊息實際發生時間（發送或接收）',
    result         varchar(20)                        null comment 'ACK 回覆結果（如 OK / FAIL）',
    result_message text                               null comment 'ACK 補充說明（可為空）',
    payload        json                               null comment '原始 JSON 訊息內容',
    created_time   datetime default CURRENT_TIMESTAMP null comment '建立時間',
    updated_time   datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新時間'
)
    comment 'MQTT 指令與回覆訊息記錄（command / ack）' charset = utf8mb4;

create index IDX_mqtt_message_log_sender_receiver
    on mqtt_message_log (sender, receiver);

create index IDX_mqtt_message_log_tid_timestamp
    on mqtt_message_log (tid, timestamp);

create index idx_cmd_type
    on mqtt_message_log (cmd_id, message_type);

create index idx_created_time
    on mqtt_message_log (created_time);

create index idx_tid
    on mqtt_message_log (tid);

