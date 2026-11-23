#!/usr/bin/env bash
# task 02 결과 표: runs/task-02-index.tsv와 실행마다의 metrics.tsv(analyzer timing의 출력)에서 다시 계산한다.
#   scripts/task02-table.sh <실험 이름> <지표 이름>...
#   예: scripts/task02-table.sh ga edges.inventory->payment.falseTimeoutRatio
# 줄마다: | 조건 | 지표 | r1 | r2 | r3 | 가운데값 | 범위(최소~최대) |
# 버리는 실행(r0)은 넣지 않는다. 종료 코드가 0이 아닌 실행은 값 대신 "실패(<코드>)"로 적고 가운데값·범위에서 뺀다.
# 지표가 없거나 빈 값(null)이면 "없음" — 그 실행은 가운데값·범위에서 뺀다.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/lib.sh

EXPERIMENT=${1:?"실험 이름(ga|na|da|cal)"}
shift
(($# > 0)) || die "지표 이름을 하나 이상 준다"
INDEX=runs/task-02-index.tsv
[[ -f $INDEX ]] || die "$INDEX 가 없다"

conditions=$(awk -F'\t' -v e="$EXPERIMENT" 'NR > 1 && $1 == e { if (!seen[$2]++) print $2 }' "$INDEX")
[[ -n $conditions ]] || die "$INDEX 에 실험 '$EXPERIMENT' 의 줄이 없다"

for metric in "$@"; do
  for c in $conditions; do
    cells=()
    values=()
    while IFS=$'\t' read -r rep run rc; do
      if [[ $rc != 0 ]]; then
        cells+=("실패($rc)")
        continue
      fi
      [[ -f $run/metrics.tsv ]] || die "$run/metrics.tsv 가 없다(종료 코드 0인 실행인데)"
      v=$(awk -F'\t' -v m="$metric" '$1 == m { print $2; found = 1 } END { if (!found) print "__none__" }' "$run/metrics.tsv")
      if [[ $v == __none__ || -z $v ]]; then
        cells+=("없음")
      else
        cells+=("$v")
        values+=("$v")
      fi
    done < <(awk -F'\t' -v e="$EXPERIMENT" -v c="$c" 'NR > 1 && $1 == e && $2 == c && $4 != 0 { print $4 "\t" $5 "\t" $6 }' "$INDEX" | sort -n)
    if ((${#values[@]} > 0)); then
      # 홀수 개면 가운데 값을 적힌 그대로, 짝수 개면 가운데 둘의 평균.
      stats=$(printf '%s\n' "${values[@]}" | sort -g | awk '{ a[NR] = $1 } END {
        if (NR % 2) printf "%s", a[(NR + 1) / 2]; else printf "%.6g", (a[NR / 2] + a[NR / 2 + 1]) / 2
        printf " | %s~%s", a[1], a[NR] }')
    else
      stats="- | -"
    fi
    echo "| $c | \`$metric\` | $(IFS='|'; echo "${cells[*]}" | sed 's/|/ | /g') | $stats |"
  done
done
