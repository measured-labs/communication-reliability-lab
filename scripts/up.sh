#!/usr/bin/env bash
# 이미지를 짓고 DB와 세 서비스를 띄운다. 호스트 포트 8081~8083을 쓴다.
# (task 06) SERVICES_SSH를 주면 서비스 서버에서 짓고 띄운다(포트 확인도 그 서버에서) — scripts/lib.sh 머리.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/lib.sh

# 명령(원격이면 ssh)이 실패하면 멈춘다 — 빈 출력을 "떠 있는 것이 없다"·"포트가 비어 있다"로 읽지 않게(R06-6).
ours=$("${COMPOSE[@]}" ps -q) || die "이 프로젝트의 컨테이너 목록을 읽지 못했다(docker compose ps)"
if [[ -z $ours ]]; then
  for entry in "${SERVICE_PORTS[@]}"; do
    port=${entry#*:}
    listening=$("${ON_SERVICES[@]}" ss -ltnH "sport = :${port}") || die "포트 ${port}를 확인하지 못했다(ss)"
    [[ -z $listening ]] || die "호스트 포트 ${port}를 이미 누가 쓴다 — 다른 프로젝트의 것을 건드리지 않으려고 멈춘다"
  done
fi

log "이미지 짓기"
build_images
log "띄우기"
"${COMPOSE[@]}" up -d postgres order inventory payment
wait_ready
require_running
log "준비됨: order :8081 → inventory :8082 → payment :8083"
