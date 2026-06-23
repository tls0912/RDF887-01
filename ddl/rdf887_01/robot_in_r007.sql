create table robot_in_r007
(
    log_id       bigint                             not null comment '對應 mqtt_message_log.id（入站 COMMAND）'
        primary key,
    lot_id       varchar(64)                        not null,
    carrier_id   varchar(64)                        not null comment 'CARRIERID（修正命名）',
    wip_name     varchar(64)                        not null comment '來源 WIP/STK',
    dest_loc     varchar(64)                        not null comment '目的設備',
    eqp_port     varchar(32)                        not null,
    device_name  varchar(64)                        null comment 'AGV/AMR 名稱（允許空）',
    stk_port     varchar(32)                        null comment 'ASE→廠商禁止；SAA→SEEC 才會有',
    created_time datetime default CURRENT_TIMESTAMP null,
    updated_time datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint fk_robot_in_r007_log
        foreign key (log_id) references mqtt_message_log (id)
            on delete cascade
)
    comment '入站 R007 MESSAGE 明細（WIP→EQP；一筆對應一個 mqtt_message_log.id）' charset = utf8mb4;

