# scripts/*.sh 가 source 한다. 이 프로젝트의 compose 파일로만 Docker를 다룬다.
SERVICE_PORTS=(order:8081 inventory:8082 payment:8083)

log() { echo "[$(date +%H:%M:%S)] $*" >&2; }
die() { echo "실패: $*" >&2; exit 1; }

# (task 06) 서비스(세 서비스 + PostgreSQL)가 도는 곳. 주지 않으면 이 컴퓨터 — task 01~05의 동작 그대로.
#   SERVICES_SSH   서비스 서버의 ssh 목적지(<계정>@<주소>). 주면 Compose·docker·ss·psql을 그 서버의 ~/communication-reliability-lab 에서
#                  ssh로 부르고, 서비스의 HTTP 주소는 그 목적지의 주소 부분이다. 키는 이 컴퓨터에 두지 않는다 — ssh agent 전달(-A)로 받는다.
#                  비밀번호를 묻지 않게 BatchMode(키를 못 쓰면 바로 실패). 주소는 커밋하는 파일에 적지 않는다 — 환경 변수로만 준다.
#   SERVICES_HOST  서비스의 HTTP 주소. 줄 필요는 없다 — 주면 SERVICES_SSH의 주소와 같아야 하고(다르면 한 서버의 서비스를 HTTP로 재며
#                  다른 서버의 Docker를 보는 것이라 멈춘다), SERVICES_SSH 없이 localhost가 아닌 값을 주면 멈춘다.
# 분석기는 늘 이 컴퓨터의 Docker에서 돈다(실행 기록 runs/가 여기에 있다) — COMPOSE_LOCAL.
SERVICES_SSH=${SERVICES_SSH:-}
SERVICES_DIR=communication-reliability-lab # 서비스 서버의 작업 폴더(홈 아래) — scripts/servers-sync.sh가 코드를 보내는 곳
if [[ -n $SERVICES_SSH ]]; then
  [[ $SERVICES_SSH == *@* && -n ${SERVICES_SSH#*@} ]] || die "SERVICES_SSH '$SERVICES_SSH' 는 <계정>@<주소> 꼴이어야 한다"
  [[ -z ${SERVICES_HOST:-} || $SERVICES_HOST == "${SERVICES_SSH#*@}" ]] \
    || die "SERVICES_HOST($SERVICES_HOST)가 SERVICES_SSH의 주소(${SERVICES_SSH#*@})와 다르다 — 서비스의 HTTP와 Docker가 한 서버여야 한다"
  SERVICES_HOST=${SERVICES_SSH#*@}
  # 서비스 서버에서 명령 하나. 인자마다 %q로 감싸 원격 셸이 다시 나누지 않게 한다(psql의 SQL 등). 표준 입력은 넘기지 않는다(-n).
  on_services() { ssh -n -o BatchMode=yes -o ConnectTimeout=10 "$SERVICES_SSH" "cd ~/$SERVICES_DIR && $(printf '%q ' "$@")"; }
  ON_SERVICES=(on_services)
  # 조건은 compose.yaml이 환경 변수(${VAR:-기본})로 읽는다. ssh는 환경을 넘기지 않으므로 이 컴퓨터에서 내보낸(export) 그 변수들만 골라
  # 서비스 서버의 docker compose에 그대로 준다 — 이 컴퓨터에서 compose를 부를 때와 같은 값이 걸린다(어긋나면 run.sh의 /settings 대조가 잡는다).
  mapfile -t COMPOSE_VARS < <(grep -o '\${[A-Z_][A-Z0-9_]*' compose.yaml | cut -c3- | sort -u)
  services_compose() {
    local v val env=()
    for v in "${COMPOSE_VARS[@]}"; do val=$(printenv "$v") && env+=("$v=$val"); done
    # BUILDX_NO_DEFAULT_ATTESTATIONS: 서비스 서버의 Docker는 containerd 이미지 저장소라 이미지 id가 manifest list의 다이제스트이고,
    # BuildKit이 기본으로 붙이는 출처 증명이 짓기마다 그 다이제스트를 바꾼다 — 캐시만으로 다시 지어도 run.sh의
    # "떠 있는 이미지 = 방금 지은 이미지" 대조가 늘 실패한다(2026-11-26 확인). 증명을 붙이지 않으면 같은 입력에서 같은 id다.
    on_services env BUILDX_NO_DEFAULT_ATTESTATIONS=1 "${env[@]}" docker compose -f compose.yaml -p communication-reliability-lab "$@"
  }
  COMPOSE=(services_compose)
else
  [[ ${SERVICES_HOST:-localhost} == localhost ]] || die "SERVICES_HOST '$SERVICES_HOST' 는 SERVICES_SSH와 함께만 준다(서비스의 Docker가 이 컴퓨터에 있으면 주소는 localhost)"
  SERVICES_HOST=localhost
  ON_SERVICES=()
  COMPOSE=(docker compose -f compose.yaml -p communication-reliability-lab)
fi
DOCKER=("${ON_SERVICES[@]}" docker)
COMPOSE_LOCAL=(docker compose -f compose.yaml -p communication-reliability-lab)

# 이미지 짓기: 세 서비스는 서비스가 도는 곳에서, 분석기는 이 컴퓨터에서. 같은 곳이면 한 번에(task 01~05 그대로).
build_images() {
  if [[ -z $SERVICES_SSH ]]; then
    "${COMPOSE[@]}" --profile tools build
  else
    "${COMPOSE[@]}" build order inventory payment && "${COMPOSE_LOCAL[@]}" --profile tools build analyzer
  fi
}

# 이 프로젝트의 DB에만 SQL을 보낸다. ON_ERROR_STOP으로 SQL 오류를 종료 코드로 드러낸다.
psql_lab() {
  "${COMPOSE[@]}" exec -T postgres psql -U lab -d "$1" -v ON_ERROR_STOP=1 -qAt -c "$2"
}

# 세 서비스의 health가 모두 UP일 때까지 기다린다(최대 120초). 못 뜨면 실패.
# 준비됨 = HTTP 200이고 본문의 최상위 status가 UP. 구성 요소의 "UP"만으로는 준비됨이 아니다 —
# Actuator는 전체가 DOWN이면 503을 내지만, 본문 어딘가의 "UP"을 찾는 검사는 그것을 놓친다.
# 최상위 status: Jackson 3은 키를 사전순으로 내므로 최상위 객체의 마지막 키가 status다 → 본문이 ,"status":"UP"} 로 끝난다
# (구성 요소의 status는 그 뒤에 }가 하나 더 붙는다).
wait_ready() {
  local entry port out code body ok
  for _ in $(seq 1 60); do
    ok=0
    for entry in "${SERVICE_PORTS[@]}"; do
      port=${entry#*:}
      out=$(curl -s --max-time 2 -w '\n%{http_code}' "${SERVICES_HOST}:${port}/actuator/health" || true)
      code=${out##*$'\n'}
      body=${out%$'\n'*}
      [[ $code == 200 && ( $body == *',"status":"UP"}' || $body == '{"status":"UP"}' ) ]] && ok=$((ok + 1))
    done
    [[ $ok == "${#SERVICE_PORTS[@]}" ]] && return 0
    sleep 2
  done
  "${COMPOSE[@]}" ps -a >&2
  die "세 서비스가 120초 안에 UP이 되지 않았다"
}

# 네 컨테이너(postgres + 세 서비스)가 모두 running인지. 하나라도 죽었으면 실패.
require_running() {
  local running
  running=$("${COMPOSE[@]}" ps --status running --services | sort | tr '\n' ' ')
  [[ $running == "inventory order payment postgres " ]] || {
    "${COMPOSE[@]}" ps -a >&2
    die "떠 있어야 할 컨테이너가 없다 (running: ${running:-없음})"
  }
}

# 부하가 끝난 뒤 세 서비스의 처리 중인 요청 수(GET /inflight)가 모두 0이 될 때까지 기다린다.
# 위 층부터 차례로 읽는다: 위 층이 0이면 그 뒤로 아래 층에 새 호출이 생기지 않으므로, 한 바퀴에 셋이 모두 0이면 끝난 것이다.
# $1초 안에 0이 되지 않으면 실패 — 아직 끝나지 않은 처리의 행을 "처리 안 됨"으로 내보내지 않게.
# 기다린 시간(ms)을 표준 출력에 낸다.
wait_drained() {
  local timeout_s=$1 start now entry body n busy
  start=$(date +%s%N)
  while true; do
    busy=""
    for entry in "${SERVICE_PORTS[@]}"; do
      body=$(curl -sf --max-time 2 "${SERVICES_HOST}:${entry#*:}/inflight") || die "${entry%%:*}의 /inflight를 읽지 못했다"
      n=$(sed -n 's/^{"inflight":\([0-9][0-9]*\)}$/\1/p' <<< "$body")
      [[ -n $n ]] || die "${entry%%:*}의 /inflight 응답이 꼴에 맞지 않는다: $body"
      [[ $n == 0 ]] || busy+="${entry%%:*}=${n} "
    done
    now=$(date +%s%N)
    [[ -z $busy ]] && { echo $(((now - start) / 1000000)); return 0; }
    (((now - start) / 1000000000 >= timeout_s)) && die "가라앉기 ${timeout_s}s 안에 처리 중인 요청이 0이 되지 않았다(${busy% }) — DB를 내보내지 않고 멈춘다"
    sleep 0.2
  done
}

# ── (task 06) 서버에서 잴 때만 — 두 서버의 조용함과 시계. 규칙과 까닭: docs/experiments/task-06-design.md 2절 ──

# 두 서버(이 컴퓨터 = 부하 도구, 서비스 서버)의 1분 load average가 모두 "코어 수 × $1" 이하가 될 때까지 10초마다 본다.
# $2초 안에 그렇게 되지 않으면 실패(조용히 재지 않는다). 지나면 표준 출력에 "<기다린 초> <이 컴퓨터의 값> <서비스 서버의 값>".
wait_quiet() { # <코어당 문턱> <최대 기다림 초>
  local per_core=$1 max_wait=$2 start n_here n_svc l_here l_svc
  n_here=$(nproc) && n_svc=$(on_services nproc) || die "코어 수를 읽지 못했다"
  start=$(date +%s)
  while true; do
    l_here=$(cut -d' ' -f1 /proc/loadavg) && l_svc=$(on_services cut -d' ' -f1 /proc/loadavg) || die "load average를 읽지 못했다"
    if awk -v a="$l_here" -v na="$n_here" -v b="$l_svc" -v nb="$n_svc" -v m="$per_core" 'BEGIN { exit !(a <= m * na && b <= m * nb) }'; then
      echo "$(($(date +%s) - start)) $l_here $l_svc"
      return 0
    fi
    (($(date +%s) - start >= max_wait)) \
      && die "load average가 ${max_wait}s 안에 문턱(코어당 $per_core — 이 컴퓨터 $(awk -v m="$per_core" -v n="$n_here" 'BEGIN { print m * n }'), 서비스 서버 $(awk -v m="$per_core" -v n="$n_svc" 'BEGIN { print m * n }')) 아래로 내려오지 않았다(이 컴퓨터 $l_here, 서비스 서버 $l_svc) — 재지 않고 멈춘다"
    log "load average가 문턱 위 — 기다린다(이 컴퓨터 $l_here/${n_here}코어, 서비스 서버 $l_svc/${n_svc}코어)"
    sleep 10
  done
}

# 서비스 서버의 시계 − 이 컴퓨터의 시계(ms)와 그 오차의 상한(±ms). ssh 연결 하나를 열어 두고 20번 묻는다:
# 이쪽 시계로 보낸 때 t0와 받은 때 t1 사이 어딘가에서 저쪽이 시계를 읽었으므로, 어긋남 = 저쪽 값 − (t0 + t1)/2이고 참값과의 차는 (t1 − t0)/2 이하다.
# 왕복이 가장 짧은 한 번을 쓴다. 표준 출력에 "<어긋남 ms> <오차 ms>"(소수 셋째 자리).
clock_offset() {
  local t0 t1 r i best_rtt=-1 best_off=0 rtt
  coproc CLOCK { ssh -o BatchMode=yes -o ConnectTimeout=10 "$SERVICES_SSH" 'while read -r _; do date +%s%N; done'; }
  for i in $(seq 1 20); do
    t0=${EPOCHREALTIME/[.,]/}
    echo >&"${CLOCK[1]}"
    read -r -t 15 r <&"${CLOCK[0]}" || die "서비스 서버의 시계를 읽지 못했다"
    t1=${EPOCHREALTIME/[.,]/}
    [[ $r =~ ^[0-9]{19}$ ]] || die "서비스 서버의 시계 값이 꼴에 맞지 않는다: $r"
    rtt=$((t1 - t0))
    if ((best_rtt < 0 || rtt < best_rtt)); then best_rtt=$rtt; best_off=$((r / 1000 - (t0 + t1) / 2)); fi
  done
  eval "exec ${CLOCK[1]}>&-"
  wait "$CLOCK_PID" || true
  awk -v o="$best_off" -v r="$best_rtt" 'BEGIN { printf "%.3f %.3f\n", o / 1000, r / 2000 }'
}
