create table mqtt_inbox_status_log
(
    id            bigint auto_increment
        primary key,
    inbox_id      bigint                                not null comment '對應 mqtt_inbox.id',
    from_state    varchar(16)                           null,
    to_state      varchar(16)                           not null,
    changed_by    varchar(50) default 'system'          null,
    change_reason varchar(255)                          null,
    change_time   datetime    default CURRENT_TIMESTAMP not null,
    constraint fk_inbox_status_inbox
        foreign key (inbox_id) references mqtt_inbox (id)
            on delete cascade
)
    comment '入站 COMMAND 處理狀態歷程' charset = utf8mb4;

create index idx_inbox_time
    on mqtt_inbox_status_log (inbox_id, change_time);

