-- request_id에 유일 제약을 두지 않는다(order의 schema.sql과 같은 까닭).
-- 결제는 맨 아래 층이라 한 트랜잭션에 APPROVED로 들어간다.
create table if not exists payments (
    id         bigserial primary key,
    request_id text        not null,
    amount     bigint      not null,
    status     text        not null,
    created_at timestamptz not null default now()
);
create index if not exists payments_request_id on payments (request_id);
