#!/usr/bin/env bash
# 테스트를 컨테이너 안(JDK 25)에서 돌리고, 결과 XML에서 돈 테스트 수를 센다.
# 실패: Gradle이 실패함, 실패한 테스트가 있음, 또는 돈 테스트가 0개(테스트가 없어 조용히 통과하지 않게).
# 마지막 줄: 테스트 <모두>개 — 통과 <n>, 실패 <n>, 건너뜀 <n>
set -euo pipefail
cd "$(dirname "$0")/.."

IMAGE=communication-reliability-lab-test
docker build --target test -t "$IMAGE" .

results=$(mktemp -d)
trap 'rm -rf "$results"; [[ -n ${cid:-} ]] && docker rm "$cid" > /dev/null' EXIT
cid=$(docker create "$IMAGE")
docker cp "$cid:/test-results/." "$results/" > /dev/null
docker cp "$cid:/test-exit-code" "$results/exit-code" > /dev/null
gradle_exit=$(tr -d '[:space:]' < "$results/exit-code")

# Gradle(JUnit XML)의 <testsuite tests=".." skipped=".." failures=".." errors=".."> 를 더한다.
attr() { grep -ho "<testsuite [^>]*" "$results"/*.xml 2>/dev/null | sed -n "s/.* $1=\"\([0-9]*\)\".*/\1/p" | awk '{s+=$1} END {print s+0}'; }
total=$(attr tests); skipped=$(attr skipped); failures=$(attr failures); errors=$(attr errors)
failed=$((failures + errors))
passed=$((total - skipped - failed))

if ((failed > 0)); then
  echo "실패한 테스트:"
  grep -l "<failure\|<error" "$results"/*.xml | while read -r f; do
    awk '/<testcase /{match($0, /name="[^"]*"/); n=substr($0, RSTART+6, RLENGTH-7); c=$0} /<failure|<error/{print "  - " n}' "$f"
  done
fi
echo "테스트 ${total}개 — 통과 ${passed}, 실패 ${failed}, 건너뜀 ${skipped}"
((total > 0)) || { echo "실패: 돈 테스트가 0개다" >&2; exit 1; }
((failed == 0)) || exit 1
[[ $gradle_exit == 0 ]] || { echo "실패: Gradle 종료 코드 $gradle_exit" >&2; exit 1; }
