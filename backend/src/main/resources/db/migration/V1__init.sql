-- テーブルの正（docs/04_契約/テーブル定義書.md から起こした）。
-- WHY: timestamptz は `timestamp with time zone` と書く。PostgreSQL では同じ型で、テスト用の H2 でも通る書き方にするため。

create table equipment (
    equipment_id bigint generated always as identity primary key,
    name         varchar(50) not null unique,
    created_at   timestamp with time zone not null default now()
);

create table lending_request (
    request_id     bigint generated always as identity primary key,
    applicant_name varchar(50) not null,
    equipment_id   bigint not null references equipment (equipment_id),
    start_date     date not null,
    due_date       date not null,
    reason         varchar(200),
    status         varchar(10) not null,
    version        integer not null default 0,
    created_at     timestamp with time zone not null default now(),
    updated_at     timestamp with time zone not null default now(),
    -- WHY: 画面・API に続く 3 段目の検査。アプリを通らない書き込みも止める。
    constraint ck_applicant_name_not_blank check (length(trim(applicant_name)) > 0),
    constraint ck_due_after_start check (due_date >= start_date),
    constraint ck_status check (status in ('REQUESTED', 'APPROVED', 'REJECTED', 'LENT', 'RETURNED'))
);

-- WHY: 一覧の絞り込みが状態で引く。
create index ix_lending_request_status on lending_request (status);

create table status_history (
    history_id  bigint generated always as identity primary key,
    request_id  bigint not null references lending_request (request_id),
    from_status varchar(10),
    to_status   varchar(10) not null,
    changed_at  timestamp with time zone not null default now()
);

create index ix_status_history_request on status_history (request_id);
