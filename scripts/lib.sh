# scripts/*.sh 가 source 한다. 이 프로젝트의 compose 파일로만 Docker를 다룬다.
COMPOSE=(docker compose -f compose.yaml -p communication-reliability-lab)
SERVICE_PORTS=(order:8081 inventory:8082 payment:8083)

log() { echo "[$(date +%H:%M:%S)] $*" >&2; }
die() { echo "실패: $*" >&2; exit 1; }

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
      out=$(curl -s --max-time 2 -w '\n%{http_code}' "localhost:${port}/actuator/health" || true)
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
      body=$(curl -sf --max-time 2 "localhost:${entry#*:}/inflight") || die "${entry%%:*}의 /inflight를 읽지 못했다"
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
