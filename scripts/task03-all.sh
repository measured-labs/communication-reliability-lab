#!/usr/bin/env bash
# task 03의 기본 설정 확인, 보정, 실험 가·나·다·라를 설계(docs/experiments/task-03-design.md)의 조건 그대로 차례로 돌린다.
#   scripts/task03-all.sh [base|cal|ga|na|da|ra ...]   (인자가 없으면 모두, 이 차례로)
# 기본 설정 확인이나 보정이 하나라도 실패하면 멈춘다(통과하기 전의 수치는 쓰지 않는다).
# 실험의 실행이 실패하면 runs/task-03-index.tsv에 종료 코드를 남기고 다음 조건으로 간다. 끝에 실패한 조건이 있으면 종료 코드 1.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/lib.sh

parts=("$@")
((${#parts[@]} > 0)) || parts=(base cal ga na da ra)
failed=()
cond() { scripts/task03.sh "$@" || failed+=("$2"); }
cal() { DISCARD=no REPS=1 scripts/task03.sh cal "$@" || die "보정 $1 실패 — 실험을 돌리지 않는다"; }

# 재시도하는 층의 공통 값(실험 가·다·라): 처음 시도를 세는 최대 시도는 조건마다, 간격은 지수 100ms·상한 1000ms, 두 갈래 모두 다시 시도
EXP=(BACKOFF=exponential BASE_DELAY=100ms MAX_DELAY=1000ms ON=failed,unknown)
inv() { local o=(INVENTORY_RETRY_ENABLED=true); for kv in "$@"; do o+=("INVENTORY_RETRY_$kv"); done; echo "${o[@]}"; }
ord() { local o=(ORDER_RETRY_ENABLED=true); for kv in "$@"; do o+=("ORDER_RETRY_$kv"); done; echo "${o[@]}"; }

for part in "${parts[@]}"; do
  case "$part" in
    base) # 기본 설정(재시도 꺼짐)에서 task 01·02의 보정이 원래의 기대 파일 그대로 통과하나
      DISCARD=no REPS=1 scripts/task03.sh base base-calibration calibration || die "기본 설정 확인 calibration 실패"
      DISCARD=no REPS=1 scripts/task03.sh base base-duplicate duplicate || die "기본 설정 확인 duplicate 실패"
      DISCARD=no REPS=1 scripts/task03.sh base base-ft-t300 cal-false-timeout ORDER_TO_INVENTORY_TIMEOUT=5000ms INVENTORY_TO_PAYMENT_TIMEOUT=300ms \
        TIMING_EXPECT=load/expectations/cal-false-timeout-t300.timing.json || die "기본 설정 확인 cal-false-timeout t300 실패"
      DISCARD=no REPS=1 scripts/task03.sh base base-ft-t1000 cal-false-timeout ORDER_TO_INVENTORY_TIMEOUT=5000ms INVENTORY_TO_PAYMENT_TIMEOUT=1000ms \
        TIMING_EXPECT=load/expectations/cal-false-timeout-t1000.timing.json || die "기본 설정 확인 cal-false-timeout t1000 실패"
      DISCARD=no REPS=1 scripts/task03.sh base base-sampler cal-sampler ORDER_TO_INVENTORY_TIMEOUT=2000ms INVENTORY_TO_PAYMENT_TIMEOUT=1000ms \
        || die "기본 설정 확인 cal-sampler 실패"
      ;;
    cal)
      cal c1-max1 cal-retry-first ORDER_TO_INVENTORY_TIMEOUT=5000ms $(inv MAX_ATTEMPTS=1) RETRY_EXPECT=load/expectations/cal-retry-c1-max1.retry.json
      cal c2-fixed200 cal-retry-first ORDER_TO_INVENTORY_TIMEOUT=5000ms $(inv MAX_ATTEMPTS=2 BACKOFF=fixed BASE_DELAY=200ms) RETRY_EXPECT=load/expectations/cal-retry-c2-fixed200.retry.json
      cal c6-outage cal-retry-outage ORDER_TO_INVENTORY_TIMEOUT=5000ms $(inv MAX_ATTEMPTS=2 BACKOFF=fixed BASE_DELAY=200ms) RETRY_EXPECT=load/expectations/cal-retry-c6-outage.retry.json
      cal c7-burst cal-retry-burst ORDER_TO_INVENTORY_TIMEOUT=5000ms $(inv MAX_ATTEMPTS=2 BACKOFF=fixed BASE_DELAY=200ms) RETRY_EXPECT=load/expectations/cal-retry-c7-burst.retry.json
      cal c3-exponential cal-retry-second ORDER_TO_INVENTORY_TIMEOUT=5000ms $(inv MAX_ATTEMPTS=3 BACKOFF=exponential BASE_DELAY=100ms MAX_DELAY=1000ms) RETRY_EXPECT=load/expectations/cal-retry-c3-exponential.retry.json
      cal c4-jitter cal-retry-second ORDER_TO_INVENTORY_TIMEOUT=5000ms $(inv MAX_ATTEMPTS=3 BACKOFF=exponential-jitter BASE_DELAY=100ms MAX_DELAY=1000ms) RETRY_EXPECT=load/expectations/cal-retry-c4-jitter.retry.json
      cal c5-budget0 cal-retry-first ORDER_TO_INVENTORY_TIMEOUT=5000ms $(inv MAX_ATTEMPTS=2 BUDGET_ENABLED=true BUDGET_CAPACITY=0 BUDGET_DEPOSIT=0) RETRY_EXPECT=load/expectations/cal-retry-c5-budget0.retry.json
      ;;
    ga) # 실험 가: 재고→결제의 최대 시도 1·2·3·5
      for m in 1 2 3 5; do
        cond ga "ga-max$m" retry-mix ORDER_TO_INVENTORY_TIMEOUT=5000ms $(inv MAX_ATTEMPTS=$m "${EXP[@]}")
      done
      ;;
    na) # 실험 나: 간격 넷, 무리 지은 도착 + 결제 구간 장애 [10s, 20s), 재고 최대 시도 4
      cond na na-none retry-outage-burst ORDER_TO_INVENTORY_TIMEOUT=5000ms $(inv MAX_ATTEMPTS=4 BACKOFF=none)
      cond na na-fixed retry-outage-burst ORDER_TO_INVENTORY_TIMEOUT=5000ms $(inv MAX_ATTEMPTS=4 BACKOFF=fixed BASE_DELAY=200ms)
      cond na na-exponential retry-outage-burst ORDER_TO_INVENTORY_TIMEOUT=5000ms $(inv MAX_ATTEMPTS=4 BACKOFF=exponential BASE_DELAY=100ms MAX_DELAY=1000ms)
      cond na na-jitter retry-outage-burst ORDER_TO_INVENTORY_TIMEOUT=5000ms $(inv MAX_ATTEMPTS=4 BACKOFF=exponential-jitter BASE_DELAY=100ms MAX_DELAY=1000ms)
      ;;
    da) # 실험 다: 재시도하는 층 × 주문→재고 타임아웃
      cond da da-1L retry-mix ORDER_TO_INVENTORY_TIMEOUT=3000ms $(inv MAX_ATTEMPTS=3 "${EXP[@]}")
      cond da da-1S retry-mix ORDER_TO_INVENTORY_TIMEOUT=250ms $(inv MAX_ATTEMPTS=3 "${EXP[@]}")
      cond da da-2 retry-mix ORDER_TO_INVENTORY_TIMEOUT=3000ms $(ord MAX_ATTEMPTS=3 "${EXP[@]}")
      cond da da-3L retry-mix ORDER_TO_INVENTORY_TIMEOUT=3000ms $(inv MAX_ATTEMPTS=3 "${EXP[@]}") $(ord MAX_ATTEMPTS=3 "${EXP[@]}")
      cond da da-3S retry-mix ORDER_TO_INVENTORY_TIMEOUT=250ms $(inv MAX_ATTEMPTS=3 "${EXP[@]}") $(ord MAX_ATTEMPTS=3 "${EXP[@]}")
      ;;
    ra) # 실험 라: 예산 꺼짐 / 켬(용량 10, 호출마다 0.1), 결제 구간 장애 [10s, 30s), 재고 최대 시도 4
      cond ra ra-off retry-budget ORDER_TO_INVENTORY_TIMEOUT=5000ms $(inv MAX_ATTEMPTS=4 "${EXP[@]}")
      cond ra ra-on retry-budget ORDER_TO_INVENTORY_TIMEOUT=5000ms $(inv MAX_ATTEMPTS=4 "${EXP[@]}" BUDGET_ENABLED=true BUDGET_CAPACITY=10 BUDGET_DEPOSIT=0.1)
      ;;
    *) die "모르는 묶음 '$part'" ;;
  esac
done
((${#failed[@]} == 0)) || { log "실패한 실행이 있는 조건: ${failed[*]} — runs/task-03-index.tsv"; exit 1; }
log "모두 종료 코드 0"
