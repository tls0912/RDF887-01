create table strapping_precheck_result
(
    id             bigint auto_increment comment 'PK'
        primary key,
    tid            varchar(64)                        not null comment '對應 S068 的 TID（唯一）',
    result         enum ('OK', 'NG', 'NotReady')      null comment 'ACK 的結果：OK / NG / NotReady',
    result_message varchar(500)                       null comment '補充說明',
    created_time   datetime default CURRENT_TIMESTAMP not null comment '建立時間（通常是 ACK 時間）',
    constraint uq_tid
        unique (tid)
)
    comment 'S068 打帶前狀態確認結果' charset = utf8mb4;

