#!/usr/bin/env bash
# task 03의 조건 하나: 서비스를 그 조건으로 다시 띄우고 → 버리는 실행(r0) → 반복 r1..r<N>. (task02.sh와 같은 꼴, 색인과 받는 변수만 다르다)
#   scripts/task03.sh <실험 이름> <조건 이름> <시나리오> [VAR=값 ...]
#   예: scripts/task03.sh ga ga-max2 retry-mix INVENTORY_RETRY_ENABLED=true INVENTORY_RETRY_MAX_ATTEMPTS=2
# 받는 변수: ORDER_TO_INVENTORY_TIMEOUT, INVENTORY_TO_PAYMENT_TIMEOUT, RETRY_EXPECT, TIMING_EXPECT(기본 설정 확인용), ORDER_RETRY_*, INVENTORY_RETRY_*(docs/retry.md).
#   주지 않은 것은 기본값(재시도 꺼짐, task 01의 타임아웃) — 조건마다 따로 된 프로세스라 앞 조건의 값이 넘어오지 않는다.
# 환경 변수 REPS(기본 3), DISCARD(기본 yes — no면 r0을 돌리지 않는다: 보정).
# 실행마다 runs/task-03-index.tsv에 한 줄(실험, 조건, 시나리오, 반복, 실행 폴더, 종료 코드, 조건 값, 비고). 실패한 실행도 적고 다음 반복으로 간다.
# 종료 코드: 모든 실행이 0이면 0, 아니면 1.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/lib.sh

EXPERIMENT=${1:?"실험 이름"}
CONDITION=${2:?"조건 이름"}
SCENARIO=${3:?"시나리오"}
shift 3
for kv in "$@"; do
  [[ $kv =~ ^(ORDER_TO_INVENTORY_TIMEOUT|INVENTORY_TO_PAYMENT_TIMEOUT|RETRY_EXPECT|TIMING_EXPECT|(ORDER|INVENTORY)_RETRY_(ENABLED|MAX_ATTEMPTS|ON|BACKOFF|BASE_DELAY|MAX_DELAY|BUDGET_ENABLED|BUDGET_CAPACITY|BUDGET_DEPOSIT))=.+$ ]] || die "모르는 조건 '$kv'"
  export "${kv?}"
done
REPS=${REPS:-3}
DISCARD=${DISCARD:-yes}
INDEX=runs/task-03-index.tsv
[[ -f $INDEX ]] || printf 'experiment\tcondition\tscenario\trep\trun\texit\tconditions\tnote\n' > "$INDEX"

log "조건 $CONDITION ($*) — 서비스를 이 조건으로 다시 띄운다"
scripts/up.sh

failed=0
reps=$(seq 1 "$REPS")
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
done
exit "$failed"
