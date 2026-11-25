#!/usr/bin/env bash
# task 05의 보정과 실험을 설계(docs/experiments/task-05-design.md)의 조건과 차례 그대로 돌린다.
#   scripts/task05-all.sh [cal|exp ...]   (인자가 없으면 cal exp)
# 조건 하나 = scripts/task04.sh(INDEX=runs/task-05-index.tsv): 서비스를 그 조건으로 다시 띄우고 → 버리는 실행(r0) → REP_LIST의 반복.
# 실험(exp)은 조건 넷(H·G1·G2·G3)을 바퀴마다 번갈아 돈다(설계 4절 — 바퀴마다 차례를 바꾸고, 바퀴 안에서 P1의 넷 뒤에 P2의 넷).
# 조건마다 시작 앞에 `date`와 `uptime`을 runs/task-05-uptime.txt에 남긴다(이 컴퓨터의 다른 부하 — 실행마다의 값은 config.env의 host_loadavg_*).
# 보정(cal)이 하나라도 실패하면 멈춘다(통과하기 전의 수치는 쓰지 않는다).
# 실험의 실행이 실패하면 색인에 종료 코드를 남기고 다음 조건으로 간다. 끝에 실패한 조건이 있으면 종료 코드 1.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/lib.sh

parts=("$@")
((${#parts[@]} > 0)) || parts=(cal exp)
export INDEX=runs/task-05-index.tsv
UPTIME_LOG=runs/task-05-uptime.txt
failed=()
stamp() { printf '%s\t%s\t%s\n' "$(date -Iseconds)" "$1" "$(uptime)" >> "$UPTIME_LOG"; }
cond() { stamp "$2 ${REP_LIST:-}"; scripts/task04.sh "$@" || failed+=("$2"); }
once() { stamp "$2"; DISCARD=no REPS=1 scripts/task04.sh "$@" || die "$1 $2 실패 — 실험을 돌리지 않는다"; }
X=load/expectations

# 조건(설계 3절)
H=(CHAIN_TRANSPORT=http)
G1=(CHAIN_TRANSPORT=grpc GRPC_DEADLINE_PROPAGATION=false GRPC_OBSERVE_CANCELLATION=false)
G2=(CHAIN_TRANSPORT=grpc GRPC_DEADLINE_PROPAGATION=true GRPC_OBSERVE_CANCELLATION=false)
G3=(CHAIN_TRANSPORT=grpc GRPC_DEADLINE_PROPAGATION=true GRPC_OBSERVE_CANCELLATION=true)
# 배치(설계 4절 — task 02 실험 다의 둘)
P1=(ORDER_TO_INVENTORY_TIMEOUT=2000ms INVENTORY_TO_PAYMENT_TIMEOUT=1000ms)
P2=(ORDER_TO_INVENTORY_TIMEOUT=1000ms INVENTORY_TO_PAYMENT_TIMEOUT=2000ms)
FT300=(ORDER_TO_INVENTORY_TIMEOUT=5000ms INVENTORY_TO_PAYMENT_TIMEOUT=300ms)
FT1000=(ORDER_TO_INVENTORY_TIMEOUT=5000ms INVENTORY_TO_PAYMENT_TIMEOUT=1000ms)
REACH=TIMING_EXPECT=$X/task05-reach.timing.json # 실험의 부하가 모두 서버에 닿았나(서버에 닿지 못한 요청·응답 없음 0)

conds() { # <조건 이름> → 그 조건의 값
  case "$1" in H) echo "${H[@]}" ;; G1) echo "${G1[@]}" ;; G2) echo "${G2[@]}" ;; G3) echo "${G3[@]}" ;; *) die "모르는 조건 '$1'" ;; esac
}

for part in "${parts[@]}"; do
  case "$part" in
    cal) # 설계 6절: HTTP 기본이 그대로인지 → gRPC(G1)에서 같은 기대 건수 → G2·G3의 답을 아는 실행
      once cal cal-h-calibration calibration "${H[@]}"
      once cal cal-h-ft300 cal-false-timeout "${H[@]}" "${FT300[@]}" TIMING_EXPECT=$X/cal-false-timeout-t300.timing.json
      once cal cal-h-ft1000 cal-false-timeout "${H[@]}" "${FT1000[@]}" TIMING_EXPECT=$X/cal-false-timeout-t1000.timing.json
      once cal cal-g1-calibration calibration "${G1[@]}"
      once cal cal-g1-ft300 cal-false-timeout "${G1[@]}" "${FT300[@]}" TIMING_EXPECT=$X/cal-false-timeout-t300.timing.json
      once cal cal-g1-ft1000 cal-false-timeout "${G1[@]}" "${FT1000[@]}" TIMING_EXPECT=$X/cal-false-timeout-t1000.timing.json
      once cal cal-g2-calibration calibration "${G2[@]}" RECON_EXPECT=$X/calibration-grpc-g2.json
      once cal cal-g3-calibration calibration "${G3[@]}" RECON_EXPECT=$X/calibration-grpc-g3.json
      once cal cal-g3-ft300 cal-false-timeout "${G3[@]}" "${FT300[@]}" RECON_EXPECT=$X/cal-false-timeout-grpc-g3.json TIMING_EXPECT=$X/cal-false-timeout-grpc-g3.timing.json
      ;;
    exp) # 설계 4절: 바퀴 셋 × (P1의 넷 → P2의 넷), 조건마다 다시 띄우고 r0을 버린다
      rounds=("H G1 G2 G3" "G3 G2 G1 H" "G1 G3 H G2")
      for r in 1 2 3; do
        for p in P1 P2; do
          for c in ${rounds[$((r - 1))]}; do
            if [[ $p == P1 ]]; then pv=("${P1[@]}"); else pv=("${P2[@]}"); fi
            # shellcheck disable=SC2046
            REP_LIST="$r" cond "exp-$p" "$p-$c" split-timeout $(conds "$c") "${pv[@]}" "$REACH"
          done
        done
      done
      ;;
    *) die "모르는 묶음 '$part'" ;;
  esac
done
stamp "끝"
((${#failed[@]} == 0)) || { log "실패한 실행이 있는 조건: ${failed[*]} — $INDEX"; exit 1; }
log "모두 종료 코드 0"
