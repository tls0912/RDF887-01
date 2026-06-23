create table infrared_request_history
(
    id                bigint auto_increment
        primary key,
    origin_id         bigint                                                        not null comment '對應 infrared_request.id',
    request_key       varchar(100)                                                  null,
    version           int                                                           null,
    request_source    enum ('UI', 'SYSTEM')                                         null,
    infrared_id       bigint                                                        null,
    container_main_id bigint                                                        null comment '對應的容器主檔',
    task_type         enum ('MEASURE')                                              null comment '任務類型',
    accepted          char                                                          null,
    accept_time       datetime                                                      null,
    reject_reason     varchar(255)                                                  null,
    request_time      datetime                                                      null,
    operator          varchar(50)                                                   null,
    raw_payload       text                                                          null,
    remark            text                                                          null,
    created_time      datetime                            default CURRENT_TIMESTAMP null,
    updated_time      datetime                                                      null comment '對應主表 updated_time',
    change_type       enum ('INSERT', 'UPDATE', 'DELETE') default 'INSERT'          null comment '異動類型',
    archived_time     datetime                            default CURRENT_TIMESTAMP null comment '歸檔時間'
)
    comment 'Infrared 任務請求歷史記錄' charset = utf8mb4;

create index IDX_infrared_request_history_archived_time
    on infrared_request_history (archived_time);

create index idx_hist_container_main_id
    on infrared_request_history (container_main_id);

create index idx_origin_id
    on infrared_request_history (origin_id);

