#!/usr/bin/env bash
# 테스트를 컨테이너 안(JDK 25)에서 돌린다. 실패하면 빌드가 실패한다.
set -euo pipefail
cd "$(dirname "$0")/.."
docker build --target test -t communication-reliability-lab-test .
