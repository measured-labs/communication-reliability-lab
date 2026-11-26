#!/usr/bin/env bash
# task 06-a: task 01~05의 보정 전부를 서버 배치에서 원래의 기대 파일 그대로 돌린다(docs/experiments/task-06-design.md 3절).
# 부하 서버에서 돌린다 — 서비스 서버는 SERVICES_SSH, k6는 K6(scripts/lib.sh·run.sh 머리, scripts/servers-sync.sh가 둔 것):
#   SERVICES_SSH=<계정>@<서비스 서버> K6=.tools/k6-v2.4.0/k6 scripts/task06-all.sh [cal|restart ...]   (인자가 없으면 cal restart)
#   cal: 맨 앞의 calibration은 버리는 실행(r0 — 서비스를 새로 띄운 직후) 뒤 세 번(r1~r3 — 편차), 나머지는 한 번씩.
#        조건과 기대 파일은 앞 단계의 것 그대로: scripts/task04b-all.sh의 base·cal, scripts/task05-all.sh의 cal·cal2.
#        task05-all.sh의 cal-h-*(HTTP 기본)는 base-calibration·base-ft-t300·base-ft-t1000과 조건이 같아 다시 돌리지 않는다.
#   restart: 멱등 키가 서비스의 재기동을 넘어 남는지(scripts/task04-restart-check.sh — 실행 기록은 runs/restart-check-*).
# 조건 하나 = scripts/task04.sh(INDEX=runs/task-06-index.tsv): 서비스를 그 조건으로 다시 띄우고 → (버리는 실행) → 반복.
# 보정 하나가 실패해도 다음 보정으로 간다(서버에서 무엇이 통과하지 않는지 모두 보려고). 끝에 실패가 있으면 종료 코드 1 —
# 그때는 실험(06-b)으로 넘어가지 않는다.
# 조건마다 시작 앞에 두 서버의 `uptime`을 runs/task-06-uptime.txt에 남긴다(실행마다의 값은 config.env의 host_loadavg_*·services_loadavg_*).
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/lib.sh

[[ -n $SERVICES_SSH ]] || die "SERVICES_SSH(서비스 서버)를 준다 — 노트북의 보정은 scripts/task04b-all.sh base 등으로 돌린다"
parts=("$@")
((${#parts[@]} > 0)) || parts=(cal restart)
export INDEX=runs/task-06-index.tsv
UPTIME_LOG=runs/task-06-uptime.txt
failed=()
stamp() { printf '%s\t%s\t%s\t%s\n' "$(date -Iseconds)" "$1" "load: $(uptime)" "services: $(on_services uptime)" >> "$UPTIME_LOG"; }
once() { stamp "$2"; DISCARD=${DISCARD:-no} REPS=${REPS:-1} scripts/task04.sh "$@" || failed+=("$2"); }
X=load/expectations

# 조건(task 05 설계 3·10절)
G1=(CHAIN_TRANSPORT=grpc GRPC_DEADLINE_PROPAGATION=false GRPC_OBSERVE_CANCELLATION=false)
G2=(CHAIN_TRANSPORT=grpc GRPC_DEADLINE_PROPAGATION=true GRPC_OBSERVE_CANCELLATION=false)
G3=(CHAIN_TRANSPORT=grpc GRPC_DEADLINE_PROPAGATION=true GRPC_OBSERVE_CANCELLATION=true)
G1o=(CHAIN_TRANSPORT=grpc GRPC_DEADLINE_PROPAGATION=false GRPC_OBSERVE_CANCELLATION=true)
FT300=(ORDER_TO_INVENTORY_TIMEOUT=5000ms INVENTORY_TO_PAYMENT_TIMEOUT=300ms)
FT1000=(ORDER_TO_INVENTORY_TIMEOUT=5000ms INVENTORY_TO_PAYMENT_TIMEOUT=1000ms)
LIMIT=(PAYMENT_OVERLOAD_ENABLED=true PAYMENT_OVERLOAD_LIMIT=50 PAYMENT_OVERLOAD_WINDOW=1000ms ORDER_TO_INVENTORY_TIMEOUT=5000ms)

for part in "${parts[@]}"; do
  case "$part" in
    cal)
      # task 01~04 (scripts/task04b-all.sh base와 같은 줄)
      DISCARD=yes REPS=3 once cal base-calibration calibration
      once cal base-duplicate duplicate
      once cal base-ft-t300 cal-false-timeout "${FT300[@]}" TIMING_EXPECT=$X/cal-false-timeout-t300.timing.json
      once cal base-ft-t1000 cal-false-timeout "${FT1000[@]}" TIMING_EXPECT=$X/cal-false-timeout-t1000.timing.json
      once cal base-sampler cal-sampler ORDER_TO_INVENTORY_TIMEOUT=2000ms INVENTORY_TO_PAYMENT_TIMEOUT=1000ms
      once cal base-c1-max1 cal-retry-first ORDER_TO_INVENTORY_TIMEOUT=5000ms INVENTORY_RETRY_ENABLED=true INVENTORY_RETRY_MAX_ATTEMPTS=1 RETRY_EXPECT=$X/cal-retry-c1-max1.retry.json
      once cal base-c2-fixed200 cal-retry-first ORDER_TO_INVENTORY_TIMEOUT=5000ms INVENTORY_RETRY_ENABLED=true INVENTORY_RETRY_MAX_ATTEMPTS=2 INVENTORY_RETRY_BACKOFF=fixed INVENTORY_RETRY_BASE_DELAY=200ms RETRY_EXPECT=$X/cal-retry-c2-fixed200.retry.json
      once cal base-c6-outage cal-retry-outage ORDER_TO_INVENTORY_TIMEOUT=5000ms INVENTORY_RETRY_ENABLED=true INVENTORY_RETRY_MAX_ATTEMPTS=2 INVENTORY_RETRY_BACKOFF=fixed INVENTORY_RETRY_BASE_DELAY=200ms RETRY_EXPECT=$X/cal-retry-c6-outage.retry.json
      once cal base-c7-burst cal-retry-burst ORDER_TO_INVENTORY_TIMEOUT=5000ms INVENTORY_RETRY_ENABLED=true INVENTORY_RETRY_MAX_ATTEMPTS=2 INVENTORY_RETRY_BACKOFF=fixed INVENTORY_RETRY_BASE_DELAY=200ms RETRY_EXPECT=$X/cal-retry-c7-burst.retry.json
      once cal base-c3-exponential cal-retry-second ORDER_TO_INVENTORY_TIMEOUT=5000ms INVENTORY_RETRY_ENABLED=true INVENTORY_RETRY_MAX_ATTEMPTS=3 INVENTORY_RETRY_BACKOFF=exponential INVENTORY_RETRY_BASE_DELAY=100ms INVENTORY_RETRY_MAX_DELAY=1000ms RETRY_EXPECT=$X/cal-retry-c3-exponential.retry.json
      once cal base-c4-jitter cal-retry-second ORDER_TO_INVENTORY_TIMEOUT=5000ms INVENTORY_RETRY_ENABLED=true INVENTORY_RETRY_MAX_ATTEMPTS=3 INVENTORY_RETRY_BACKOFF=exponential-jitter INVENTORY_RETRY_BASE_DELAY=100ms INVENTORY_RETRY_MAX_DELAY=1000ms RETRY_EXPECT=$X/cal-retry-c4-jitter.retry.json
      once cal base-c5-budget0 cal-retry-first ORDER_TO_INVENTORY_TIMEOUT=5000ms INVENTORY_RETRY_ENABLED=true INVENTORY_RETRY_MAX_ATTEMPTS=2 INVENTORY_RETRY_BUDGET_ENABLED=true INVENTORY_RETRY_BUDGET_CAPACITY=0 INVENTORY_RETRY_BUDGET_DEPOSIT=0 RETRY_EXPECT=$X/cal-retry-c5-budget0.retry.json
      once cal base-cal-idem-off cal-idem RECON_EXPECT=$X/cal-idem-off.json IDEM_EXPECT=$X/cal-idem-off.idem.json
      once cal base-cal-idem-on cal-idem IDEMPOTENCY_ENABLED=true RECON_EXPECT=$X/cal-idem-on.json IDEM_EXPECT=$X/cal-idem-on.idem.json
      # task 04-b (scripts/task04b-all.sh cal)
      once cal cal-overload cal-overload "${LIMIT[@]}"
      # task 05 (scripts/task05-all.sh cal·cal2 — cal-h-*는 위와 같은 조건이라 뺀다)
      once cal cal-g1-calibration calibration "${G1[@]}"
      once cal cal-g1-ft300 cal-false-timeout "${G1[@]}" "${FT300[@]}" TIMING_EXPECT=$X/cal-false-timeout-t300.timing.json
      once cal cal-g1-ft1000 cal-false-timeout "${G1[@]}" "${FT1000[@]}" TIMING_EXPECT=$X/cal-false-timeout-t1000.timing.json
      once cal cal-g2-calibration calibration "${G2[@]}" RECON_EXPECT=$X/calibration-grpc-g2.json
      once cal cal-g3-calibration calibration "${G3[@]}" RECON_EXPECT=$X/calibration-grpc-g3.json
      once cal cal-g3-ft300 cal-false-timeout "${G3[@]}" "${FT300[@]}" RECON_EXPECT=$X/cal-false-timeout-grpc-g3.json TIMING_EXPECT=$X/cal-false-timeout-grpc-g3.timing.json
      once cal cal-g1o-calibration calibration "${G1o[@]}" RECON_EXPECT=$X/calibration-grpc-g3.json
      once cal cal-g1o-ft300 cal-false-timeout "${G1o[@]}" "${FT300[@]}" RECON_EXPECT=$X/cal-false-timeout-grpc-g3.json TIMING_EXPECT=$X/cal-false-timeout-grpc-g3.timing.json
      ;;
    restart)
      stamp restart-check
      scripts/task04-restart-check.sh || failed+=(restart-check)
      ;;
    *) die "모르는 묶음 '$part'" ;;
  esac
done
stamp "끝"
((${#failed[@]} == 0)) || { log "실패한 보정: ${failed[*]} — $INDEX"; exit 1; }
log "모두 종료 코드 0"
