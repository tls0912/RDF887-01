create table robot_in_r029_lot
(
    id         bigint auto_increment
        primary key,
    log_id     bigint      not null comment '對應 robot_in_r029.log_id（= mqtt_message_log.id）',
    carrier_id varchar(64) not null,
    constraint uq_r029_log_lot
        unique (log_id, carrier_id),
    constraint fk_r029_lot_log
        foreign key (log_id) references robot_in_r029 (log_id)
            on delete cascade
)
    comment '入站 R029 LOT 清單' charset = utf8mb4;

create index idx_r029_lotid
    on robot_in_r029_lot (carrier_id);

