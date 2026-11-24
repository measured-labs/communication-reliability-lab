#!/usr/bin/env bash
# task 04의 커밋한 실행 기록에서 분석을 다시 돌려 커밋한 보고와 견준다.
#   scripts/task04-recompute.sh <실행 폴더>...
# raw.tar.gz를 runs/.recompute/<이름>/에 풀고, 대조 분석(그리고 보고가 있는 것만: analyzer retry, analyzer timing, analyzer idem)을
# 그 실행과 같은 인자로 돌린 뒤 보고(report.json, 있으면 report-retry.json·metrics-retry.tsv·report-timing.json·metrics.tsv·
# report-idem.json·metrics-idem.tsv)가 커밋한 것과 바이트까지 같은지 견준다. 분석기 이미지는 지금의 코드로 짓는다.
# 대조 분석의 --planned는 config.env의 planned_answers(없으면 planned_requests — task 01~03의 실행).
# analyzer idem: config.env에 row_ids_exported=yes가 없는 실행(row_id를 내보내기 전 — task 04의 첫 62개)에는 --no-row-ids를 준다
#   (그 실행의 보고에는 layers.<L>.keyLinks가 없다 — docs/measurement.md 9-4).
# 바이트가 다른 지표 파일(.tsv)은 지표 이름으로 맞춰 "값이 다른 지표 / 더한 지표 / 뺀 지표"를 적는다(규칙을 바꾼 뒤 다시 만들 때 무엇이 바뀌었나 보려고).
# 환경 변수 WRITE=yes: 다시 만든 보고를 실행 폴더의 커밋한 보고 위에 쓴다(분석기 규칙을 바꾼 뒤 모든 실행의 보고를 다시 만들 때 —
#   값이 다른 지표가 있어도 쓰고 그 줄을 적는다. 바뀐 값이 규칙을 바꾼 지표뿐인지는 이 출력을 읽어 확인한다).
# 종료 코드: 모든 실행에서 바이트가 같으면 0, 아니면 1(WRITE=yes면: 값이 다른 지표가 없으면 0). 분석기의 종료 코드도 실행마다 적는다.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/lib.sh

(($# > 0)) || die "실행 폴더를 하나 이상 준다"
WRITE=${WRITE:-no}
[[ $WRITE == yes || $WRITE == no ]] || die "WRITE '$WRITE' 는 yes|no"
"${COMPOSE[@]}" --profile tools build analyzer > /dev/null
bad=0
analyze() { set +e; "${COMPOSE[@]}" run --rm --no-deps --user "$(id -u):$(id -g)" analyzer "$@" > /dev/null 2>&1; local rc=$?; set -e; echo "$rc"; }
# 지표 이름으로 맞춘 차이: 값이 다른 것, 더한 것(새 보고에만), 뺀 것(옛 보고에만)
tsv_diff() { # <옛> <새>
  awk -F'\t' 'NR == FNR { old[$1] = $2; next } { new[$1] = $2 }
    END {
      for (k in new) if (k in old) { if (old[k] != new[k]) { changed++; printf "    값이 다름: %s %s → %s\n", k, old[k], new[k] } } else added = added " " k
      for (k in old) if (!(k in new)) removed = removed " " k
      if (added != "") print "    더한 지표:" added
      if (removed != "") print "    뺀 지표:" removed
      exit (changed > 0)
    }' "$1" "$2"
}
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
  codes="대조 $(analyze "${args[@]}")"
  files=(report.json)
  if [[ -f $run/report-retry.json ]]; then
    rargs=(retry --run "/runs/.recompute/${name}")
    [[ -f $tmp/expect.retry.json ]] && rargs+=(--expect "/runs/.recompute/${name}/expect.retry.json")
    codes+=", 재시도 $(analyze "${rargs[@]}")"
    files+=(report-retry.json metrics-retry.tsv)
  fi
  if [[ -f $run/report-timing.json ]]; then
    targs=(timing --run "/runs/.recompute/${name}")
    slow=$(conf slow_label); [[ -n $slow ]] && targs+=(--slow-label "$slow")
    [[ -f $tmp/expect.timing.json ]] && targs+=(--expect "/runs/.recompute/${name}/expect.timing.json")
    codes+=", 시각 $(analyze "${targs[@]}")"
    files+=(report-timing.json metrics.tsv)
  fi
  if [[ -f $run/report-idem.json ]]; then
    iargs=(idem --run "/runs/.recompute/${name}")
    [[ -f $tmp/expect.idem.json ]] && iargs+=(--expect "/runs/.recompute/${name}/expect.idem.json")
    [[ $(conf row_ids_exported) == yes ]] || iargs+=(--no-row-ids)
    codes+=", 멱등 키 $(analyze "${iargs[@]}")"
    files+=(report-idem.json metrics-idem.tsv)
  fi
  same=yes; valuesChanged=no
  for f in "${files[@]}"; do
    [[ -f $tmp/$f ]] || { echo "  없음(분석이 보고를 쓰지 않았다): $f"; same=no; valuesChanged=yes; continue; }
    sed -i "s#/runs/.recompute/${name}#/runs/${name}#g" "$tmp/$f"
    if ! diff -q "$tmp/$f" "$run/$f" > /dev/null; then
      same=no; echo "  다름: $f"
      if [[ $f == *.tsv ]]; then tsv_diff "$run/$f" "$tmp/$f" || valuesChanged=yes; fi
    fi
  done
  if [[ $WRITE == yes ]]; then
    for f in "${files[@]}"; do [[ -f $tmp/$f ]] && cp "$tmp/$f" "$run/$f"; done
  fi
  echo "$name: 분석 종료 코드($codes), 커밋한 보고와 바이트가 같음 $same, 값이 다른 지표 $valuesChanged$([[ $WRITE == yes ]] && echo ", 다시 만든 보고를 씀")"
  if [[ $WRITE == yes ]]; then [[ $valuesChanged == no ]] || bad=1; else [[ $same == yes ]] || bad=1; fi
done
exit "$bad"
