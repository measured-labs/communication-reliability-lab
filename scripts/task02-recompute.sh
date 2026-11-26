#!/usr/bin/env bash
# task 02의 커밋한 실행 기록에서 분석을 다시 돌려 커밋한 보고와 같은지 본다.
#   scripts/task02-recompute.sh <실행 폴더>...     (예: runs/false-timeout-20261124T024512-r1)
# 실행 폴더마다 raw.tar.gz(정답·조회·DB 상태·장애 수·설정·시각 기록·표본·기대 파일 — scripts/task02-pack.sh가 만든다)를
# runs/.recompute/<이름>/ 에 풀고, 대조 분석과 시각 분석을 그 실행과 같은 인자로 돌린 뒤
# report.json·report-timing.json·metrics.tsv가 커밋한 것과 바이트까지 같은지 견준다. 하나라도 다르면 종료 코드 1.
# 분석기 이미지는 지금의 코드로 짓는다(docker compose build analyzer).
# WRITE=yes 를 주면 견주지 않고, 다시 돈 보고로 실행 폴더의 보고(report.json·report.md·report-timing.json·metrics.tsv)를 바꾼다 —
# 분석기의 지표를 고친 뒤 커밋한 실행들을 지금의 분석기로 다시 분석할 때(그 뒤 WRITE 없이 다시 돌려 같음을 확인한다).
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/lib.sh

(($# > 0)) || die "실행 폴더를 하나 이상 준다"
"${COMPOSE_LOCAL[@]}" --profile tools build analyzer > /dev/null
bad=0
for run in "$@"; do
  run=${run%/}
  name=$(basename "$run")
  [[ -f $run/raw.tar.gz && -f $run/config.env ]] || die "$run 에 raw.tar.gz나 config.env가 없다"
  tmp=runs/.recompute/$name
  rm -rf "$tmp"
  mkdir -p "$tmp"
  tar -xzf "$run/raw.tar.gz" -C "$tmp"
  conf() { sed -n "s/^$1=//p" "$run/config.env"; }
  planned=$(conf planned_requests)
  slow=$(conf slow_label)
  args=(--run "/runs/.recompute/${name}" --planned "$planned")
  [[ -f $tmp/expect.json ]] && args+=(--expect "/runs/.recompute/${name}/expect.json")
  targs=(timing --run "/runs/.recompute/${name}")
  [[ -n $slow ]] && targs+=(--slow-label "$slow")
  [[ -f $tmp/expect.timing.json ]] && targs+=(--expect "/runs/.recompute/${name}/expect.timing.json")
  set +e
  "${COMPOSE_LOCAL[@]}" run --rm --no-deps --user "$(id -u):$(id -g)" analyzer "${args[@]}" > /dev/null 2>&1
  rc=$?
  "${COMPOSE_LOCAL[@]}" run --rm --no-deps --user "$(id -u):$(id -g)" analyzer "${targs[@]}" > /dev/null 2>&1
  trc=$?
  set -e
  if [[ ${WRITE:-no} == yes ]]; then
    for f in report.json report.md report-timing.json metrics.tsv; do
      [[ -f $tmp/$f ]] || die "$name: 다시 돈 분석이 $f 를 쓰지 않았다(대조 분석 $rc, 시각 분석 $trc)"
      sed "s#/runs/.recompute/${name}#/runs/${name}#g" "$tmp/$f" > "$run/$f"
    done
    echo "$name: 대조 분석 $rc, 시각 분석 $trc, 보고를 다시 씀"
    continue
  fi
  same=yes
  for f in report.json report-timing.json metrics.tsv; do
    # 보고 안의 경로는 다시 돈 위치(/runs/.recompute/…)를 가리키므로 그 부분만 원래 위치로 바꿔 견준다.
    if ! diff -q <(sed "s#/runs/.recompute/${name}#/runs/${name}#g" "$tmp/$f" 2>/dev/null) "$run/$f" > /dev/null; then
      same=no
    fi
  done
  echo "$name: 대조 분석 $rc, 시각 분석 $trc, 커밋한 보고와 같음 $same"
  [[ $same == yes ]] || bad=1
done
exit "$bad"
