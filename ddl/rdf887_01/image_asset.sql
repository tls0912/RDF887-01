create table image_asset
(
    id             bigint auto_increment comment '主鍵 ID'
        primary key,
    scene          enum ('S072', 'S073', 'OTHER')                                 not null comment '來源場景（如 S072/S073）',
    ref_type       enum ('MESSAGE', 'EVENT', 'SESSION') default 'MESSAGE'         not null comment '關聯型別：MESSAGE=對應 mqtt_message_log，EVENT=對應 mqtt_event_log，SESSION=其他用途',
    ref_id         bigint                                                         not null comment '關聯主鍵（mqtt_message_log.id 或 mqtt_event_log.id 等）',
    role           varchar(32)                                                    not null comment '角色：raw1/raw2/crop1/roi/thumb 等',
    storage_url    varchar(512)                                                   not null comment '實際儲存路徑（如 file:///data/ocr/... 或 minio://bucket/...）',
    mime           varchar(64)                          default 'image/jpeg'      not null comment '檔案 MIME 類型（預設 image/jpeg）',
    bytes          int                                                            not null comment '檔案大小（Byte）',
    width          int                                                            null comment '影像寬度（像素）',
    height         int                                                            null comment '影像高度（像素）',
    sha256         char(64)                                                       not null comment '影像 SHA-256 校驗碼（判重/驗證用）',
    captured_at    datetime                                                       null comment '影像拍攝時間（若有）',
    retention_days int                                  default 30                not null comment '保留天數，排程用於自動清理',
    created_time   datetime                             default CURRENT_TIMESTAMP null comment '建立時間',
    updated_time   datetime                             default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新時間'
)
    comment '影像外部儲存索引表（S072/S073 等影像引用）' charset = utf8mb4;

create index idx_created
    on image_asset (created_time);

create index idx_ref
    on image_asset (ref_type, ref_id);

create index idx_scene_role
    on image_asset (scene, role);

