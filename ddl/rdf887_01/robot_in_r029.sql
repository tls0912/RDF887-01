create table robot_in_r029
(
    log_id    bigint       not null comment '對應 mqtt_message_log.id（入站 COMMAND）'
        primary key,
    count     int          not null comment '批數（payload 雖為字串，這裡用數字存）',
    tray_type varchar(64)  null,
    tray_desc varchar(128) null,
    constraint fk_r029_log
        foreign key (log_id) references mqtt_message_log (id)
            on delete cascade
)
    comment '入站 R029 MESSAGE 主檔（拆併打帶）' charset = utf8mb4;

