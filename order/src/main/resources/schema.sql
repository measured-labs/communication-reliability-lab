-- request_id에 유일 제약을 두지 않는다: task 01에는 멱등 키가 없어서, 같은 requestId가 두 번 오면 두 번 처리된다(분석기가 센다).
-- status: PENDING(아래 층 호출 중) → COMPLETED | FAILED | UNKNOWN(아래 층이 처리했는지 모름)
create table if not exists orders (
    id         bigserial primary key,
    request_id text        not null,
    amount     bigint      not null,
    status     text        not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
create index if not exists orders_request_id on orders (request_id);

-- task 04: 멱등 키(docs/idempotency.md). 키가 켜져 있을 때만 쓴다 — 업무 행과 같은 트랜잭션에서 넣는다.
-- row_id = 같은 트랜잭션에 넣은 업무 행의 id. state: IN_PROGRESS | COMPLETED | FAILED | UNKNOWN.
-- 응답 칸은 COMPLETED·FAILED(되돌려 줄 응답)와 UNKNOWN(기록만)에서 채운다.
create table if not exists idempotency_keys (
    key             text        primary key,
    request_id      text        not null,
    row_id          bigint      not null,
    amount          bigint      not null,
    state           text        not null,
    response_status int,
    response_body   text,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now()
);
