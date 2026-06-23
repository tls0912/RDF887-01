create table auto_walk_config
(
    id                     bigint auto_increment
        primary key,
    strategy_code          varchar(50)                          not null comment '策略代碼，如 RANDOM、SEQUENTIAL、SINGLE',
    strategy_name          varchar(100)                         not null comment '策略名稱（中文可讀）',
    enabled                tinyint(1) default 0                 not null comment '是否啟用該策略',
    container_limit        int                                  null comment '每輪最多搬幾個容器（NULL 表示不限）',
    excluded_container_ids json                                 null comment '排除的 container_main_id 列表（JSON 陣列）',
    extra_config           json                                 null comment '策略參數擴充用，例如排除哪些儲位等',
    created_time           datetime   default CURRENT_TIMESTAMP null comment '建立時間',
    updated_time           datetime   default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新時間',
    constraint uk_strategy_code
        unique (strategy_code)
)
    charset = utf8mb4;

create index IDX_auto_walk_config_enabled
    on auto_walk_config (enabled);

