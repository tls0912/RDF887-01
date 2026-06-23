create table s072_session
(
    id             bigint auto_increment comment '主鍵 ID'
        primary key,
    station_name   varchar(32)                                                                                                                     not null comment '工作站名稱，例如 STK01/STK02',
    camera_ip      varchar(64)                                                                                                                     not null comment '相機 IP，例如 192.168.3.250',
    barcode        varchar(64)                                                                                                                     null comment '條碼（如有）',
    carrier_id     varchar(64)                                                                                                                     not null comment '載具 ID（主要識別用）',
    lot_id         varchar(64)                                                                                                                     null comment '批次號（可選）',
    tray_type      varchar(64)                                                                                                                     null comment 'Tray 類型（可選）',
    image_path_1   varchar(512)                                                                                                                    null comment '拍照圖像檔案路徑 #1（左或第一次）',
    image_path_2   varchar(512)                                                                                                                    null comment '拍照圖像檔案路徑 #2（右或第二次）',
    captured_at_1  datetime                                                                                                                        null comment '拍照時間 #1',
    captured_at_2  datetime                                                                                                                        null comment '拍照時間 #2',
    capture_mode   enum ('SINGLE', 'DUAL')                                                                               default 'DUAL'            not null comment '拍照模式：DUAL=分兩次拍，SINGLE=一次同時拍',
    tid            varchar(64)                                                                                                                     null comment '對應 S072 的 TID（ACK 回填用）',
    result         enum ('OK', 'NG')                                                                                                               null comment 'ASE 回覆結果（OK=PASS, NG=FAIL）',
    result_message varchar(1024)                                                                                                                   null comment 'ASE 回覆訊息',
    error_message  varchar(255)                                                                                                                    null comment '拍照或發送異常說明',
    status         enum ('INIT', 'WAIT_CAPTURE', 'WAIT_FIRST', 'WAIT_SECOND', 'READY', 'SENT', 'ACK', 'ERROR', 'CLOSED') default 'INIT'            not null comment '流程狀態',
    created_at     datetime                                                                                              default CURRENT_TIMESTAMP not null,
    updated_at     datetime                                                                                              default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP
)
    comment 'S072 拍照與檢查會話表（支援單/雙拍照模式）' charset = utf8mb4;

create index IDX_s072_session_created_at
    on s072_session (created_at);

create index IDX_s072_session_status_created_at
    on s072_session (status, created_at);

create index idx_s072_session_carrier_status_id
    on s072_session (carrier_id asc, status asc, id desc);

create index idx_tid_id
    on s072_session (tid asc, id desc);

