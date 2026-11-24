#!/usr/bin/env bash
# task 04의 꺼진 상태 확인, 보정, 재기동 확인, 실험 가·나·다·라를 설계(docs/experiments/task-04-design.md)의 조건 그대로 차례로 돌린다.
#   scripts/task04-all.sh [base|cal|restart|ga|na|da|ra ...]   (인자가 없으면 모두, 이 차례로)
# 꺼진 상태 확인·보정·재기동 확인이 하나라도 실패하면 멈춘다(통과하기 전의 수치는 쓰지 않는다).
# 실험의 실행이 실패하면 runs/task-04-index.tsv에 종료 코드를 남기고 다음 조건으로 간다. 끝에 실패한 조건이 있으면 종료 코드 1.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/lib.sh

parts=("$@")
((${#parts[@]} > 0)) || parts=(base cal restart ga na da ra)
failed=()
cond() { scripts/task04.sh "$@" || failed+=("$2"); }
once() { DISCARD=no REPS=1 scripts/task04.sh "$@" || die "$1 $2 실패 — 실험을 돌리지 않는다"; }
X=load/expectations
ON=IDEMPOTENCY_ENABLED=true

# 재시도하는 층의 공통 값(실험 가 — task 03 실험 다와 같다): 최대 3, 지수 100ms·상한 1000ms, 두 갈래 모두 다시 시도
EXP=(MAX_ATTEMPTS=3 BACKOFF=exponential BASE_DELAY=100ms MAX_DELAY=1000ms ON=failed,unknown)
inv() { local o=(INVENTORY_RETRY_ENABLED=true); for kv in "${EXP[@]}"; do o+=("INVENTORY_RETRY_$kv"); done; echo "${o[@]}"; }
ord() { local o=(ORDER_RETRY_ENABLED=true); for kv in "${EXP[@]}"; do o+=("ORDER_RETRY_$kv"); done; echo "${o[@]}"; }

for part in "${parts[@]}"; do
  case "$part" in
    base) # 꺼진 상태(멱등 키 기본값)에서 task 01·02·03의 보정이 원래의 기대 파일 그대로 통과하나
      once base base-calibration calibration
      once base base-duplicate duplicate
      once base base-ft-t300 cal-false-timeout ORDER_TO_INVENTORY_TIMEOUT=5000ms INVENTORY_TO_PAYMENT_TIMEOUT=300ms TIMING_EXPECT=$X/cal-false-timeout-t300.timing.json
      once base base-ft-t1000 cal-false-timeout ORDER_TO_INVENTORY_TIMEOUT=5000ms INVENTORY_TO_PAYMENT_TIMEOUT=1000ms TIMING_EXPECT=$X/cal-false-timeout-t1000.timing.json
      once base base-sampler cal-sampler ORDER_TO_INVENTORY_TIMEOUT=2000ms INVENTORY_TO_PAYMENT_TIMEOUT=1000ms
      once base base-c1-max1 cal-retry-first ORDER_TO_INVENTORY_TIMEOUT=5000ms INVENTORY_RETRY_ENABLED=true INVENTORY_RETRY_MAX_ATTEMPTS=1 RETRY_EXPECT=$X/cal-retry-c1-max1.retry.json
      once base base-c2-fixed200 cal-retry-first ORDER_TO_INVENTORY_TIMEOUT=5000ms INVENTORY_RETRY_ENABLED=true INVENTORY_RETRY_MAX_ATTEMPTS=2 INVENTORY_RETRY_BACKOFF=fixed INVENTORY_RETRY_BASE_DELAY=200ms RETRY_EXPECT=$X/cal-retry-c2-fixed200.retry.json
      once base base-c6-outage cal-retry-outage ORDER_TO_INVENTORY_TIMEOUT=5000ms INVENTORY_RETRY_ENABLED=true INVENTORY_RETRY_MAX_ATTEMPTS=2 INVENTORY_RETRY_BACKOFF=fixed INVENTORY_RETRY_BASE_DELAY=200ms RETRY_EXPECT=$X/cal-retry-c6-outage.retry.json
      once base base-c7-burst cal-retry-burst ORDER_TO_INVENTORY_TIMEOUT=5000ms INVENTORY_RETRY_ENABLED=true INVENTORY_RETRY_MAX_ATTEMPTS=2 INVENTORY_RETRY_BACKOFF=fixed INVENTORY_RETRY_BASE_DELAY=200ms RETRY_EXPECT=$X/cal-retry-c7-burst.retry.json
      once base base-c3-exponential cal-retry-second ORDER_TO_INVENTORY_TIMEOUT=5000ms INVENTORY_RETRY_ENABLED=true INVENTORY_RETRY_MAX_ATTEMPTS=3 INVENTORY_RETRY_BACKOFF=exponential INVENTORY_RETRY_BASE_DELAY=100ms INVENTORY_RETRY_MAX_DELAY=1000ms RETRY_EXPECT=$X/cal-retry-c3-exponential.retry.json
      once base base-c4-jitter cal-retry-second ORDER_TO_INVENTORY_TIMEOUT=5000ms INVENTORY_RETRY_ENABLED=true INVENTORY_RETRY_MAX_ATTEMPTS=3 INVENTORY_RETRY_BACKOFF=exponential-jitter INVENTORY_RETRY_BASE_DELAY=100ms INVENTORY_RETRY_MAX_DELAY=1000ms RETRY_EXPECT=$X/cal-retry-c4-jitter.retry.json
      once base base-c5-budget0 cal-retry-first ORDER_TO_INVENTORY_TIMEOUT=5000ms INVENTORY_RETRY_ENABLED=true INVENTORY_RETRY_MAX_ATTEMPTS=2 INVENTORY_RETRY_BUDGET_ENABLED=true INVENTORY_RETRY_BUDGET_CAPACITY=0 INVENTORY_RETRY_BUDGET_DEPOSIT=0 RETRY_EXPECT=$X/cal-retry-c5-budget0.retry.json
      ;;
    cal) # 새 장애와 새 지표의 보정(설계 3절): 키 끔 / 켬
      once cal cal-idem-off cal-idem RECON_EXPECT=$X/cal-idem-off.json IDEM_EXPECT=$X/cal-idem-off.idem.json
      once cal cal-idem-on cal-idem $ON RECON_EXPECT=$X/cal-idem-on.json IDEM_EXPECT=$X/cal-idem-on.idem.json
      ;;
    restart) # 키가 서비스의 재기동을 넘어 남는지(확인 한 번)
      scripts/task04-restart-check.sh || die "재기동 확인 실패"
      ;;
    ga) # 실험 가: task 03에서 두 번 처리됨이 나온 두 배치 × 키 끔/켬
      cond ga ga-1-off idem-retry-mix ORDER_TO_INVENTORY_TIMEOUT=3000ms $(inv)
      cond ga ga-1-on idem-retry-mix ORDER_TO_INVENTORY_TIMEOUT=3000ms $(inv) $ON
      cond ga ga-2-off idem-retry-mix ORDER_TO_INVENTORY_TIMEOUT=250ms $(inv) $(ord)
      cond ga ga-2-on idem-retry-mix ORDER_TO_INVENTORY_TIMEOUT=250ms $(inv) $(ord) $ON
      ;;
    na) # 실험 나: 클라이언트의 중복 요청(잇달아 / 동시에) × 키 끔/켬
      cond na na-seq-off idem-dup-seq
      cond na na-seq-on idem-dup-seq $ON
      cond na na-conc-off idem-dup-conc
      cond na na-conc-on idem-dup-conc $ON
      ;;
    da) # 실험 다: 키 저장의 비용
      cond da da-off idem-plain
      cond da da-on idem-plain $ON
      ;;
    ra) # 실험 라: 결과 모름을 같은 키로 나중에 맞추기
      cond ra ra-off idem-reconcile
      cond ra ra-on idem-reconcile $ON
      ;;
    *) die "모르는 묶음 '$part'" ;;
  esac
done
((${#failed[@]} == 0)) || { log "실패한 실행이 있는 조건: ${failed[*]} — runs/task-04-index.tsv"; exit 1; }
log "모두 종료 코드 0"
