-- request_id에 유일 제약을 두지 않는다(order의 schema.sql과 같은 까닭).
-- status: PENDING(결제 호출 중) → RESERVED | FAILED | UNKNOWN(결제가 처리됐는지 모름)
create table if not exists reservations (
    id         bigserial primary key,
    request_id text        not null,
    status     text        not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
create index if not exists reservations_request_id on reservations (request_id);
