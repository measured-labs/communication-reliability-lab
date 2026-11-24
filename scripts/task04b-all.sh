#!/usr/bin/env bash
# task 04-b의 꺼진 상태 확인, 보정, 실험 가·나를 설계(docs/experiments/task-04b-design.md)의 조건과 차례 그대로 돌린다.
#   scripts/task04b-all.sh [base|cal|ga|na ...]   (인자가 없으면 base cal ga — na는 실험 가의 결과로 조건을 고른 뒤 NA_BACKOFF를 주고 돌린다)
#   NA_BACKOFF=none|fixed|exponential|exponential-jitter scripts/task04b-all.sh na
# 조건 하나 = scripts/task04.sh(INDEX=runs/task-04b-index.tsv): 서비스를 그 조건으로 다시 띄우고 → 버리는 실행(r0) → REP_LIST의 반복.
# 실험 가·나는 반복을 조건 사이에 번갈아 돈다(설계 7절 — 둘째 바퀴는 거꾸로, 같은 조건이 이어지는 곳은 다시 띄우지 않고 이어 잰다).
# 조건마다 시작 앞에 `date`와 `uptime`을 runs/task-04b-uptime.txt에 남긴다(이 컴퓨터의 다른 부하 — 실행마다의 값은 config.env의 host_loadavg_*).
# 꺼진 상태 확인·보정이 하나라도 실패하면 멈춘다(통과하기 전의 수치는 쓰지 않는다).
# 실험의 실행이 실패하면 색인에 종료 코드를 남기고 다음 조건으로 간다. 끝에 실패한 조건이 있으면 종료 코드 1.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/lib.sh

parts=("$@")
((${#parts[@]} > 0)) || parts=(base cal ga)
export INDEX=runs/task-04b-index.tsv
UPTIME_LOG=runs/task-04b-uptime.txt
failed=()
stamp() { printf '%s\t%s\t%s\n' "$(date -Iseconds)" "$1" "$(uptime)" >> "$UPTIME_LOG"; }
cond() { stamp "$2 ${REP_LIST:-}"; scripts/task04.sh "$@" || failed+=("$2"); }
once() { stamp "$2"; DISCARD=no REPS=1 scripts/task04.sh "$@" || die "$1 $2 실패 — 실험을 돌리지 않는다"; }
X=load/expectations

# 모든 실험 조건에 같은 것(설계 0·2절): 결제의 한도 50·창 1s, 주문→재고 5000ms, 재고→결제 1000ms(기본값)
LIMIT=(PAYMENT_OVERLOAD_ENABLED=true PAYMENT_OVERLOAD_LIMIT=50 PAYMENT_OVERLOAD_WINDOW=1000ms ORDER_TO_INVENTORY_TIMEOUT=5000ms)
# 재고→결제의 재시도: 최대 3, 두 갈래 모두 다시 시도(설계 5절). 간격의 값은 task 03과 같다
inv() { # <backoff> [예산 켬: budget]
  local o=(INVENTORY_RETRY_ENABLED=true INVENTORY_RETRY_MAX_ATTEMPTS=3 INVENTORY_RETRY_ON=failed,unknown "INVENTORY_RETRY_BACKOFF=$1")
  case "$1" in
    none) ;;
    fixed) o+=(INVENTORY_RETRY_BASE_DELAY=200ms) ;;
    exponential|exponential-jitter) o+=(INVENTORY_RETRY_BASE_DELAY=100ms INVENTORY_RETRY_MAX_DELAY=1000ms) ;;
    *) die "모르는 간격 '$1'" ;;
  esac
  [[ ${2:-} == budget ]] && o+=(INVENTORY_RETRY_BUDGET_ENABLED=true INVENTORY_RETRY_BUDGET_CAPACITY=10 INVENTORY_RETRY_BUDGET_DEPOSIT=0.1)
  echo "${o[@]}"
}
ga() { # <조건> <반복 번호들>
  local c=$1 reps=$2 r=()
  case "$c" in
    off) ;;
    none) read -ra r <<< "$(inv none)" ;;
    fixed) read -ra r <<< "$(inv fixed)" ;;
    exp) read -ra r <<< "$(inv exponential)" ;;
    jitter) read -ra r <<< "$(inv exponential-jitter)" ;;
    *) die "모르는 조건 '$c'" ;;
  esac
  REP_LIST="$reps" cond ga "ga-$c" overload-spike "${LIMIT[@]}" "${r[@]}"
}
na() { # <off|on> <반복 번호들>
  local r=()
  read -ra r <<< "$(inv "$NA_BACKOFF" "$([[ $1 == on ]] && echo budget)")"
  REP_LIST="$2" cond na "na-$1" overload-spike "${LIMIT[@]}" "${r[@]}"
}

for part in "${parts[@]}"; do
  case "$part" in
    base) # 한도를 켜지 않은 채(기본값) task 01~04의 보정이 원래의 기대 파일 그대로 통과하나
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
      once base base-cal-idem-off cal-idem RECON_EXPECT=$X/cal-idem-off.json IDEM_EXPECT=$X/cal-idem-off.idem.json
      once base base-cal-idem-on cal-idem IDEMPOTENCY_ENABLED=true RECON_EXPECT=$X/cal-idem-on.json IDEM_EXPECT=$X/cal-idem-on.idem.json
      ;;
    cal) # 새 장애의 보정(설계 4절): 재시도 꺼짐, 기대는 load/expectations/cal-overload.{json,overload.json}
      once cal cal-overload cal-overload "${LIMIT[@]}"
      ;;
    ga) # 실험 가(설계 5·7절): off r1 · none r1 · fixed r1 · exp r1 · jitter r1·r2 · exp r2 · fixed r2 · none r2 · off r2·r3 · none r3 · fixed r3 · exp r3 · jitter r3
      ga off 1; ga none 1; ga fixed 1; ga exp 1; ga jitter "1 2"
      ga exp 2; ga fixed 2; ga none 2; ga off "2 3"
      ga none 3; ga fixed 3; ga exp 3; ga jitter 3
      ;;
    na) # 실험 나(설계 6·7절): on r1 · off r1·r2 · on r2·r3 · off r3 — 간격은 실험 가에서 고른 것(NA_BACKOFF)
      [[ -n ${NA_BACKOFF:-} ]] || die "NA_BACKOFF(실험 가에서 고른 간격)를 준다"
      na on 1; na off "1 2"; na on "2 3"; na off 3
      ;;
    *) die "모르는 묶음 '$part'" ;;
  esac
done
stamp "끝"
((${#failed[@]} == 0)) || { log "실패한 실행이 있는 조건: ${failed[*]} — $INDEX"; exit 1; }
log "모두 종료 코드 0"
