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
