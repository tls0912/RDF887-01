create table robot_in_r031
(
    log_id     bigint      not null comment '對應 mqtt_message_log.id（入站 COMMAND）'
        primary key,
    lot_id     varchar(64) not null,
    carrier_id varchar(64) not null,
    wip_name   varchar(64) not null comment 'Manual Port 儲格',
    constraint fk_r031_log
        foreign key (log_id) references mqtt_message_log (id)
            on delete cascade
)
    comment '入站 R031 MESSAGE 明細（WIP→Manual Port）' charset = utf8mb4;

create index idx_r031_carrier
    on robot_in_r031 (carrier_id);

create index idx_r031_lot
    on robot_in_r031 (lot_id);

create index idx_r031_wip
    on robot_in_r031 (wip_name);

