#!/usr/bin/env bash
# 실행 한 번: 확인 → 이미지 확인 → 워밍업 → 상태 초기화 → 부하 → 가라앉기 → DB 상태 내보내기 → 분석.
#   scripts/run.sh <시나리오> <반복 번호>
#   시나리오: calibration | duplicate (task 01) · false-timeout | slow-layer | split-timeout | cal-false-timeout | cal-sampler (task 02)
#             · retry-mix | retry-outage-burst | retry-budget | cal-retry-first | cal-retry-second | cal-retry-outage | cal-retry-burst (task 03)
#             · cal-idem | idem-retry-mix | idem-dup-seq | idem-dup-conc | idem-plain | idem-reconcile (task 04)
#             · cal-overload | overload-spike | overload-low | overload-edge | overload-burst (task 04-b)
#   반복 번호 0 = 서비스를 새로 띄운 직후의 버리는 실행(config.env에 discard_run=yes — 결과 표에 넣지 않는다).
# task 02의 조건은 환경 변수로 준다(서비스도 같은 값으로 띄워 둔다 — scripts/up.sh): ORDER_TO_INVENTORY_TIMEOUT,
#   INVENTORY_TO_PAYMENT_TIMEOUT(예: 2000ms), VIRTUAL_THREADS(true|false). 주지 않으면 task 01의 값(2000ms, 1000ms, false).
#   떠 있는 서비스의 /settings(실제 값)가 이 값과 다르면 실행 실패.
# 기록: runs/<시나리오>-<시각>-r<번호>/ (형식은 docs/measurement.md)
# 서비스는 먼저 scripts/up.sh 로 띄워 둔다. 종료 코드: 0 통과, 1 실행 실패, 2 분석 결과가 기대와 다름.
# (task 06) 서버에서 잴 때: SERVICES_SSH(서비스 서버 — scripts/lib.sh 머리)를 주고 이 스크립트를 부하 서버에서 돌린다. 그때만 더 하는 것:
#   두 서버의 코드가 같은 커밋인지(scripts/servers-sync.sh가 보낸 .deployed-commit·.deployed-sha256), 두 서버가 조용한지
#   (1분 load average ≤ 코어 수 × LOADAVG_MAX_PER_CORE — 아니면 10초마다 보며 LOADAVG_WAIT_SECONDS까지 기다리고, 그래도 높으면 실패),
#   두 서버 시계의 어긋남(실행 앞·뒤 두 번 — 앞뒤의 차가 CLOCK_DRIFT_MAX_MS(기본 5)ms보다 크면 분석기가 입력 오류로 낸다),
#   서비스 서버의 이름·코어·메모리·load average(앞·뒤)를 config.env에 남긴다. 규칙과 까닭: docs/experiments/task-06-design.md 2절,
#   어긋남을 분석에 쓰는 규칙: docs/measurement.md 12절. 앞의 어긋남은 k6에도 넘겨(CLOCK_OFFSET_MS) 구간 장애의 시각을 서비스의 시계로 옮긴다.
# K6 = k6 실행 파일(기본 PATH의 k6). 판이 K6_REQUIRED_VERSION이 아니면 실패한다(노트북과 서버가 같은 판 — docs/dependencies.md "task 06").
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/lib.sh

SCENARIO=${1:?"시나리오(calibration|duplicate)"}
REP=${2:?"반복 번호"}

# 시나리오마다의 부하 모양. SETTLE_TIMEOUT = k6가 끝난 뒤 세 서비스의 처리 중인 요청이 0이 되기를 기다리는 최대 시간.
# 시나리오의 가장 긴 지연(calibration: order:delay=6000)보다 길게 둔다. 그 안에 0이 안 되면 실행 실패.
# 환경 변수 SETTLE_TIMEOUT_SECONDS로 바꿀 수 있다(기록에 남는다 — 가라앉기 검사가 실패를 내는지 일부러 짧게 해 볼 때).
# REQUEST_TIMEOUT = k6의 요청 타임아웃(k6 기본 60s를 쓰지 않는다 — 층의 타임아웃보다 길게). MAX_VUS = 사슬 시나리오의 VU 수
# (모두 미리 만든다 — 도착률 × k6 요청 타임아웃보다 크게: slow-layer 60/s × 20s = 1200 < 1500).
# TIMING = 시각 기록·표본을 분석하나(task 02). SLOW_LABEL = 느린 구간을 가리키는 장애 값(slow-layer).
# task 02의 부하 모양과 까닭: docs/experiments/task-02-design.md
# K6_NO_CONNECTION_REUSE = 부하의 k6가 keep-alive를 끄나(요청마다 새 연결, `Connection: close`). slow-layer만 켠다 —
# VU 1500개를 돌려 쓰면 한 연결이 25s 쉬었다 다시 쓰여 주문 서비스의 keep-alive(20s)를 넘는다. 스레드가 모두 찬 Tomcat은
# 그 연결을 닫는 일을 실행기의 큐에 넣어 늦게 닫으므로, k6가 그 사이 그 연결에 보낸 요청이 EOF로 끊겼다
# (2026-11-24 — docs/results/task-02-timeouts.md "실험 나" R02-1). 서비스 사이의 HttpClient는 15s < 20s로 이미 피한다.
# RETRY = 재시도 분석(task 03 — analyzer retry)을 하나. task 03의 시나리오는 모두 k6의 연결 재사용을 끈다(task 02 R02-1과 같은 까닭).
# IDEM = 멱등 키 분석(task 04 — analyzer idem)을 하나. RESEND = 부하가 가라앉은 뒤 결과 모름을 같은 키로 다시 보내나(맞추기 단계).
# ANSWERS_PER_ITERATION = 반복 하나가 남기는 정답 줄 수(같은 요청을 두 번 보내는 시나리오는 2, cal-idem은 8칸 가운데 셋이 2 → 11/8).
# (task 05) 모든 시나리오가 전송 대조(analyzer transport)를 거친다 — 고른 전송과 실제로 쓰인 전송을 견준다(docs/measurement.md 11절).
# OVERLOAD = 한도 분석(task 04-b — analyzer overload)을 하나. SPIKE_* = 평소 RATE 위에 SPIKE_FROM_SECONDS부터 SPIKE_SECONDS 동안 더하는 초당 수.
LOOKUP_RATE=0; SLOW_LABEL=""; TIMING=no; K6_NO_CONNECTION_REUSE=false; RETRY=no; IDEM=no; RESEND=no; ANSWERS_PER_8_ITERATIONS=8
OVERLOAD=no; SPIKE_RATE=0; SPIKE_FROM_SECONDS=0; SPIKE_SECONDS=0
case "$SCENARIO" in
  calibration)       RATE=20; DURATION_SECONDS=30; DEFAULT_SETTLE_TIMEOUT=20;  REQUEST_TIMEOUT=5s;  MAX_VUS=300 ;;
  duplicate)         RATE=20; DURATION_SECONDS=15; DEFAULT_SETTLE_TIMEOUT=10;  REQUEST_TIMEOUT=5s;  MAX_VUS=300 ;;
  false-timeout)     RATE=40; DURATION_SECONDS=30; DEFAULT_SETTLE_TIMEOUT=20;  REQUEST_TIMEOUT=10s; MAX_VUS=300;  TIMING=yes ;;
  slow-layer)        RATE=60; DURATION_SECONDS=50; DEFAULT_SETTLE_TIMEOUT=180; REQUEST_TIMEOUT=20s; MAX_VUS=1500; TIMING=yes
                     LOOKUP_RATE=10; SLOW_LABEL="payment:delay=5000"; K6_NO_CONNECTION_REUSE=true ;;
  split-timeout)     RATE=20; DURATION_SECONDS=40; DEFAULT_SETTLE_TIMEOUT=20;  REQUEST_TIMEOUT=10s; MAX_VUS=300;  TIMING=yes ;;
  cal-false-timeout) RATE=20; DURATION_SECONDS=20; DEFAULT_SETTLE_TIMEOUT=10;  REQUEST_TIMEOUT=10s; MAX_VUS=300;  TIMING=yes ;;
  cal-sampler)       RATE=50; DURATION_SECONDS=30; DEFAULT_SETTLE_TIMEOUT=20;  REQUEST_TIMEOUT=10s; MAX_VUS=300;  TIMING=yes ;;
  # task 03 (docs/experiments/task-03-design.md)
  cal-retry-first)    RATE=20; DURATION_SECONDS=15; DEFAULT_SETTLE_TIMEOUT=20; REQUEST_TIMEOUT=10s; MAX_VUS=300; RETRY=yes; K6_NO_CONNECTION_REUSE=true ;;
  cal-retry-second)   RATE=20; DURATION_SECONDS=15; DEFAULT_SETTLE_TIMEOUT=20; REQUEST_TIMEOUT=10s; MAX_VUS=300; RETRY=yes; K6_NO_CONNECTION_REUSE=true ;;
  cal-retry-outage)   RATE=20; DURATION_SECONDS=20; DEFAULT_SETTLE_TIMEOUT=20; REQUEST_TIMEOUT=10s; MAX_VUS=300; RETRY=yes; K6_NO_CONNECTION_REUSE=true ;;
  cal-retry-burst)    RATE=40; DURATION_SECONDS=15; DEFAULT_SETTLE_TIMEOUT=20; REQUEST_TIMEOUT=10s; MAX_VUS=300; RETRY=yes; K6_NO_CONNECTION_REUSE=true ;;
  retry-mix)          RATE=40; DURATION_SECONDS=20; DEFAULT_SETTLE_TIMEOUT=20; REQUEST_TIMEOUT=10s; MAX_VUS=300; RETRY=yes; K6_NO_CONNECTION_REUSE=true ;;
  retry-outage-burst) RATE=40; DURATION_SECONDS=30; DEFAULT_SETTLE_TIMEOUT=20; REQUEST_TIMEOUT=10s; MAX_VUS=300; RETRY=yes; K6_NO_CONNECTION_REUSE=true ;;
  retry-budget)       RATE=40; DURATION_SECONDS=40; DEFAULT_SETTLE_TIMEOUT=20; REQUEST_TIMEOUT=10s; MAX_VUS=300; RETRY=yes; K6_NO_CONNECTION_REUSE=true ;;
  # task 04 (docs/experiments/task-04-design.md) — 모두 k6의 연결 재사용을 끈다
  cal-idem)       RATE=16; DURATION_SECONDS=25; DEFAULT_SETTLE_TIMEOUT=20; REQUEST_TIMEOUT=10s; MAX_VUS=300; IDEM=yes; RESEND=yes; ANSWERS_PER_8_ITERATIONS=11; K6_NO_CONNECTION_REUSE=true ;;
  idem-retry-mix) RATE=40; DURATION_SECONDS=20; DEFAULT_SETTLE_TIMEOUT=20; REQUEST_TIMEOUT=10s; MAX_VUS=300; IDEM=yes; RETRY=yes; K6_NO_CONNECTION_REUSE=true ;;
  idem-dup-seq)   RATE=20; DURATION_SECONDS=20; DEFAULT_SETTLE_TIMEOUT=20; REQUEST_TIMEOUT=10s; MAX_VUS=300; IDEM=yes; ANSWERS_PER_8_ITERATIONS=16; K6_NO_CONNECTION_REUSE=true ;;
  idem-dup-conc)  RATE=20; DURATION_SECONDS=20; DEFAULT_SETTLE_TIMEOUT=20; REQUEST_TIMEOUT=10s; MAX_VUS=300; IDEM=yes; ANSWERS_PER_8_ITERATIONS=16; K6_NO_CONNECTION_REUSE=true ;;
  idem-plain)     RATE=40; DURATION_SECONDS=30; DEFAULT_SETTLE_TIMEOUT=20; REQUEST_TIMEOUT=10s; MAX_VUS=300; IDEM=yes; K6_NO_CONNECTION_REUSE=true ;;
  idem-reconcile) RATE=40; DURATION_SECONDS=20; DEFAULT_SETTLE_TIMEOUT=20; REQUEST_TIMEOUT=10s; MAX_VUS=300; IDEM=yes; RESEND=yes; K6_NO_CONNECTION_REUSE=true ;;
  # task 04-b (docs/experiments/task-04b-design.md) — 평소 25/s × 35s, 10s부터 5s 동안 +75/s. 재시도 분석과 한도 분석. 연결 재사용을 끈다
  #   리뷰 뒤 더 잰 세 자리(설계 10절)는 평소 도착률만 다르다: overload-low 15/s · overload-edge 17/s · overload-burst 평균 15/s(2s마다 30건 무리 — load/chain.js)
  cal-overload|overload-spike|overload-low|overload-edge|overload-burst)
                  RATE=25; DURATION_SECONDS=35; SPIKE_RATE=75; SPIKE_FROM_SECONDS=10; SPIKE_SECONDS=5; DEFAULT_SETTLE_TIMEOUT=20; REQUEST_TIMEOUT=10s
                  MAX_VUS=150; RETRY=yes; OVERLOAD=yes; K6_NO_CONNECTION_REUSE=true
                  case "$SCENARIO" in overload-low|overload-burst) RATE=15 ;; overload-edge) RATE=17 ;; esac ;;
  *) die "모르는 시나리오 '$SCENARIO'" ;;
esac
SETTLE_TIMEOUT_SECONDS=${SETTLE_TIMEOUT_SECONDS:-$DEFAULT_SETTLE_TIMEOUT}
# task 02의 조건(기본값 = task 01의 값, compose.yaml과 같다).
ORDER_TO_INVENTORY_TIMEOUT=${ORDER_TO_INVENTORY_TIMEOUT:-2000ms}
INVENTORY_TO_PAYMENT_TIMEOUT=${INVENTORY_TO_PAYMENT_TIMEOUT:-1000ms}
VIRTUAL_THREADS=${VIRTUAL_THREADS:-false}
# task 05의 조건: 서비스 사이의 전송과 gRPC의 기한·취소(기본 http·꺼짐 — compose.yaml과 같다). 규칙: docs/experiments/task-05-design.md
CHAIN_TRANSPORT=${CHAIN_TRANSPORT:-http}
GRPC_DEADLINE_PROPAGATION=${GRPC_DEADLINE_PROPAGATION:-false}
GRPC_OBSERVE_CANCELLATION=${GRPC_OBSERVE_CANCELLATION:-false}
[[ $CHAIN_TRANSPORT == http || $CHAIN_TRANSPORT == grpc ]] || die "CHAIN_TRANSPORT '$CHAIN_TRANSPORT' 는 http|grpc"
for v in GRPC_DEADLINE_PROPAGATION GRPC_OBSERVE_CANCELLATION; do
  [[ ${!v} == true || ${!v} == false ]] || die "$v '${!v}' 는 true|false"
  [[ ${!v} == false || $CHAIN_TRANSPORT == grpc ]] || die "$v=true 는 CHAIN_TRANSPORT=grpc 에서만"
done
[[ -z ${TRANSPORT_EXPECT:-} || -f $TRANSPORT_EXPECT ]] || die "TRANSPORT_EXPECT '$TRANSPORT_EXPECT' 가 없다"
# task 04의 조건: 멱등 키(기본 꺼짐 — compose.yaml과 같다). 규칙: docs/idempotency.md
IDEMPOTENCY_ENABLED=${IDEMPOTENCY_ENABLED:-false}
# task 04-b의 조건: 결제의 한도(기본 꺼짐 — compose.yaml과 같다). 규칙: docs/measurement.md 10절
PAYMENT_OVERLOAD_ENABLED=${PAYMENT_OVERLOAD_ENABLED:-false}
PAYMENT_OVERLOAD_LIMIT=${PAYMENT_OVERLOAD_LIMIT:-0}
PAYMENT_OVERLOAD_WINDOW=${PAYMENT_OVERLOAD_WINDOW:-1000ms}
[[ $PAYMENT_OVERLOAD_ENABLED == true || $PAYMENT_OVERLOAD_ENABLED == false ]] || die "PAYMENT_OVERLOAD_ENABLED '$PAYMENT_OVERLOAD_ENABLED' 는 true|false"
[[ $OVERLOAD == no || $PAYMENT_OVERLOAD_ENABLED == true ]] || die "시나리오 $SCENARIO 는 결제의 한도가 켜져 있어야 한다(PAYMENT_OVERLOAD_ENABLED=true)"
# 한도 분석의 기대 파일(보정). 주면 꼭 있어야 한다. 주지 않으면 load/expectations/<시나리오>.overload.json이 있을 때만.
if [[ -n ${OVERLOAD_EXPECT:-} ]]; then
  [[ -f $OVERLOAD_EXPECT ]] || die "OVERLOAD_EXPECT '$OVERLOAD_EXPECT' 가 없다"
elif [[ -f load/expectations/${SCENARIO}.overload.json ]]; then
  OVERLOAD_EXPECT=load/expectations/${SCENARIO}.overload.json
fi
[[ $IDEMPOTENCY_ENABLED == true || $IDEMPOTENCY_ENABLED == false ]] || die "IDEMPOTENCY_ENABLED '$IDEMPOTENCY_ENABLED' 는 true|false"
# task 04(더 재는 조건): 위 층이 아래 층의 409(같은 키 처리 중)를 결과 모름으로 읽나(기본 false — 지금까지의 동작). 키가 켜져 있을 때만 켤 수 있다.
IDEMPOTENCY_IN_PROGRESS_AS_UNKNOWN=${IDEMPOTENCY_IN_PROGRESS_AS_UNKNOWN:-false}
[[ $IDEMPOTENCY_IN_PROGRESS_AS_UNKNOWN == true || $IDEMPOTENCY_IN_PROGRESS_AS_UNKNOWN == false ]] \
  || die "IDEMPOTENCY_IN_PROGRESS_AS_UNKNOWN '$IDEMPOTENCY_IN_PROGRESS_AS_UNKNOWN' 는 true|false"
[[ $IDEMPOTENCY_IN_PROGRESS_AS_UNKNOWN == false || $IDEMPOTENCY_ENABLED == true ]] || die "IDEMPOTENCY_IN_PROGRESS_AS_UNKNOWN=true 는 멱등 키가 켜져 있을 때만"
# task 03의 조건: 층마다의 재시도(기본값 = 꺼짐, compose.yaml과 같다). 규칙: docs/retry.md
for p in ORDER INVENTORY; do
  for kv in ENABLED=false MAX_ATTEMPTS=1 ON=failed,unknown BACKOFF=none BASE_DELAY=0ms MAX_DELAY=0ms BUDGET_ENABLED=false BUDGET_CAPACITY=0 BUDGET_DEPOSIT=0; do
    var="${p}_RETRY_${kv%%=*}"
    [[ -n ${!var:-} ]] || printf -v "$var" '%s' "${kv#*=}"
  done
done
# 재시도 분석의 기대 파일(보정). 주면 꼭 있어야 한다. 주지 않으면 load/expectations/<시나리오>.retry.json이 있을 때만.
if [[ -n ${RETRY_EXPECT:-} ]]; then
  [[ -f $RETRY_EXPECT ]] || die "RETRY_EXPECT '$RETRY_EXPECT' 가 없다"
elif [[ -f load/expectations/${SCENARIO}.retry.json ]]; then
  RETRY_EXPECT=load/expectations/${SCENARIO}.retry.json
fi
# task 04: 대조 분석의 기대 파일을 바꿔 줄 때(키 켬/끔의 보정이 같은 시나리오라서). 주면 꼭 있어야 한다.
# 주지 않으면 load/expectations/<시나리오>.json이 있을 때만(task 01~03 그대로). 멱등 키 분석의 기대 파일은 IDEM_EXPECT.
[[ -z ${RECON_EXPECT:-} || -f $RECON_EXPECT ]] || die "RECON_EXPECT '$RECON_EXPECT' 가 없다"
[[ -z ${IDEM_EXPECT:-} || -f $IDEM_EXPECT ]] || die "IDEM_EXPECT '$IDEM_EXPECT' 가 없다"
# 분석기가 견줄 시각 지표의 기대 파일(보정). 주면 꼭 있어야 한다. 주지 않으면 load/expectations/<시나리오>.timing.json이 있을 때만.
if [[ -n ${TIMING_EXPECT:-} ]]; then
  [[ -f $TIMING_EXPECT ]] || die "TIMING_EXPECT '$TIMING_EXPECT' 가 없다"
elif [[ -f load/expectations/${SCENARIO}.timing.json ]]; then
  TIMING_EXPECT=load/expectations/${SCENARIO}.timing.json
fi
ms_of() { [[ $1 =~ ^([0-9]+)ms$ ]] || die "타임아웃 '$1' 은 <정수>ms 꼴이어야 한다"; echo "${BASH_REMATCH[1]}"; }
[[ $VIRTUAL_THREADS == true || $VIRTUAL_THREADS == false ]] || die "VIRTUAL_THREADS '$VIRTUAL_THREADS' 는 true|false"
WARMUP_REQUEST_TIMEOUT=5s
WARMUP_RATE=20
WARMUP_SECONDS=15
PLANNED=$((RATE * DURATION_SECONDS + SPIKE_RATE * SPIKE_SECONDS)) # task 04-b: 구간 동안 더하는 요청까지
# 정답 줄 수의 계획(분석기의 --planned). 같은 요청을 두 번 보내는 시나리오는 반복 수보다 많다(task 04).
((PLANNED % 8 == 0)) || ((ANSWERS_PER_8_ITERATIONS == 8)) || die "반복 수 $PLANNED 가 8의 배수가 아니다"
PLANNED_ANSWERS=$((PLANNED * ANSWERS_PER_8_ITERATIONS / 8))
BASE_URL=http://${SERVICES_HOST}:8081
K6=${K6:-k6}
K6_REQUIRED_VERSION=v2.4.0
k6_version=$("$K6" version 2>&1 | head -1) || die "k6($K6)를 부르지 못했다: $k6_version"
[[ $k6_version == "k6 $K6_REQUIRED_VERSION "* ]] || die "k6 판이 $K6_REQUIRED_VERSION 이 아니다($K6: $k6_version)"
LOADAVG_MAX_PER_CORE=${LOADAVG_MAX_PER_CORE:-0.25}
LOADAVG_WAIT_SECONDS=${LOADAVG_WAIT_SECONDS:-600}
CLOCK_DRIFT_MAX_MS=${CLOCK_DRIFT_MAX_MS:-5}
[[ $CLOCK_DRIFT_MAX_MS =~ ^[0-9]+(\.[0-9]+)?$ ]] || die "CLOCK_DRIFT_MAX_MS '$CLOCK_DRIFT_MAX_MS' 는 0 이상의 10진 수(ms)"

NAME="${SCENARIO}-$(date +%Y%m%dT%H%M%S)-r${REP}"
OUT="runs/${NAME}"
mkdir -p "$OUT/db"
log "실행 $NAME"

# 1. 확인: 네 컨테이너가 떠 있고 세 서비스가 UP
require_running
wait_ready
# 1-0. (task 06) 두 서버의 코드가 scripts/servers-sync.sh가 보낸 같은 커밋이고, 보낸 뒤 바뀐 파일이 없는가.
if [[ -n $SERVICES_SSH ]]; then
  [[ -f .deployed-commit ]] || die "이 컴퓨터의 사본에 .deployed-commit이 없다 — scripts/servers-sync.sh로 보낸다"
  services_commit=$(on_services cat .deployed-commit) || die "서비스 서버의 .deployed-commit을 읽지 못했다"
  [[ $services_commit == "$(cat .deployed-commit)" ]] || die "두 서버의 코드가 다른 커밋이다(이 컴퓨터 $(cat .deployed-commit), 서비스 서버 $services_commit)"
  sha256sum --quiet -c .deployed-sha256 > /dev/null 2>&1 || die "이 컴퓨터의 사본이 보낸 커밋과 다르다(sha256sum -c .deployed-sha256)"
  on_services sha256sum --quiet -c .deployed-sha256 > /dev/null 2>&1 || die "서비스 서버의 사본이 보낸 커밋과 다르다"
fi

# 1-1. 이미지가 지금의 코드로 지어졌나: 짓고(바뀐 것이 없으면 층 캐시로 금방 끝난다),
#      떠 있는 세 서비스가 방금 지은 이미지로 떠 있는지 본다. 분석기는 이 실행의 끝에 방금 지은 이미지로 돈다.
log "이미지 짓기(분석기 포함) — 바뀐 것이 없으면 캐시"
build_images > "$OUT/image-build.txt" 2>&1 || die "이미지 짓기가 실패했다 — $OUT/image-build.txt"
for svc in order inventory payment; do
  running_image=$("${DOCKER[@]}" inspect --format '{{.Image}}' "$("${COMPOSE[@]}" ps -q "$svc")")
  built_image=$("${DOCKER[@]}" image inspect --format '{{.Id}}' "communication-reliability-lab-${svc}")
  [[ $running_image == "$built_image" ]] || die "$svc 가 지금의 코드로 지은 이미지로 떠 있지 않다 — scripts/up.sh 를 다시 돌린다"
done

# 1-2. (task 06) 두 서버가 조용해질 때까지 기다리고(못 미치면 실패), 시계의 어긋남을 잰다.
if [[ -n $SERVICES_SSH ]]; then
  log "두 서버의 load average 확인(코어당 ${LOADAVG_MAX_PER_CORE} 이하, 최대 ${LOADAVG_WAIT_SECONDS}s 기다림)"
  quiet=$(wait_quiet "$LOADAVG_MAX_PER_CORE" "$LOADAVG_WAIT_SECONDS")
  clock=$(clock_offset)
fi

# 2. 설정 기록 — 바꾼 것과 같게 둔 것. git을 읽지 못하면(사본으로 옮긴 경우 등) "모름"으로 적는다.
git_commit=$(git rev-parse HEAD 2>/dev/null) || git_commit=모름
if git_status=$(git status --porcelain 2>/dev/null); then
  [[ -z $git_status ]] && git_dirty=no || git_dirty=yes
elif [[ -f .deployed-commit && -f .deployed-sha256 ]]; then
  # (task 06) scripts/servers-sync.sh가 보낸 사본: 보낸 커밋, 깨끗한지는 보낸 파일의 sha256으로 본다(보낸 뒤 더한 파일은 보지 못한다).
  git_commit=$(cat .deployed-commit)
  if git_status=$(sha256sum --quiet -c .deployed-sha256 2>/dev/null); then git_dirty=no; else git_dirty=yes; git_status=$(sed -n 's/: FAILED.*$//p' <<< "$git_status"); fi
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
  echo "planned_answers=$PLANNED_ANSWERS"
  echo "k6_request_timeout=$REQUEST_TIMEOUT"
  echo "k6_max_vus=$MAX_VUS"
  echo "k6_no_connection_reuse=$K6_NO_CONNECTION_REUSE"
  echo "lookup_rate_per_second=$LOOKUP_RATE"
  echo "slow_label=$SLOW_LABEL"
  echo "order_to_inventory_timeout=$ORDER_TO_INVENTORY_TIMEOUT"
  echo "inventory_to_payment_timeout=$INVENTORY_TO_PAYMENT_TIMEOUT"
  echo "virtual_threads=$VIRTUAL_THREADS"
  echo "chain_transport=$CHAIN_TRANSPORT"
  echo "grpc_deadline_propagation=$GRPC_DEADLINE_PROPAGATION"
  echo "grpc_observe_cancellation=$GRPC_OBSERVE_CANCELLATION"
  echo "transport_expect=${TRANSPORT_EXPECT:-}"
  echo "timing_expect=${TIMING_EXPECT:-}"
  echo "retry_expect=${RETRY_EXPECT:-}"
  echo "idempotency_enabled=$IDEMPOTENCY_ENABLED"
  echo "idempotency_in_progress_as_unknown=$IDEMPOTENCY_IN_PROGRESS_AS_UNKNOWN"
  echo "recon_expect=${RECON_EXPECT:-}"
  echo "idem_expect=${IDEM_EXPECT:-}"
  echo "resend=$RESEND"
  echo "payment_overload_enabled=$PAYMENT_OVERLOAD_ENABLED"
  echo "payment_overload_limit=$PAYMENT_OVERLOAD_LIMIT"
  echo "payment_overload_window=$PAYMENT_OVERLOAD_WINDOW"
  echo "spike_rate_per_second=$SPIKE_RATE"
  echo "spike_from_seconds=$SPIKE_FROM_SECONDS"
  echo "spike_seconds=$SPIKE_SECONDS"
  echo "overload_expect=${OVERLOAD_EXPECT:-}"
  for p in ORDER INVENTORY; do
    for k in ENABLED MAX_ATTEMPTS ON BACKOFF BASE_DELAY MAX_DELAY BUDGET_ENABLED BUDGET_CAPACITY BUDGET_DEPOSIT; do
      var="${p}_RETRY_${k}"; echo "$(tr '[:upper:]' '[:lower:]' <<< "$var")=${!var}"
    done
  done
  [[ $REP == 0 ]] && echo "discard_run=yes" || echo "discard_run=no"
  echo "settle_timeout_seconds=$SETTLE_TIMEOUT_SECONDS"
  echo "warmup=${WARMUP_RATE}/s x ${WARMUP_SECONDS}s"
  echo "k6_version=$k6_version"
  echo "host_nproc=$(nproc)"
  echo "host_mem_total_kb=$(awk '/MemTotal/ {print $2}' /proc/meminfo)"
  echo "host_loadavg_start=$(cat /proc/loadavg)" # 이 컴퓨터의 다른 부하(task 04-b — 실행 끝의 값은 host_loadavg_end)
} > "$OUT/config.env"
# (task 06) 서버에서 잴 때만: 두 서버와 조용함·시계(규칙: docs/experiments/task-06-design.md 2절). 주소는 끝 마디만 남긴다.
if [[ -n $SERVICES_SSH ]]; then
  read -r quiet_waited quiet_load_here quiet_load_services <<< "$quiet"
  read -r clock_ms clock_err_ms <<< "$clock"
  s_host=$(on_services hostname) && s_nproc=$(on_services nproc) && s_mem=$(on_services awk '/MemTotal/ {print $2}' /proc/meminfo) \
    && s_load=$(on_services cat /proc/loadavg) || die "서비스 서버의 정보를 읽지 못했다"
  {
    echo "load_host_hostname=$(hostname)"
    echo "services_host=.${SERVICES_HOST##*.}"
    echo "services_hostname=$s_host"
    echo "services_nproc=$s_nproc"
    echo "services_mem_total_kb=$s_mem"
    echo "services_loadavg_start=$s_load"
    echo "loadavg_max_per_core=$LOADAVG_MAX_PER_CORE"
    echo "loadavg_wait_seconds=$LOADAVG_WAIT_SECONDS"
    echo "quiet_waited_seconds=$quiet_waited"
    echo "quiet_loadavg_load_host=$quiet_load_here"
    echo "quiet_loadavg_services_host=$quiet_load_services"
    echo "clock_offset_services_minus_load_ms_start=$clock_ms"
    echo "clock_offset_error_ms_start=$clock_err_ms"
    echo "clock_offset_drift_max_ms=$CLOCK_DRIFT_MAX_MS"
  } >> "$OUT/config.env"
fi
"${COMPOSE[@]}" config > "$OUT/compose.resolved.yaml"
"${COMPOSE[@]}" images > "$OUT/images.txt"
"${DOCKER[@]}" image inspect --format '{{index .RepoDigests 0}}' postgres:18.7 >> "$OUT/images.txt"
for entry in "${SERVICE_PORTS[@]}"; do
  svc=${entry%%:*}; port=${entry#*:}
  curl -sf "${SERVICES_HOST}:${port}/settings" > "$OUT/settings-${svc}.json"
done

# 2-1. 조건이 실제로 걸렸나 — 떠 있는 객체에서 읽은 값(/settings)과 이 실행의 조건을 견준다.
want_settings() { # <파일> <찾을 조각> <뜻>
  grep -qF "$2" "$1" || die "$3: $1 에 $2 가 없다 — 서비스를 이 조건으로 다시 띄운다(scripts/up.sh에 같은 환경 변수)"
}
want_settings "$OUT/settings-order.json" "\"requestTimeoutMs\":$(ms_of "$ORDER_TO_INVENTORY_TIMEOUT")," "주문→재고 요청 타임아웃"
want_settings "$OUT/settings-inventory.json" "\"requestTimeoutMs\":$(ms_of "$INVENTORY_TO_PAYMENT_TIMEOUT")," "재고→결제 요청 타임아웃"
for svc in order inventory payment; do
  want_settings "$OUT/settings-${svc}.json" "\"executorVirtualThreads\":$VIRTUAL_THREADS" "$svc 의 스레드 방식"
done
# task 03: 떠 있는 재시도 감싸개의 값(/settings의 retry)과 조건을 견준다. 숫자 뒤에는 , 나 } 가 와야 한다(3이 30에 맞지 않게).
want_settings_re() { # <파일> <정규식> <뜻>
  grep -qE "$2" "$1" || die "$3: $1 에 /$2/ 가 없다 — 서비스를 이 조건으로 다시 띄운다(scripts/up.sh에 같은 환경 변수)"
}
for p in ORDER INVENTORY; do
  svc=$(tr '[:upper:]' '[:lower:]' <<< "$p"); f="$OUT/settings-${svc}.json"
  v() { local n="${p}_RETRY_$1"; echo "${!n}"; }
  if [[ $(v ENABLED) == false ]]; then
    want_settings "$f" '"retry":{"enabled":false}' "$svc 의 재시도 꺼짐"
    continue
  fi
  [[ $(v ENABLED) == true ]] || die "${p}_RETRY_ENABLED '$(v ENABLED)' 는 true|false"
  on=$(tr ',' '\n' <<< "$(v ON)" | sort | sed 's/.*/"&"/' | paste -sd, -)
  want_settings_re "$f" '"retry":\{"enabled":true[,}]' "$svc 의 재시도 켜짐"
  want_settings_re "$f" "\"maxAttempts\":$(v MAX_ATTEMPTS)[,}]" "$svc 의 최대 시도"
  want_settings "$f" "\"retryOn\":[$on]" "$svc 의 다시 시도하는 실패"
  want_settings "$f" "\"backoff\":\"$(v BACKOFF)\"" "$svc 의 간격"
  want_settings_re "$f" "\"baseDelayMs\":$(ms_of "$(v BASE_DELAY)")[,}]" "$svc 의 간격 시작값"
  want_settings_re "$f" "\"maxDelayMs\":$(ms_of "$(v MAX_DELAY)")[,}]" "$svc 의 간격 상한"
  if [[ $(v BUDGET_ENABLED) == true ]]; then
    want_settings "$f" "\"capacity\":\"$(v BUDGET_CAPACITY)\"" "$svc 의 예산 용량(정규형 — 끝의 0 없이)"
    want_settings "$f" "\"depositPerCall\":\"$(v BUDGET_DEPOSIT)\"" "$svc 의 예산 넣는 양(정규형)"
  else
    want_settings "$f" '"budget":{"enabled":false}' "$svc 의 예산 꺼짐"
  fi
done

# task 04: 떠 있는 멱등 키 객체의 값과 조건을 견준다(세 서비스).
for svc in order inventory payment; do
  if [[ $IDEMPOTENCY_ENABLED == true ]]; then
    want_settings "$OUT/settings-${svc}.json" '"idempotency":{"enabled":true,' "$svc 의 멱등 키 켬"
    want_settings "$OUT/settings-${svc}.json" "\"inProgressAsUnknown\":$IDEMPOTENCY_IN_PROGRESS_AS_UNKNOWN}" "$svc 가 아래 층의 409를 읽는 법"
  else
    want_settings "$OUT/settings-${svc}.json" '"idempotency":{"enabled":false}' "$svc 의 멱등 키 꺼짐"
  fi
done

# task 05: 떠 있는 서비스가 고른 전송과 gRPC 조건(세 서비스에 같은 값). 아래 층을 부르는 두 서비스는 그 전송의 구현이 떠 있는지도 본다.
for svc in order inventory payment; do
  want_settings "$OUT/settings-${svc}.json" "\"transport\":{\"chosen\":\"$CHAIN_TRANSPORT\"," "$svc 의 전송"
  want_settings "$OUT/settings-${svc}.json" "\"deadlinePropagation\":$GRPC_DEADLINE_PROPAGATION," "$svc 의 기한 전파"
  want_settings "$OUT/settings-${svc}.json" "\"observeCancellation\":$GRPC_OBSERVE_CANCELLATION," "$svc 의 취소 보기"
done
impl=$([[ $CHAIN_TRANSPORT == grpc ]] && echo GrpcNextLayerCall || echo HttpNextLayerCall)
for svc in order inventory; do
  # 재시도(task 03)를 켠 층은 감싸개가 그 구현을 감싼다 — /settings는 "RetryingNextLayerCall(<구현>)"
  r="$(tr '[:lower:]' '[:upper:]' <<< "$svc")_RETRY_ENABLED"
  want=$impl; [[ ${!r} == true ]] && want="RetryingNextLayerCall($impl)"
  want_settings "$OUT/settings-${svc}.json" "\"nextLayerCall\":\"$want\"" "$svc 의 아래 층 호출 구현"
done
for svc in inventory payment; do
  want_settings "$OUT/settings-${svc}.json" "\"grpcServerRunning\":$([[ $CHAIN_TRANSPORT == grpc ]] && echo true || echo false)" "$svc 의 gRPC 서버"
done

# task 04-b: 떠 있는 한도 객체의 값과 조건을 견준다. 주문·재고는 늘 꺼짐(compose.yaml이 결제에만 준다).
for svc in order inventory; do
  want_settings "$OUT/settings-${svc}.json" '"overload":{"enabled":false}' "$svc 의 한도 꺼짐"
done
if [[ $PAYMENT_OVERLOAD_ENABLED == true ]]; then
  want_settings "$OUT/settings-payment.json" "\"overload\":{\"enabled\":true,\"limit\":${PAYMENT_OVERLOAD_LIMIT},\"windowMs\":$(ms_of "$PAYMENT_OVERLOAD_WINDOW"),\"rule\":\"sliding-window\"}" "결제의 한도"
else
  want_settings "$OUT/settings-payment.json" '"overload":{"enabled":false}' "결제의 한도 꺼짐"
fi

# 3. 워밍업(장애 없음). 한 회차가 모두 201이 될 때까지 최대 3회차 — 셋 다 아니면 서비스가 정상이 아닌 채로 재는 것이라 멈춘다.
#    막 뜬 JVM에서는 첫 회차의 일부가 층의 타임아웃(1000ms)을 넘는다(504 32/300 — 실측 2026-11-24).
warmed=no
for round in 1 2 3; do
  log "워밍업 ${round}회차 ${WARMUP_RATE}/s × ${WARMUP_SECONDS}s"
  "$K6" run -q -e SCENARIO=warmup -e BASE_URL="$BASE_URL" -e RATE="$WARMUP_RATE" -e DURATION_SECONDS="$WARMUP_SECONDS" \
    -e REQUEST_TIMEOUT="$WARMUP_REQUEST_TIMEOUT" -e MAX_VUS=300 -e RUN_ID="warmup${round}-${NAME}" -e WARMUP_KEYS="$IDEMPOTENCY_ENABLED" \
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
for db in order_db inventory_db payment_db; do psql_lab "$db" "truncate idempotency_keys"; done # task 04(꺼져 있으면 늘 비어 있다)
for entry in "${SERVICE_PORTS[@]}"; do
  curl -sf -X DELETE "${SERVICES_HOST}:${entry#*:}/faults" > /dev/null
  curl -sf -X DELETE "${SERVICES_HOST}:${entry#*:}/timings" > /dev/null
  curl -sf -X DELETE "${SERVICES_HOST}:${entry#*:}/samples" > /dev/null
  curl -sf -X DELETE "${SERVICES_HOST}:${entry#*:}/attempt-faults" > /dev/null
  curl -sf -X DELETE "${SERVICES_HOST}:${entry#*:}/attempts" > /dev/null
  curl -sf -X DELETE "${SERVICES_HOST}:${entry#*:}/drop-faults" > /dev/null
  curl -sf -X DELETE "${SERVICES_HOST}:${entry#*:}/idempotency-counts" > /dev/null
  curl -sf -X DELETE "${SERVICES_HOST}:${entry#*:}/overload" > /dev/null # task 04-b(꺼져 있으면 늘 0)
  curl -sf -X DELETE "${SERVICES_HOST}:${entry#*:}/transport-counts" > /dev/null # task 05
done
left_o=$(psql_lab order_db "select count(*) from orders")
left_i=$(psql_lab inventory_db "select count(*) from reservations")
left_p=$(psql_lab payment_db "select count(*) from payments")
[[ "$left_o $left_i $left_p" == "0 0 0" ]] || die "초기화 뒤에도 행이 남았다 ($left_o $left_i $left_p)"
# R03-7: 재시도 예산의 시작 토큰 — 예산은 실행 사이에 비우지 않으므로 부하 직전의 값을 남긴다(꺼져 있으면 {"enabled":false})
b_order=$(curl -sf ${SERVICES_HOST}:8081/retry-budget) && b_inventory=$(curl -sf ${SERVICES_HOST}:8082/retry-budget) || die "/retry-budget을 받지 못했다"
printf '{"order":%s,"inventory":%s}\n' "$b_order" "$b_inventory" > "$OUT/retry-budget-start.json"
budget_tokens() { sed -n 's/.*"tokens":"\([^"]*\)".*/\1/p' <<< "$1"; }
echo "order_retry_budget_start_tokens=$(budget_tokens "$b_order")" >> "$OUT/config.env"
echo "inventory_retry_budget_start_tokens=$(budget_tokens "$b_inventory")" >> "$OUT/config.env"

# 5. 부하. k6가 실패로 끝나면(스크립트 오류 등) 실행도 실패다.
log "부하 ${RATE}/s × ${DURATION_SECONDS}s = ${PLANNED}건"
k6_flags=()
[[ $K6_NO_CONNECTION_REUSE == true ]] && k6_flags+=(--no-connection-reuse)
"$K6" run "${k6_flags[@]}" -e SCENARIO="$SCENARIO" -e BASE_URL="$BASE_URL" -e RATE="$RATE" -e DURATION_SECONDS="$DURATION_SECONDS" \
  -e REQUEST_TIMEOUT="$REQUEST_TIMEOUT" -e MAX_VUS="$MAX_VUS" -e LOOKUP_RATE="$LOOKUP_RATE" -e RUN_ID="$NAME" \
  -e SPIKE_RATE="$SPIKE_RATE" -e SPIKE_FROM_SECONDS="$SPIKE_FROM_SECONDS" -e SPIKE_SECONDS="$SPIKE_SECONDS" \
  -e CLOCK_OFFSET_MS="${clock_ms:-0}" \
  --log-output=file="$OUT/k6-log.txt" --log-format=raw \
  --summary-export="$OUT/k6-summary.json" load/chain.js > "$OUT/k6-stdout.txt" 2>&1 \
  || die "k6가 실패했다 — $OUT/k6-stdout.txt"
grep '^{"type":"answer"' "$OUT/k6-log.txt" > "$OUT/answers.jsonl" || die "정답 줄이 하나도 없다 — $OUT/k6-log.txt"
if ((LOOKUP_RATE > 0)); then
  grep '^{"type":"lookup"' "$OUT/k6-log.txt" > "$OUT/lookups.jsonl" || die "조회 줄이 하나도 없다 — $OUT/k6-log.txt"
fi
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

# 6-1. (task 04) 맞추기 단계: 결과 모름을 같은 키로 다시 보낸다(고르는 규칙은 load/chain.js의 RESEND — 같은 규칙을 jq로 세어 0이면 건너뛴다).
if [[ $RESEND == yes ]]; then
  n_resend=$(jq -s 'group_by(.requestId) | map(select((map(.status >= 200 and .status < 300) | any | not) and (map(.status == 0 or .status == 504) | any))) | length' "$OUT/answers.jsonl")
  echo "resend_candidates=$n_resend" >> "$OUT/config.env"
  : > "$OUT/resends.jsonl"
  if ((n_resend > 0)); then
    log "맞추기 단계: 결과 모름 ${n_resend}건을 같은 키로 다시 보낸다(20/s)"
    "$K6" run "${k6_flags[@]}" -e PHASE=resend -e ANSWERS_FILE="$PWD/$OUT/answers.jsonl" -e RESEND_RATE=20 -e SCENARIO="$SCENARIO" -e BASE_URL="$BASE_URL" \
      -e RATE="$RATE" -e DURATION_SECONDS="$DURATION_SECONDS" -e REQUEST_TIMEOUT="$REQUEST_TIMEOUT" -e MAX_VUS="$MAX_VUS" -e LOOKUP_RATE=0 -e RUN_ID="$NAME" \
      --log-output=file="$OUT/resend-k6-log.txt" --log-format=raw --summary-export="$OUT/resend-k6-summary.json" load/chain.js > "$OUT/resend-k6-stdout.txt" 2>&1 \
      || die "맞추기 단계의 k6가 실패했다 — $OUT/resend-k6-stdout.txt"
    grep '^{"type":"resend"' "$OUT/resend-k6-log.txt" > "$OUT/resends.jsonl" || die "다시 보낸 줄이 하나도 없다 — $OUT/resend-k6-log.txt"
    ! grep -q '"dropped_iterations"' "$OUT/resend-k6-summary.json" || die "맞추기 단계의 k6가 보내지 못한 반복이 있다"
    settled_ms=$(wait_drained "$SETTLE_TIMEOUT_SECONDS")
    echo "settled_after_resend_ms=$settled_ms" >> "$OUT/config.env"
    require_running
  fi
fi

# 7. 세 DB의 상태와 서비스가 센 장애 수를 내보낸다.
psql_csv() { "${COMPOSE[@]}" exec -T postgres psql -U lab -d "$1" -v ON_ERROR_STOP=1 -q -c "\\copy ($2) to stdout with (format csv, header)"; }
psql_csv order_db "select request_id, amount, status from orders order by id" > "$OUT/db/orders.csv"
psql_csv inventory_db "select request_id, status from reservations order by id" > "$OUT/db/reservations.csv"
psql_csv payment_db "select request_id, amount, status from payments order by id" > "$OUT/db/payments.csv"
f_order=$(curl -sf ${SERVICES_HOST}:8081/faults)
f_inventory=$(curl -sf ${SERVICES_HOST}:8082/faults)
f_payment=$(curl -sf ${SERVICES_HOST}:8083/faults)
printf '{"order":%s,"inventory":%s,"payment":%s}\n' "$f_order" "$f_inventory" "$f_payment" > "$OUT/fault-counts.json"
# task 05: 서비스가 전송마다 센 호출 수(GET /transport-counts). 받지 못하면 실패.
t=()
for entry in "${SERVICE_PORTS[@]}"; do
  t+=("$(curl -sf "${SERVICES_HOST}:${entry#*:}/transport-counts")") || die "/transport-counts를 받지 못했다"
done
printf '{"order":%s,"inventory":%s,"payment":%s}\n' "${t[@]}" > "$OUT/transport-counts.json"
# task 04: 키 표, 서비스가 센 키 수와 끊은 수, 시도마다 넣은 실패(재시도 분석을 하지 않는 실행도 대조하려고). 받지 못하면 실패.
# 업무 행의 id와 키 행이 가리키는 업무 행(row_id)은 새 파일로 낸다(R04-2 — 대조 분석이 읽는 db/<업무>.csv와 키 표의 꼴은 바꾸지 않는다).
if [[ $IDEM == yes ]]; then
  for db in order inventory payment; do
    psql_csv "${db}_db" "select key, request_id, amount, state, response_status from idempotency_keys order by key" > "$OUT/db/idempotency-${db}.csv"
    psql_csv "${db}_db" "select key, row_id from idempotency_keys order by key" > "$OUT/db/idempotency-row-ids-${db}.csv"
  done
  psql_csv order_db "select id, request_id from orders order by id" > "$OUT/db/row-ids-order.csv"
  psql_csv inventory_db "select id, request_id from reservations order by id" > "$OUT/db/row-ids-inventory.csv"
  psql_csv payment_db "select id, request_id from payments order by id" > "$OUT/db/row-ids-payment.csv"
  echo "row_ids_exported=yes" >> "$OUT/config.env"
  c=(); d=(); a=()
  for entry in "${SERVICE_PORTS[@]}"; do
    port=${entry#*:}
    c+=("$(curl -sf "${SERVICES_HOST}:${port}/idempotency-counts")") || die "/idempotency-counts를 받지 못했다"
    d+=("$(curl -sf "${SERVICES_HOST}:${port}/drop-faults")") || die "/drop-faults를 받지 못했다"
    a+=("$(curl -sf "${SERVICES_HOST}:${port}/attempt-faults")") || die "/attempt-faults를 받지 못했다"
  done
  printf '{"order":%s,"inventory":%s,"payment":%s}\n' "${c[@]}" > "$OUT/idempotency-counts.json"
  printf '{"order":%s,"inventory":%s,"payment":%s}\n' "${d[@]}" > "$OUT/drop-faults.json"
  printf '{"order":%s,"inventory":%s,"payment":%s}\n' "${a[@]}" > "$OUT/attempt-faults.json"
fi
# task 04-b: 세 서비스가 센 든 수·거절 수(GET /overload). 받지 못하면 실패.
if [[ $OVERLOAD == yes ]]; then
  o=()
  for entry in "${SERVICE_PORTS[@]}"; do
    o+=("$(curl -sf "${SERVICES_HOST}:${entry#*:}/overload")") || die "/overload를 받지 못했다"
  done
  printf '{"order":%s,"inventory":%s,"payment":%s}\n' "${o[@]}" > "$OUT/overload-counts.json"
fi
# 시각 기록과 표본(task 02 — docs/measurement.md 7절). 받지 못하면 실패.
mkdir -p "$OUT/timing"
for entry in "${SERVICE_PORTS[@]}"; do
  svc=${entry%%:*}; port=${entry#*:}
  curl -sf "${SERVICES_HOST}:${port}/timings" > "$OUT/timing/timings-${svc}.json" || die "$svc 의 /timings를 받지 못했다"
  curl -sf "${SERVICES_HOST}:${port}/samples" > "$OUT/timing/samples-${svc}.json" || die "$svc 의 /samples를 받지 못했다"
done
# task 03: 시도 기록(재시도 감싸개)과 서비스가 센 넣은 실패(X-Attempt-Fault). 받지 못하면 실패.
if [[ $RETRY == yes ]]; then
  for entry in "${SERVICE_PORTS[@]}"; do
    svc=${entry%%:*}; port=${entry#*:}
    curl -sf "${SERVICES_HOST}:${port}/attempts" > "$OUT/timing/attempts-${svc}.json" || die "$svc 의 /attempts를 받지 못했다"
  done
  a_order=$(curl -sf ${SERVICES_HOST}:8081/attempt-faults) && a_inventory=$(curl -sf ${SERVICES_HOST}:8082/attempt-faults) && a_payment=$(curl -sf ${SERVICES_HOST}:8083/attempt-faults) \
    || die "/attempt-faults를 받지 못했다"
  printf '{"order":%s,"inventory":%s,"payment":%s}\n' "$a_order" "$a_inventory" "$a_payment" > "$OUT/attempt-faults.json"
fi

# 7-1. (task 06) 시계의 어긋남을 실행 뒤에 한 번 더 잰다(부하·가라앉기·내보내기 뒤, 분석 앞). 분석기가 앞·뒤의 평균을 쓰고,
#      차가 clock_offset_drift_max_ms보다 크면 입력 오류로 낸다(docs/measurement.md 12절).
if [[ -n $SERVICES_SSH ]]; then
  clock_end=$(clock_offset)
  read -r clock_end_ms clock_end_err_ms <<< "$clock_end"
  echo "clock_offset_services_minus_load_ms_end=$clock_end_ms" >> "$OUT/config.env"
  echo "clock_offset_error_ms_end=$clock_end_err_ms" >> "$OUT/config.env"
  log "시계 어긋남: 앞 ${clock_ms}ms, 뒤 ${clock_end_ms}ms"
fi

# 8. 분석(컨테이너 안). 기대 파일이 있으면 실행 기록에 함께 넣고 견준다.
args=(--run "/runs/${NAME}" --planned "$PLANNED_ANSWERS")
if [[ -n ${RECON_EXPECT:-} ]]; then
  cp "$RECON_EXPECT" "$OUT/expect.json"
  args+=(--expect "/runs/${NAME}/expect.json")
elif [[ -f "load/expectations/${SCENARIO}.json" ]]; then
  cp "load/expectations/${SCENARIO}.json" "$OUT/expect.json"
  args+=(--expect "/runs/${NAME}/expect.json")
fi
set +e
"${COMPOSE_LOCAL[@]}" run --rm --no-deps --user "$(id -u):$(id -g)" analyzer "${args[@]}"
rc=$?
set -e
log "대조 분석 종료 코드 $rc"
# 8-1. 전송 대조(task 05 — docs/measurement.md 11절). 모든 시나리오. 대조 분석이 입력 오류(1)면 하지 않는다. 종료 코드는 큰 것.
if [[ $rc != 1 ]]; then
  xargs_=(transport --run "/runs/${NAME}")
  if [[ -n ${TRANSPORT_EXPECT:-} ]]; then
    cp "$TRANSPORT_EXPECT" "$OUT/expect.transport.json"
    xargs_+=(--expect "/runs/${NAME}/expect.transport.json")
  fi
  set +e
  "${COMPOSE_LOCAL[@]}" run --rm --no-deps --user "$(id -u):$(id -g)" analyzer "${xargs_[@]}"
  xrc=$?
  set -e
  log "전송 대조 종료 코드 $xrc"
  ((xrc > rc)) && rc=$xrc
fi
# 9. 시각 지표(task 02). 대조 분석이 입력 오류(1)면 하지 않는다. 종료 코드는 둘 가운데 큰 것.
if [[ $TIMING == yes && $rc != 1 ]]; then
  targs=(timing --run "/runs/${NAME}")
  [[ -n $SLOW_LABEL ]] && targs+=(--slow-label "$SLOW_LABEL")
  if [[ -n ${TIMING_EXPECT:-} ]]; then
    cp "$TIMING_EXPECT" "$OUT/expect.timing.json"
    targs+=(--expect "/runs/${NAME}/expect.timing.json")
  fi
  set +e
  "${COMPOSE_LOCAL[@]}" run --rm --no-deps --user "$(id -u):$(id -g)" analyzer "${targs[@]}"
  trc=$?
  set -e
  log "시각 분석 종료 코드 $trc"
  ((trc > rc)) && rc=$trc
fi
# 10. 재시도 지표(task 03 — docs/measurement.md 8절). 대조 분석이 입력 오류(1)면 하지 않는다. 종료 코드는 큰 것.
if [[ $RETRY == yes && $rc != 1 ]]; then
  rargs=(retry --run "/runs/${NAME}")
  if [[ -n ${RETRY_EXPECT:-} ]]; then
    cp "$RETRY_EXPECT" "$OUT/expect.retry.json"
    rargs+=(--expect "/runs/${NAME}/expect.retry.json")
  fi
  set +e
  "${COMPOSE_LOCAL[@]}" run --rm --no-deps --user "$(id -u):$(id -g)" analyzer "${rargs[@]}"
  rrc=$?
  set -e
  log "재시도 분석 종료 코드 $rrc"
  ((rrc > rc)) && rc=$rrc
fi
# 11. 멱등 키 지표(task 04 — docs/measurement.md 9절). 대조 분석이 입력 오류(1)면 하지 않는다. 종료 코드는 큰 것.
if [[ $IDEM == yes && $rc != 1 ]]; then
  iargs=(idem --run "/runs/${NAME}")
  if [[ -n ${IDEM_EXPECT:-} ]]; then
    cp "$IDEM_EXPECT" "$OUT/expect.idem.json"
    iargs+=(--expect "/runs/${NAME}/expect.idem.json")
  fi
  set +e
  "${COMPOSE_LOCAL[@]}" run --rm --no-deps --user "$(id -u):$(id -g)" analyzer "${iargs[@]}"
  irc=$?
  set -e
  log "멱등 키 분석 종료 코드 $irc"
  ((irc > rc)) && rc=$irc
fi
# 12. 한도 지표(task 04-b — docs/measurement.md 10절). 대조 분석이 입력 오류(1)면 하지 않는다. 종료 코드는 큰 것.
if [[ $OVERLOAD == yes && $rc != 1 ]]; then
  oargs=(overload --run "/runs/${NAME}")
  if [[ -n ${OVERLOAD_EXPECT:-} ]]; then
    cp "$OVERLOAD_EXPECT" "$OUT/expect.overload.json"
    oargs+=(--expect "/runs/${NAME}/expect.overload.json")
  fi
  set +e
  "${COMPOSE_LOCAL[@]}" run --rm --no-deps --user "$(id -u):$(id -g)" analyzer "${oargs[@]}"
  orc=$?
  set -e
  log "한도 분석 종료 코드 $orc"
  ((orc > rc)) && rc=$orc
fi
echo "host_loadavg_end=$(cat /proc/loadavg)" >> "$OUT/config.env"
if [[ -n $SERVICES_SSH ]]; then
  s_load=$(on_services cat /proc/loadavg) || die "서비스 서버의 load average를 읽지 못했다"
  echo "services_loadavg_end=$s_load" >> "$OUT/config.env"
fi
log "기록: $OUT (종료 코드 $rc)"
exit "$rc"
