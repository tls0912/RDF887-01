create table r029_output_item
(
    id              bigint auto_increment
        primary key,
    task_id         bigint                                                                                                         not null comment '對應 robot_r029_task.id',
    from_carrier_id varchar(64)                                                                                                    not null comment '來源 CARRIER（原 serial/lot）',
    new_carrier_id  varchar(64)                                                                                                    not null comment '拆併後新 CARRIER（新序號/新載具）',
    pieces          int                                                                                  default 0                 not null comment '此新載具實際承載片數',
    state           enum ('NONE', 'STRAPPED', 'LABELED', 'INQUIRY', 'SHELVED', 'REMOVED', 'STOCKED_OUT') default 'NONE'            not null comment '原 zipb_state 改為 state',
    created_time    datetime                                                                             default CURRENT_TIMESTAMP null,
    updated_time    datetime                                                                             default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint uq_r029_task_newcarrier
        unique (task_id, new_carrier_id),
    constraint fk_r029_output_task
        foreign key (task_id) references robot_r029_task (id)
            on delete cascade,
    constraint chk_r029_pieces_nonneg
        check (`pieces` >= 0)
)
    comment 'R029 產出與上架追蹤（逐新載具；狀態欄為 state）' charset = utf8mb4;

create index IDX_r029_output_item_state
    on r029_output_item (state);

create index idx_r029_task_fromcarrier
    on r029_output_item (task_id, from_carrier_id);

