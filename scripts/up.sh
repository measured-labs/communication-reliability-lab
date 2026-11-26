#!/usr/bin/env bash
# 이미지를 짓고 DB와 세 서비스를 띄운다. 호스트 포트 8081~8083을 쓴다.
# (task 06) SERVICES_SSH를 주면 서비스 서버에서 짓고 띄운다(포트 확인도 그 서버에서) — scripts/lib.sh 머리.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/lib.sh

if [[ -z $("${COMPOSE[@]}" ps -q) ]]; then
  for entry in "${SERVICE_PORTS[@]}"; do
    port=${entry#*:}
    if "${ON_SERVICES[@]}" ss -ltnH "sport = :${port}" | grep -q .; then
      die "호스트 포트 ${port}를 이미 누가 쓴다 — 다른 프로젝트의 것을 건드리지 않으려고 멈춘다"
    fi
  done
fi

log "이미지 짓기"
build_images
log "띄우기"
"${COMPOSE[@]}" up -d postgres order inventory payment
wait_ready
require_running
log "준비됨: order :8081 → inventory :8082 → payment :8083"
