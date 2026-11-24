#!/usr/bin/env bash
# task 04의 커밋한 실행 기록에서 분석을 다시 돌려 커밋한 보고와 같은지 본다.
#   scripts/task04-recompute.sh <실행 폴더>...
# raw.tar.gz를 runs/.recompute/<이름>/에 풀고, 대조 분석(그리고 보고가 있는 것만: analyzer retry, analyzer timing, analyzer idem)을
# 그 실행과 같은 인자로 돌린 뒤 보고(report.json, 있으면 report-retry.json·metrics-retry.tsv·report-timing.json·metrics.tsv·
# report-idem.json·metrics-idem.tsv)가 커밋한 것과 바이트까지 같은지 견준다. 하나라도 다르면 종료 코드 1. 분석기 이미지는 지금의 코드로 짓는다.
# 대조 분석의 --planned는 config.env의 planned_answers(없으면 planned_requests — task 01~03의 실행).
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/lib.sh

(($# > 0)) || die "실행 폴더를 하나 이상 준다"
"${COMPOSE[@]}" --profile tools build analyzer > /dev/null
bad=0
for run in "$@"; do
  run=${run%/}
  name=$(basename "$run")
  [[ -f $run/raw.tar.gz && -f $run/config.env ]] || die "$run 에 raw.tar.gz나 config.env가 없다"
  tmp=runs/.recompute/$name
  rm -rf "$tmp"; mkdir -p "$tmp"
  tar -xzf "$run/raw.tar.gz" -C "$tmp"
  conf() { sed -n "s/^$1=//p" "$run/config.env"; }
  planned=$(conf planned_answers); [[ -n $planned ]] || planned=$(conf planned_requests)
  args=(--run "/runs/.recompute/${name}" --planned "$planned")
  [[ -f $tmp/expect.json ]] && args+=(--expect "/runs/.recompute/${name}/expect.json")
  set +e
  "${COMPOSE[@]}" run --rm --no-deps --user "$(id -u):$(id -g)" analyzer "${args[@]}" > /dev/null 2>&1
  rc=$?
  set -e
  files=(report.json)
  if [[ -f $run/report-retry.json ]]; then
    rargs=(retry --run "/runs/.recompute/${name}")
    [[ -f $tmp/expect.retry.json ]] && rargs+=(--expect "/runs/.recompute/${name}/expect.retry.json")
    set +e; "${COMPOSE[@]}" run --rm --no-deps --user "$(id -u):$(id -g)" analyzer "${rargs[@]}" > /dev/null 2>&1; set -e
    files+=(report-retry.json metrics-retry.tsv)
  fi
  if [[ -f $run/report-timing.json ]]; then
    targs=(timing --run "/runs/.recompute/${name}")
    slow=$(conf slow_label); [[ -n $slow ]] && targs+=(--slow-label "$slow")
    [[ -f $tmp/expect.timing.json ]] && targs+=(--expect "/runs/.recompute/${name}/expect.timing.json")
    set +e; "${COMPOSE[@]}" run --rm --no-deps --user "$(id -u):$(id -g)" analyzer "${targs[@]}" > /dev/null 2>&1; set -e
    files+=(report-timing.json metrics.tsv)
  fi
  if [[ -f $run/report-idem.json ]]; then
    iargs=(idem --run "/runs/.recompute/${name}")
    [[ -f $tmp/expect.idem.json ]] && iargs+=(--expect "/runs/.recompute/${name}/expect.idem.json")
    set +e; "${COMPOSE[@]}" run --rm --no-deps --user "$(id -u):$(id -g)" analyzer "${iargs[@]}" > /dev/null 2>&1; set -e
    files+=(report-idem.json metrics-idem.tsv)
  fi
  same=yes
  for f in "${files[@]}"; do
    if ! diff -q <(sed "s#/runs/.recompute/${name}#/runs/${name}#g" "$tmp/$f" 2>/dev/null) "$run/$f" > /dev/null; then same=no; echo "  다름: $f"; fi
  done
  echo "$name: 대조 분석 $rc, 커밋한 보고와 같음 $same"
  [[ $same == yes ]] || bad=1
done
exit "$bad"
