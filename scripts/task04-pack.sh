#!/usr/bin/env bash
# task 04의 실행 기록 가운데 결과를 다시 계산하는 데 필요한 것을 실행 폴더마다 raw.tar.gz로 묶는다(커밋하려고).
#   scripts/task04-pack.sh <실행 폴더>...
# 묶는 것: task03-pack.sh의 것 + db/idempotency-*.csv, idempotency-counts.json, drop-faults.json, resends.jsonl,
#   retry-budget-start.json, expect.idem.json(있으면 — task 04가 아닌 시나리오(꺼진 상태 확인)에는 없다),
#   db/row-ids-*.csv·db/idempotency-row-ids-*.csv(있으면 — row_id를 내보낸 뒤의 실행만. config.env의 row_ids_exported=yes).
# 묶지 않는 것: k6-log.txt, resend-k6-log.txt, warmup*-k6-*.txt, image-build.txt. 꼭 있어야 할 것이 없으면 실패.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/lib.sh

(($# > 0)) || die "실행 폴더를 하나 이상 준다"
for run in "$@"; do
  run=${run%/}
  files=(answers.jsonl db/orders.csv db/reservations.csv db/payments.csv fault-counts.json
    settings-order.json settings-inventory.json settings-payment.json retry-budget-start.json
    timing/timings-order.json timing/timings-inventory.json timing/timings-payment.json
    timing/samples-order.json timing/samples-inventory.json timing/samples-payment.json)
  for f in "${files[@]}"; do [[ -f $run/$f ]] || die "$run/$f 가 없다"; done
  if grep -qx 'row_ids_exported=yes' "$run/config.env"; then
    for l in order inventory payment; do
      for f in db/row-ids-$l.csv db/idempotency-row-ids-$l.csv; do [[ -f $run/$f ]] || die "$run/$f 가 없다(row_ids_exported=yes)"; done
    done
  fi
  for f in attempt-faults.json timing/attempts-order.json timing/attempts-inventory.json timing/attempts-payment.json \
    lookups.jsonl expect.json expect.timing.json expect.retry.json expect.idem.json resends.jsonl \
    db/idempotency-order.csv db/idempotency-inventory.csv db/idempotency-payment.csv idempotency-counts.json drop-faults.json \
    db/row-ids-order.csv db/row-ids-inventory.csv db/row-ids-payment.csv \
    db/idempotency-row-ids-order.csv db/idempotency-row-ids-inventory.csv db/idempotency-row-ids-payment.csv; do
    [[ -f $run/$f ]] && files+=("$f")
  done
  tar -czf "$run/raw.tar.gz" -C "$run" --sort=name --owner=0 --group=0 --mtime='2026-01-01' "${files[@]}"
done
