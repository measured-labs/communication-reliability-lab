#!/usr/bin/env bash
# task 02의 실행 기록 가운데 결과를 다시 계산하는 데 필요한 것을 실행 폴더마다 raw.tar.gz로 묶는다(커밋하려고).
#   scripts/task02-pack.sh <실행 폴더>...
# 묶는 것: answers.jsonl, lookups.jsonl(있으면), db/*.csv, fault-counts.json, settings-*.json, timing/*.json, expect.json·expect.timing.json(있으면).
# 묶지 않는 것(커밋하지 않는다): k6-log.txt(정답·조회 줄과 k6 경고 — 정답과 조회는 위에 있다), warmup*-k6-*.txt, image-build.txt.
# 묶을 파일 가운데 꼭 있어야 할 것이 없으면 실패.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/lib.sh

(($# > 0)) || die "실행 폴더를 하나 이상 준다"
for run in "$@"; do
  run=${run%/}
  files=(answers.jsonl db/orders.csv db/reservations.csv db/payments.csv fault-counts.json
    settings-order.json settings-inventory.json settings-payment.json
    timing/timings-order.json timing/timings-inventory.json timing/timings-payment.json
    timing/samples-order.json timing/samples-inventory.json timing/samples-payment.json)
  for f in "${files[@]}"; do [[ -f $run/$f ]] || die "$run/$f 가 없다"; done
  for f in lookups.jsonl expect.json expect.timing.json; do [[ -f $run/$f ]] && files+=("$f"); done
  tar -czf "$run/raw.tar.gz" -C "$run" --sort=name --owner=0 --group=0 --mtime='2026-01-01' "${files[@]}"
done
