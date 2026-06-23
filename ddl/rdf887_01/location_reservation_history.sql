create table location_reservation_history
(
    id                bigint auto_increment comment '歷史主鍵'
        primary key,
    origin_id         bigint                                                        not null comment '對應原始 location_reservation_record.id',
    container_main_id bigint                                                        null,
    location_point_id bigint                                                        null,
    reserved_by       varchar(50)                                                   null,
    reserved_reason   varchar(255)                                                  null,
    reserved_time     datetime                                                      null,
    expired_time      datetime                                                      null,
    fulfilled         tinyint(1)                                                    null,
    fulfilled_time    datetime                                                      null,
    cancelled         tinyint(1)                                                    null,
    cancelled_time    datetime                                                      null,
    cancelled_reason  varchar(255)                                                  null,
    expired           tinyint(1)                                                    null,
    change_type       enum ('INSERT', 'UPDATE', 'DELETE') default 'INSERT'          null comment '異動類型',
    archived_time     datetime                            default CURRENT_TIMESTAMP null comment '歸檔時間',
    operator          varchar(50)                                                   null comment '操作者（系統或人員帳號）',
    remark            text                                                          null
)
    comment '儲位預約紀錄歷史' charset = utf8mb4;

create index idx_lrh_archived_time
    on location_reservation_history (archived_time);

create index idx_lrh_container_archived
    on location_reservation_history (container_main_id, archived_time);

create index idx_origin_id
    on location_reservation_history (origin_id);

