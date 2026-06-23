create table container_attr
(
    id                bigint auto_increment comment '主鍵 ID'
        primary key,
    container_main_id bigint                             not null comment '對應 container_main.id',
    attr_key          varchar(50)                        not null comment '屬性名稱（如 thickness_mm、height_mm、weight_g）',
    attr_value        varchar(50)                        not null comment '屬性值（可文字/數字/JSON）',
    unit              varchar(20)                        null comment '屬性單位（如 mm、g、pcs，選填）',
    created_time      datetime default CURRENT_TIMESTAMP null comment '建立時間',
    updated_time      datetime default CURRENT_TIMESTAMP not null comment '更新時間',
    constraint uk_main_attr
        unique (container_main_id, attr_key),
    constraint fk_container_attr_main
        foreign key (container_main_id) references container_main (id)
            on delete cascade
)
    comment '虛擬容器-屬性對應表（可彈性擴充各式欄位）' charset = utf8mb4;

create index IDX_container_attr_attr_key_attr_value
    on container_attr (attr_key, attr_value);

create index IDX_container_attr_created_time
    on container_attr (created_time);

create index idx_container_main_id
    on container_attr (container_main_id);

