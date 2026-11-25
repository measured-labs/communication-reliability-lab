#!/usr/bin/env bash
# task 05 결과 표: 색인(runs/task-05-index.tsv)과 실행마다의 보고에서 다시 계산한다.
#   scripts/task05-table.sh <실험 이름> <지표 이름>...
#   예: scripts/task05-table.sh exp-P2 waste.payment.wastedMsSum recon.layers.payment.unknownProcessed transport.deadline.paymentAfterInventoryOverSlack
# 지표 이름의 앞: `recon.` = report.json의 reconciliation(대조 분석 — 나머지는 점으로 이은 자리), `transport.` = metrics-transport.tsv(전송 대조),
#   그 밖 = metrics.tsv(시각 분석).
# 줄마다: | 조건 | 지표 | r1 | r2 | r3 | 가운데값 | 범위(최소~최대) | — 조건은 색인의 차례(처음 나온 차례).
# 버리는 실행(r0)은 넣지 않는다. 종료 코드가 0이 아닌 실행은 값 대신 "실패(<코드>)"로 적고 가운데값·범위에서 뺀다.
# 지표가 없거나 빈 값(null)이면 "없음" — 그 실행은 가운데값·범위에서 뺀다.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/lib.sh

EXPERIMENT=${1:?"실험 이름(exp-P1|exp-P2|cal)"}
shift
(($# > 0)) || die "지표 이름을 하나 이상 준다"
INDEX=${INDEX:-runs/task-05-index.tsv}
[[ -f $INDEX ]] || die "$INDEX 가 없다"
conditions=$(awk -F'\t' -v e="$EXPERIMENT" 'NR > 1 && $1 == e { if (!seen[$2]++) print $2 }' "$INDEX")
[[ -n $conditions ]] || die "$INDEX 에 실험 '$EXPERIMENT' 의 줄이 없다"

value() { # <실행 폴더> <지표> → 값(없으면 __none__)
  local run=$1 metric=$2
  case "$metric" in
    recon.*)
      jq -r --arg p "${metric#recon.}" '.reconciliation | getpath($p | split(".")) | if . == null then "__none__" else tostring end' "$run/report.json" ;;
    transport.*)
      awk -F'\t' -v m="${metric#transport.}" '$1 == m { print $2; found = 1 } END { if (!found) print "__none__" }' "$run/metrics-transport.tsv" ;;
    *)
      awk -F'\t' -v m="$metric" '$1 == m { print $2; found = 1 } END { if (!found) print "__none__" }' "$run/metrics.tsv" ;;
  esac
}

for metric in "$@"; do
  for c in $conditions; do
    cells=()
    values=()
    while IFS=$'\t' read -r rep run rc; do
      if [[ $rc != 0 ]]; then
        cells+=("실패($rc)")
        continue
      fi
      v=$(value "$run" "$metric")
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
