#!/usr/bin/env bash
# 실행 한 번: 확인 → 이미지 확인 → 워밍업 → 상태 초기화 → 부하 → 가라앉기 → DB 상태 내보내기 → 분석.
#   scripts/run.sh <calibration|duplicate> <반복 번호>
# 기록: runs/<시나리오>-<시각>-r<번호>/ (형식은 docs/measurement.md)
# 서비스는 먼저 scripts/up.sh 로 띄워 둔다. 종료 코드: 0 통과, 1 실행 실패, 2 분석 결과가 기대와 다름.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/lib.sh

SCENARIO=${1:?"시나리오(calibration|duplicate)"}
REP=${2:?"반복 번호"}

# 시나리오마다의 부하 모양. SETTLE_TIMEOUT = k6가 끝난 뒤 세 서비스의 처리 중인 요청이 0이 되기를 기다리는 최대 시간.
# 시나리오의 가장 긴 지연(calibration: order:delay=6000)보다 길게 둔다. 그 안에 0이 안 되면 실행 실패.
# 환경 변수 SETTLE_TIMEOUT_SECONDS로 바꿀 수 있다(기록에 남는다 — 가라앉기 검사가 실패를 내는지 일부러 짧게 해 볼 때).
case "$SCENARIO" in
  calibration) RATE=20; DURATION_SECONDS=30; DEFAULT_SETTLE_TIMEOUT=20 ;;
  duplicate)   RATE=20; DURATION_SECONDS=15; DEFAULT_SETTLE_TIMEOUT=10 ;;
  *) die "모르는 시나리오 '$SCENARIO'" ;;
esac
SETTLE_TIMEOUT_SECONDS=${SETTLE_TIMEOUT_SECONDS:-$DEFAULT_SETTLE_TIMEOUT}
REQUEST_TIMEOUT=5s # k6 기본 60s를 쓰지 않는다. 층의 타임아웃(2000ms·1000ms)보다 길다
WARMUP_RATE=20
WARMUP_SECONDS=15
PLANNED=$((RATE * DURATION_SECONDS))
BASE_URL=http://localhost:8081

NAME="${SCENARIO}-$(date +%Y%m%dT%H%M%S)-r${REP}"
OUT="runs/${NAME}"
mkdir -p "$OUT/db"
log "실행 $NAME"

# 1. 확인: 네 컨테이너가 떠 있고 세 서비스가 UP
require_running
wait_ready

# 1-1. 이미지가 지금의 코드로 지어졌나: 짓고(바뀐 것이 없으면 층 캐시로 금방 끝난다),
#      떠 있는 세 서비스가 방금 지은 이미지로 떠 있는지 본다. 분석기는 이 실행의 끝에 방금 지은 이미지로 돈다.
log "이미지 짓기(분석기 포함) — 바뀐 것이 없으면 캐시"
"${COMPOSE[@]}" --profile tools build > "$OUT/image-build.txt" 2>&1 || die "이미지 짓기가 실패했다 — $OUT/image-build.txt"
for svc in order inventory payment; do
  running_image=$(docker inspect --format '{{.Image}}' "$("${COMPOSE[@]}" ps -q "$svc")")
  built_image=$(docker image inspect --format '{{.Id}}' "communication-reliability-lab-${svc}")
  [[ $running_image == "$built_image" ]] || die "$svc 가 지금의 코드로 지은 이미지로 떠 있지 않다 — scripts/up.sh 를 다시 돌린다"
done

# 2. 설정 기록 — 바꾼 것과 같게 둔 것. git을 읽지 못하면(사본으로 옮긴 경우 등) "모름"으로 적는다.
git_commit=$(git rev-parse HEAD 2>/dev/null) || git_commit=모름
if git_status=$(git status --porcelain 2>/dev/null); then
  [[ -z $git_status ]] && git_dirty=no || git_dirty=yes
else
  git_dirty=모름; git_status=""
fi
{
  echo "scenario=$SCENARIO"
  echo "rep=$REP"
  echo "started_at=$(date -Iseconds)"
  echo "git_commit=$git_commit"
  echo "git_dirty=$git_dirty"
  echo "git_dirty_paths=$(awk '{print $NF}' <<< "$git_status" | paste -sd' ' -)"
  echo "rate_per_second=$RATE"
  echo "duration_seconds=$DURATION_SECONDS"
  echo "planned_requests=$PLANNED"
  echo "k6_request_timeout=$REQUEST_TIMEOUT"
  echo "settle_timeout_seconds=$SETTLE_TIMEOUT_SECONDS"
  echo "warmup=${WARMUP_RATE}/s x ${WARMUP_SECONDS}s"
  echo "k6_version=$(k6 version | head -1)"
  echo "host_nproc=$(nproc)"
  echo "host_mem_total_kb=$(awk '/MemTotal/ {print $2}' /proc/meminfo)"
} > "$OUT/config.env"
"${COMPOSE[@]}" config > "$OUT/compose.resolved.yaml"
"${COMPOSE[@]}" images > "$OUT/images.txt"
docker image inspect --format '{{index .RepoDigests 0}}' postgres:18.7 >> "$OUT/images.txt"
for entry in "${SERVICE_PORTS[@]}"; do
  svc=${entry%%:*}; port=${entry#*:}
  curl -sf "localhost:${port}/settings" > "$OUT/settings-${svc}.json"
done

# 3. 워밍업(장애 없음). 한 회차가 모두 201이 될 때까지 최대 3회차 — 셋 다 아니면 서비스가 정상이 아닌 채로 재는 것이라 멈춘다.
#    막 뜬 JVM에서는 첫 회차의 일부가 층의 타임아웃(1000ms)을 넘는다(504 32/300 — 실측 2026-11-24).
warmed=no
for round in 1 2 3; do
  log "워밍업 ${round}회차 ${WARMUP_RATE}/s × ${WARMUP_SECONDS}s"
  k6 run -q -e SCENARIO=warmup -e BASE_URL="$BASE_URL" -e RATE="$WARMUP_RATE" -e DURATION_SECONDS="$WARMUP_SECONDS" \
    -e REQUEST_TIMEOUT="$REQUEST_TIMEOUT" -e RUN_ID="warmup${round}-${NAME}" \
    --log-output=file="$OUT/warmup${round}-k6-log.txt" --log-format=raw load/chain.js > "$OUT/warmup${round}-k6-stdout.txt" 2>&1 \
    || die "워밍업 k6가 실패했다 — $OUT/warmup${round}-k6-stdout.txt"
  warm_sent=$(grep -c '^{"type":"answer"' "$OUT/warmup${round}-k6-log.txt" || true)
  warm_ok=$(grep -c '"status":201' "$OUT/warmup${round}-k6-log.txt" || true)
  echo "warmup_round_${round}=${warm_ok}/${warm_sent} 201" >> "$OUT/config.env"
  if [[ $warm_sent -gt 0 && $warm_sent == "$warm_ok" ]]; then warmed=yes; break; fi
  log "워밍업 ${round}회차: ${warm_sent}건 중 201이 ${warm_ok}건"
done
[[ $warmed == yes ]] || die "워밍업 3회차 모두 201이 아닌 응답이 있었다"

# 4. 상태 초기화: 이 프로젝트의 세 DB의 행과 서비스의 장애 카운터
log "상태 초기화"
psql_lab order_db "truncate orders restart identity"
psql_lab inventory_db "truncate reservations restart identity"
psql_lab payment_db "truncate payments restart identity"
for entry in "${SERVICE_PORTS[@]}"; do
  curl -sf -X DELETE "localhost:${entry#*:}/faults" > /dev/null
done
left_o=$(psql_lab order_db "select count(*) from orders")
left_i=$(psql_lab inventory_db "select count(*) from reservations")
left_p=$(psql_lab payment_db "select count(*) from payments")
[[ "$left_o $left_i $left_p" == "0 0 0" ]] || die "초기화 뒤에도 행이 남았다 ($left_o $left_i $left_p)"

# 5. 부하. k6가 실패로 끝나면(스크립트 오류 등) 실행도 실패다.
log "부하 ${RATE}/s × ${DURATION_SECONDS}s = ${PLANNED}건"
k6 run -e SCENARIO="$SCENARIO" -e BASE_URL="$BASE_URL" -e RATE="$RATE" -e DURATION_SECONDS="$DURATION_SECONDS" \
  -e REQUEST_TIMEOUT="$REQUEST_TIMEOUT" -e RUN_ID="$NAME" \
  --log-output=file="$OUT/k6-log.txt" --log-format=raw \
  --summary-export="$OUT/k6-summary.json" load/chain.js > "$OUT/k6-stdout.txt" 2>&1 \
  || die "k6가 실패했다 — $OUT/k6-stdout.txt"
grep '^{"type":"answer"' "$OUT/k6-log.txt" > "$OUT/answers.jsonl" || die "정답 줄이 하나도 없다 — $OUT/k6-log.txt"
# k6가 VU가 모자라 시작하지 못한 반복(dropped_iterations, Counter). 0이면 요약에 지표가 아예 없다.
if grep -q '"dropped_iterations"' "$OUT/k6-summary.json"; then
  dropped=$(grep -A3 '"dropped_iterations"' "$OUT/k6-summary.json" | sed -n 's/^ *"count": *\([0-9][0-9]*\).*/\1/p' | head -1)
  die "k6가 보내지 못한 반복이 있다(dropped_iterations ${dropped:-읽지 못함}) — $OUT/k6-summary.json"
fi

# 6. 가라앉기: 세 서비스의 처리 중인 요청(지연을 넣어 아직 자는 것 포함)이 모두 0이 될 때까지 기다린다.
log "가라앉기 — 처리 중인 요청이 0이 될 때까지(최대 ${SETTLE_TIMEOUT_SECONDS}s)"
settled_ms=$(wait_drained "$SETTLE_TIMEOUT_SECONDS")
echo "settled_after_ms=$settled_ms" >> "$OUT/config.env"
log "가라앉음: ${settled_ms}ms"
require_running

# 7. 세 DB의 상태와 서비스가 센 장애 수를 내보낸다.
psql_csv() { "${COMPOSE[@]}" exec -T postgres psql -U lab -d "$1" -v ON_ERROR_STOP=1 -q -c "\\copy ($2) to stdout with (format csv, header)"; }
psql_csv order_db "select request_id, amount, status from orders order by id" > "$OUT/db/orders.csv"
psql_csv inventory_db "select request_id, status from reservations order by id" > "$OUT/db/reservations.csv"
psql_csv payment_db "select request_id, amount, status from payments order by id" > "$OUT/db/payments.csv"
f_order=$(curl -sf localhost:8081/faults)
f_inventory=$(curl -sf localhost:8082/faults)
f_payment=$(curl -sf localhost:8083/faults)
printf '{"order":%s,"inventory":%s,"payment":%s}\n' "$f_order" "$f_inventory" "$f_payment" > "$OUT/fault-counts.json"

# 8. 분석(컨테이너 안). 기대 파일이 있으면 실행 기록에 함께 넣고 견준다.
args=(--run "/runs/${NAME}" --planned "$PLANNED")
if [[ -f "load/expectations/${SCENARIO}.json" ]]; then
  cp "load/expectations/${SCENARIO}.json" "$OUT/expect.json"
  args+=(--expect "/runs/${NAME}/expect.json")
fi
set +e
"${COMPOSE[@]}" run --rm --no-deps --user "$(id -u):$(id -g)" analyzer "${args[@]}"
rc=$?
set -e
log "기록: $OUT (분석 종료 코드 $rc)"
exit "$rc"
