#!/usr/bin/env bash
# task 03의 실행 기록 가운데 결과를 다시 계산하는 데 필요한 것을 실행 폴더마다 raw.tar.gz로 묶는다(커밋하려고).
#   scripts/task03-pack.sh <실행 폴더>...
# 묶는 것: answers.jsonl, db/*.csv, fault-counts.json, attempt-faults.json(재시도 분석을 한 실행), settings-*.json,
#   timing/timings-*.json, timing/attempts-*.json(있으면), timing/samples-*.json, expect*.json(있으면), lookups.jsonl(있으면).
# 묶지 않는 것: k6-log.txt, warmup*-k6-*.txt, image-build.txt. 꼭 있어야 할 것이 없으면 실패.
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
  for f in attempt-faults.json timing/attempts-order.json timing/attempts-inventory.json timing/attempts-payment.json \
    lookups.jsonl expect.json expect.timing.json expect.retry.json; do
    [[ -f $run/$f ]] && files+=("$f")
  done
  tar -czf "$run/raw.tar.gz" -C "$run" --sort=name --owner=0 --group=0 --mtime='2026-01-01' "${files[@]}"
done
