create table working_beam_request_history
(
    id              bigint auto_increment
        primary key,
    origin_id       bigint                                                        not null comment '對應 working_beam_request.id',
    request_key     varchar(100)                                                  null,
    version         int                                                           null,
    request_source  enum ('UI', 'SYSTEM')                                         null,
    working_beam_id bigint                                                        null,
    direction       enum ('IN', 'OUT')                                            null,
    accepted        char                                                          null,
    accept_time     datetime                                                      null,
    reject_reason   varchar(255)                                                  null,
    request_time    datetime                                                      null,
    operator        varchar(50)                                                   null,
    raw_payload     text                                                          null,
    remark          text                                                          null,
    created_time    datetime                            default CURRENT_TIMESTAMP null,
    updated_time    datetime                                                      null comment '對應主表 updated_time',
    change_type     enum ('INSERT', 'UPDATE', 'DELETE') default 'INSERT'          null comment '異動類型',
    archived_time   datetime                            default CURRENT_TIMESTAMP null comment '歸檔時間',
    constraint wb_request_hist_fk_origin
        foreign key (origin_id) references working_beam_request (id)
)
    charset = utf8mb4;

create index IDX_working_beam_request_history_archived_time
    on working_beam_request_history (archived_time);

create index idx_origin_id
    on working_beam_request_history (origin_id);

