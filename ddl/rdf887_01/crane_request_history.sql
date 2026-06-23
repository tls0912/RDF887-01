create table crane_request_history
(
    id                   bigint auto_increment
        primary key,
    origin_id            bigint                                                        not null comment '對應 crane_request.id',
    request_key          varchar(100)                                                  null,
    version              int                                                           null,
    request_type         enum ('INBOUND', 'OUTBOUND', 'RELOCATE')                      null,
    request_source       enum ('UI', 'ASE', 'SYSTEM')                                  null,
    source_request_ref   varchar(100)                                                  null,
    container_main_id    bigint                                                        null,
    source_location_id   bigint                                                        null,
    target_location_id   bigint                                                        null,
    source_location_name varchar(50)                                                   null comment '外部傳入的 Source Location Name',
    target_location_name varchar(50)                                                   null comment '外部傳入的 Target Location Name',
    accepted             char                                                          null,
    accept_time          datetime                                                      null,
    reject_reason        varchar(255)                                                  null,
    operator             varchar(50)                                                   null,
    request_time         datetime                                                      null,
    created_time         datetime                            default CURRENT_TIMESTAMP null comment '建立時間',
    updated_time         datetime                                                      null comment '對應主表 updated_time（最後一次修改時間）',
    remark               text                                                          null,
    raw_payload          text                                                          null,
    change_type          enum ('INSERT', 'UPDATE', 'DELETE') default 'INSERT'          null comment '異動類型',
    archived_time        datetime                            default CURRENT_TIMESTAMP null,
    constraint crane_request_history_chk_1
        check (`accepted` in (_utf8mb4\'Y\',_utf8mb4\'N\'))
)
charset=utf8mb4;

create index IDX_crane_request_history_archived_time
    on crane_request_history (archived_time);

create index IDX_crane_request_history_container_source_target
    on crane_request_history (container_main_id, source_location_id, target_location_id);

create index origin_id
    on crane_request_history (origin_id);

