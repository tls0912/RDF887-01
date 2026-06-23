create table robot_in_r008
(
    log_id       bigint                             not null comment '對應 mqtt_message_log.id（入站 COMMAND）'
        primary key,
    lot_id       varchar(64)                        not null,
    carrier_id   varchar(64)                        not null comment 'CARRIERID',
    wip_name     varchar(64)                        null comment '目標 WIP/STK（R008 為目的地儲位，可為 NULL）',
    dest_loc     varchar(64)                        not null comment '來源機台名稱（EQP → WIP 的 EQP）',
    eqp_port     varchar(32)                        not null comment '來源機台 Port',
    device_name  varchar(64)                        null comment 'AMR 名稱（允許空）',
    stk_port     varchar(32)                        null comment 'SAA→SEEC 才會有；ASE→廠商禁止',
    created_time datetime default CURRENT_TIMESTAMP null,
    updated_time datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint fk_robot_in_r008_log
        foreign key (log_id) references mqtt_message_log (id)
            on delete cascade
)
    comment '入站 R008 MESSAGE 明細（EQP→WIP；一筆對應一個 mqtt_message_log.id）' charset = utf8mb4;

