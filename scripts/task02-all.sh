#!/usr/bin/env bash
# task 02의 보정과 실험 가·나·다를 설계(docs/experiments/task-02-design.md)의 조건 그대로 차례로 돌린다.
#   scripts/task02-all.sh [cal|ga|na|da ...]   (인자가 없으면 넷 다, 이 차례로)
# 보정이 하나라도 실패하면 실험을 돌리지 않고 멈춘다(보정을 통과하기 전의 수치는 쓰지 않는다).
# 실험의 실행이 실패하면 runs/task-02-index.tsv에 종료 코드를 남기고 다음 조건으로 간다. 끝에 실패한 조건이 있으면 종료 코드 1.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/lib.sh

parts=("$@")
((${#parts[@]} > 0)) || parts=(cal ga na da)
failed=()
cond() { scripts/task02.sh "$@" || failed+=("$2"); }

for part in "${parts[@]}"; do
  case "$part" in
    cal)
      DISCARD=no REPS=1 scripts/task02.sh cal cal-ft-t300 cal-false-timeout \
        ORDER_TO_INVENTORY_TIMEOUT=5000ms INVENTORY_TO_PAYMENT_TIMEOUT=300ms TIMING_EXPECT=load/expectations/cal-false-timeout-t300.timing.json \
        || die "보정 cal-ft-t300 실패 — 실험을 돌리지 않는다"
      DISCARD=no REPS=1 scripts/task02.sh cal cal-ft-t1000 cal-false-timeout \
        ORDER_TO_INVENTORY_TIMEOUT=5000ms INVENTORY_TO_PAYMENT_TIMEOUT=1000ms TIMING_EXPECT=load/expectations/cal-false-timeout-t1000.timing.json \
        || die "보정 cal-ft-t1000 실패 — 실험을 돌리지 않는다"
      DISCARD=no REPS=1 scripts/task02.sh cal cal-sampler cal-sampler \
        ORDER_TO_INVENTORY_TIMEOUT=2000ms INVENTORY_TO_PAYMENT_TIMEOUT=1000ms \
        || die "보정 cal-sampler 실패 — 실험을 돌리지 않는다"
      ;;
    ga) # 실험 가: 재고→결제 타임아웃 = 넣은 분포(로그정규, 가운데값 80ms, σ 0.6)의 p50·p90·p99·사실상 없음
      for t in 80 173 323 2000; do
        cond ga "ga-t${t}" false-timeout ORDER_TO_INVENTORY_TIMEOUT=5000ms INVENTORY_TO_PAYMENT_TIMEOUT=${t}ms VIRTUAL_THREADS=false
      done
      ;;
    na) # 실험 나: 타임아웃 사실상 없음 / 있음 × 플랫폼 / 가상 스레드
      cond na na-none-platform slow-layer ORDER_TO_INVENTORY_TIMEOUT=60000ms INVENTORY_TO_PAYMENT_TIMEOUT=60000ms VIRTUAL_THREADS=false
      cond na na-split-platform slow-layer ORDER_TO_INVENTORY_TIMEOUT=2000ms INVENTORY_TO_PAYMENT_TIMEOUT=1000ms VIRTUAL_THREADS=false
      cond na na-none-virtual slow-layer ORDER_TO_INVENTORY_TIMEOUT=60000ms INVENTORY_TO_PAYMENT_TIMEOUT=60000ms VIRTUAL_THREADS=true
      cond na na-split-virtual slow-layer ORDER_TO_INVENTORY_TIMEOUT=2000ms INVENTORY_TO_PAYMENT_TIMEOUT=1000ms VIRTUAL_THREADS=true
      ;;
    da) # 실험 다: 아래 구간이 위 층 안 / 위 층보다 길게
      cond da da-inside split-timeout ORDER_TO_INVENTORY_TIMEOUT=2000ms INVENTORY_TO_PAYMENT_TIMEOUT=1000ms VIRTUAL_THREADS=false
      cond da da-longer split-timeout ORDER_TO_INVENTORY_TIMEOUT=1000ms INVENTORY_TO_PAYMENT_TIMEOUT=2000ms VIRTUAL_THREADS=false
      ;;
    *) die "모르는 묶음 '$part'" ;;
  esac
done
((${#failed[@]} == 0)) || { log "실패한 실행이 있는 조건: ${failed[*]} — runs/task-02-index.tsv"; exit 1; }
log "모두 종료 코드 0"
