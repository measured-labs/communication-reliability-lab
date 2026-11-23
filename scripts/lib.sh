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
wait_ready() {
  local entry port body ok
  for _ in $(seq 1 60); do
    ok=0
    for entry in "${SERVICE_PORTS[@]}"; do
      port=${entry#*:}
      body=$(curl -s --max-time 2 "localhost:${port}/actuator/health" || true)
      [[ $body == *'"status":"UP"'* ]] && ok=$((ok + 1))
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
