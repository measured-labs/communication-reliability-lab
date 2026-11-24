#!/usr/bin/env bash
# task 04: 멱등 키가 서비스의 재기동을 넘어 남는지 — 확인 한 번(실험이 아니다, 설계 3절).
#   scripts/task04-restart-check.sh
# 키를 켜고 띄운다 → 요청 하나(키 K) → 이 프로젝트의 order 컨테이너 하나를 다시 띄운다(같은 이미지 — 메모리의 것은 사라지고 DB는 남는다)
# → 같은 키로 다시 보낸다 → 201과 Idempotent-Replayed: true, 세 층의 업무 행과 키 행이 하나씩인지 본다.
# 기록: runs/restart-check-<시각>/result.txt. 종료 코드: 0 통과, 1 실패.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/lib.sh

IDEMPOTENCY_ENABLED=true scripts/up.sh
NAME="restart-check-$(date +%Y%m%dT%H%M%S)"
OUT="runs/${NAME}"
mkdir -p "$OUT"
KEY="$NAME-k1"
send() {
  curl -sS -m 10 -D - -o "$OUT/body-$1.json" -X POST localhost:8081/orders -H 'Content-Type: application/json' \
    -H "X-Request-Id: $KEY" -H "Idempotency-Key: $KEY" -d '{"amount":4242}' | tr -d '\r' > "$OUT/headers-$1.txt"
}
count() { psql_lab "$1" "$2"; }
{
  echo "git_commit=$(git rev-parse HEAD 2>/dev/null || echo 모름)"
  echo "started_at=$(date -Iseconds)"
  echo "key=$KEY"
  send first
  echo "first: $(head -1 "$OUT/headers-first.txt") $(grep -i '^Idempotent-Replayed' "$OUT/headers-first.txt" || echo 'Idempotent-Replayed 없음')"
  before=$(docker inspect --format '{{.State.StartedAt}}' "$("${COMPOSE[@]}" ps -q order)")
  "${COMPOSE[@]}" restart order > /dev/null
  wait_ready
  after=$(docker inspect --format '{{.State.StartedAt}}' "$("${COMPOSE[@]}" ps -q order)")
  echo "order StartedAt: $before → $after"
  send second
  echo "second: $(head -1 "$OUT/headers-second.txt") $(grep -i '^Idempotent-Replayed' "$OUT/headers-second.txt" || echo 'Idempotent-Replayed 없음')"
  echo "bodies equal: $(cmp -s "$OUT/body-first.json" "$OUT/body-second.json" && echo yes || echo no)"
  echo "orders=$(count order_db "select count(*) from orders where request_id = '$KEY'") reservations=$(count inventory_db "select count(*) from reservations where request_id = '$KEY'") payments=$(count payment_db "select count(*) from payments where request_id = '$KEY'")"
  echo "keys order=$(count order_db "select count(*) from idempotency_keys where key = '$KEY'") inventory=$(count inventory_db "select count(*) from idempotency_keys where key = '$KEY'") payment=$(count payment_db "select count(*) from idempotency_keys where key = '$KEY'")"
  echo "order counts after restart: $(curl -sf localhost:8081/idempotency-counts)"
} | tee "$OUT/result.txt"
r=$OUT/result.txt
grep -q '^first: HTTP/1.1 201' "$r" && grep -Eq '^second: HTTP/1.1 201 +Idempotent-Replayed: true' "$r" && grep -q '^bodies equal: yes' "$r" \
  && grep -q '^orders=1 reservations=1 payments=1$' "$r" && grep -q '^keys order=1 inventory=1 payment=1$' "$r" \
  && ! grep -q 'StartedAt: \(.*\) → \1$' "$r" || die "재기동 확인 실패 — $r"
log "재기동 확인 통과 — $r"
