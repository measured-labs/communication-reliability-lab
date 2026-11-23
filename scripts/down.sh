#!/usr/bin/env bash
# 이 프로젝트의 컨테이너·네트워크·볼륨(pgdata)만 내린다. 이미지는 남긴다.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/lib.sh
"${COMPOSE[@]}" --profile tools down -v --remove-orphans
