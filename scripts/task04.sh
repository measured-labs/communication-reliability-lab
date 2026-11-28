#!/usr/bin/env bash
# task 04의 조건 하나: 서비스를 그 조건으로 다시 띄우고 → 버리는 실행(r0) → 반복 r1..r<N>. (task03.sh와 같은 꼴, 색인과 받는 변수만 다르다)
#   scripts/task04.sh <실험 이름> <조건 이름> <시나리오> [VAR=값 ...]
#   예: scripts/task04.sh na na-seq-on idem-dup-seq IDEMPOTENCY_ENABLED=true
# 받는 변수: task03.sh의 것 + IDEMPOTENCY_ENABLED(docs/idempotency.md), RECON_EXPECT·IDEM_EXPECT(보정의 기대 파일),
#   (task 04-b) PAYMENT_OVERLOAD_ENABLED·PAYMENT_OVERLOAD_LIMIT·PAYMENT_OVERLOAD_WINDOW(docs/measurement.md 10절), OVERLOAD_EXPECT,
#   (task 05) CHAIN_TRANSPORT·GRPC_DEADLINE_PROPAGATION·GRPC_OBSERVE_CANCELLATION(docs/experiments/task-05-design.md), TRANSPORT_EXPECT,
#   (task 06 — 06-b가 task 02~05의 조건을 모두 이 스크립트로 돌린다) VIRTUAL_THREADS(task 02 실험 나).
#   주지 않은 것은 기본값(재시도·멱등 키 꺼짐, task 01의 타임아웃) — 조건마다 따로 된 프로세스라 앞 조건의 값이 넘어오지 않는다.
# 환경 변수 REPS(기본 3), DISCARD(기본 yes — no면 r0을 돌리지 않는다: 보정), REP_LIST(주면 REPS 대신 이 반복 번호들 — 예 "2 3":
#   조건을 번갈아 돌 때 같은 조건의 반복 번호를 이어 가려고. 다시 띄운 뒤의 r0은 DISCARD대로 앞에 붙는다),
#   UP(기본 yes — no면 다시 띄우지 않고 떠 있는 서비스로 잰다: task 04-b 설계 10절처럼 같은 서비스 조건에서 시나리오만 바꿔 이어 잴 때.
#   떠 있는 서비스의 값이 조건과 다르면 run.sh의 /settings 대조가 실패시킨다),
#   DISCARD_FAIL_STOPS(기본 no — yes면 버리는 실행 r0이 0이 아닐 때 이 블록의 잰 반복을 돌지 않고 종료 1:
#   task 06-b의 실행기 scripts/task06-all.sh가 건다. 노트북의 task0*-all.sh는 걸지 않아 옛 동작 그대로 — r0이 실패해도 잰 반복으로 간다).
# 실행마다 색인(환경 변수 INDEX, 기본 runs/task-04-index.tsv — task 04-b는 runs/task-04b-index.tsv)에 한 줄(실험, 조건, 시나리오, 반복, 실행 폴더, 종료 코드, 조건 값, 비고). 실패한 실행도 적고 다음 반복으로 간다.
# 종료 코드: 모든 실행이 0이면 0, 아니면 1.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/lib.sh

EXPERIMENT=${1:?"실험 이름"}
CONDITION=${2:?"조건 이름"}
SCENARIO=${3:?"시나리오"}
shift 3
for kv in "$@"; do
  [[ $kv =~ ^(ORDER_TO_INVENTORY_TIMEOUT|INVENTORY_TO_PAYMENT_TIMEOUT|VIRTUAL_THREADS|RETRY_EXPECT|TIMING_EXPECT|IDEMPOTENCY_ENABLED|IDEMPOTENCY_IN_PROGRESS_AS_UNKNOWN|RECON_EXPECT|IDEM_EXPECT|PAYMENT_OVERLOAD_(ENABLED|LIMIT|WINDOW)|OVERLOAD_EXPECT|CHAIN_TRANSPORT|GRPC_DEADLINE_PROPAGATION|GRPC_OBSERVE_CANCELLATION|TRANSPORT_EXPECT|(ORDER|INVENTORY)_RETRY_(ENABLED|MAX_ATTEMPTS|ON|BACKOFF|BASE_DELAY|MAX_DELAY|BUDGET_ENABLED|BUDGET_CAPACITY|BUDGET_DEPOSIT))=.+$ ]] || die "모르는 조건 '$kv'"
  export "${kv?}"
done
REPS=${REPS:-3}
DISCARD=${DISCARD:-yes}
DISCARD_FAIL_STOPS=${DISCARD_FAIL_STOPS:-no}
[[ $DISCARD_FAIL_STOPS == yes || $DISCARD_FAIL_STOPS == no ]] || die "DISCARD_FAIL_STOPS '$DISCARD_FAIL_STOPS' 는 yes|no"
INDEX=${INDEX:-runs/task-04-index.tsv}
[[ -f $INDEX ]] || printf 'experiment\tcondition\tscenario\trep\trun\texit\tconditions\tnote\n' > "$INDEX"

log "조건 $CONDITION ($*) — $([[ ${UP:-yes} == no ]] && echo "떠 있는 서비스로 잰다" || echo "서비스를 이 조건으로 다시 띄운다")"
[[ ${UP:-yes} == no ]] || scripts/up.sh

failed=0
reps=${REP_LIST:-$(seq 1 "$REPS")}
[[ $DISCARD == yes ]] && reps="0 $reps"
for rep in $reps; do
  before=$(ls -d runs/"${SCENARIO}"-*-r"${rep}" 2>/dev/null | sort || true)
  set +e
  scripts/run.sh "$SCENARIO" "$rep"
  rc=$?
  set -e
  run=$(comm -13 <(echo "$before") <(ls -d runs/"${SCENARIO}"-*-r"${rep}" 2>/dev/null | sort) | tail -1)
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t\n' "$EXPERIMENT" "$CONDITION" "$SCENARIO" "$rep" "${run:-없음}" "$rc" "$*" >> "$INDEX"
  ((rc == 0)) || { failed=1; log "실패: $CONDITION r$rep 종료 코드 $rc (${run:-폴더 없음})"; }
  if [[ $rep == 0 && $rc != 0 && $DISCARD_FAIL_STOPS == yes ]]; then
    log "버리는 실행이 실패했다 — 이 블록의 잰 반복(${reps#0 })을 돌지 않는다"
    exit 1
  fi
done
exit "$failed"
