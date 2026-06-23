create table tt_record_item
(
    id        bigint auto_increment
        primary key,
    record_id bigint         not null,
    step_no   int            not null,
    step_name varchar(50)    not null,
    raw_value int            not null,
    time_sec  decimal(10, 3) not null,
    remark_id varchar(100)   null,
    constraint fk_tt_record_item_record
        foreign key (record_id) references tt_record (id)
)
    charset = utf8mb4;

create index idx_item_record
    on tt_record_item (record_id);

