#!/usr/bin/env bash
# task 06(R04-9): 실험 조건마다의 기대 파일(load/expectations/t0*-<조건>[.<분석>].json)을 지난 노트북 실행에 걸어 본다.
#   scripts/task06-expect-check.sh [<키 또는 앞 조각>...]   (예: t04 · t03-da-3S · t04b-pl-exp — 주지 않으면 모두)
# 대상: 다섯 색인(runs/task-02·03·04·04b·05-index.tsv)의 실험 줄 — 반복 0(버리는 실행)과 비고가 "대체됨"으로 시작하는 줄은 뺀다.
#   실험 이름: task 02 ga·na·da, task 03 ga·na·da·ra, task 04 ga·na·da·ra, task 04-b ga·na·pl, task 05 exp-P1·exp-P2·exp2-P1·exp2-P2
#   (보정 cal·base·cal2, keylinks, chk, diag, smoke는 넣지 않는다). 모두 돌리면 조건 75개(키 71 — task 05의 exp와 exp2의 P1-G1·P1-G3·P2-G1·P2-G3은
#   같은 키), 실행 241개다(docs/experiments/task-06-prep.md 머리) — 그 수를 출력한다. 색인 하나라도 읽지 못하거나 색인의 실험 줄이
#   241개가 아니면(고른 것과 상관없이) 분석을 돌리지 않고 실패한다(R06-3 — 색인이 빠진 채 나머지만 통과하지 않게).
# 키: task 02·03·04 = t0N-<조건>, task 04-b = t04b-<조건>-<시나리오에서 overload- 를 뗀 것>, task 05 = t05-<조건>.
# 실행마다 raw.tar.gz를 runs/.recompute/<실행 이름>-expect/에 풀고, 그 실행이 원래 돌린 분석(실행 폴더에 보고가 있는 것 — 대조 분석은 늘,
#   report-transport·retry·timing·idem·overload.json이 있으면 그 하위 명령)을 scripts/task04-recompute.sh와 같은 인자로 돌린다.
#   기대 파일은 **새 기대 파일만** 준다(대조 분석 = load/expectations/<키>.json, 하위 명령 = <키>.<분석>.json — 없으면 --expect 없이).
#   묶음 안의 옛 기대 파일(expect*.json)은 쓰지 않는다. 새 파일이 없는 분석에 옛 기대가 있었으면 "옛 기대만 있음"으로 적는다.
# 출력: 실행마다 한 줄 `<키> <실행> 대조 <rc>, 시각 <rc>, ...`, 0이 아닌 분석은 그 보고의 differences(입력 오류면 표준 오류의 끝)를 아래에 붙인다.
# 종료 코드: 모든 분석이 0이면 0, 아니면 1. 다 돈 뒤 runs/.recompute/*-expect를 지운다(KEEP=yes면 남긴다).
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/lib.sh

X=load/expectations
EXPECTED_RUNS=241
# 색인 → "키<TAB>실행 폴더" 줄. 색인을 읽지 못하면 실패(awk의 종료 코드 — 아래에서 명령 치환의 종료 코드로 받는다).
list_runs() {
  local t exps
  for t in 02 03 04 04b 05; do
    [[ -r runs/task-$t-index.tsv ]] || { echo "실패: 색인 runs/task-$t-index.tsv 를 읽지 못했다" >&2; return 1; }
    case $t in
      02) exps="ga na da" ;; 03|04) exps="ga na da ra" ;; 04b) exps="ga na pl" ;; 05) exps="exp-P1 exp-P2 exp2-P1 exp2-P2" ;;
    esac
    awk -F'\t' -v t="$t" -v exps="$exps" 'BEGIN { n = split(exps, a, " "); for (i = 1; i <= n; i++) ok[a[i]] = 1 }
      NR > 1 && ($1 in ok) && $4 != 0 && $8 !~ /^대체됨/ {
        key = "t" t "-" $2
        if (t == "04b") { sc = $3; sub(/^overload-/, "", sc); key = key "-" sc }
        print key "\t" $5 }' "runs/task-$t-index.tsv" || return 1
  done
}
picked() { # <키> — 인자가 없거나, 인자 가운데 하나가 키와 같거나 키의 "<인자>-" 앞 조각이면
  local k=$1 a
  (($# == 1 && ${#SELECT[@]} == 0)) && return 0
  for a in "${SELECT[@]}"; do [[ $k == "$a" || $k == "$a"-* ]] && return 0; done
  return 1
}
SELECT=("$@")
rows=$(list_runs) || die "색인을 읽지 못했다"
mapfile -t ROWS <<< "$rows"
[[ -n $rows ]] || ROWS=()
all_keys=$(printf '%s\n' "${ROWS[@]}" | cut -f1 | sort -u | wc -l)
log "색인의 실험 줄: 키 ${all_keys}개, 실행 ${#ROWS[@]}개(${EXPECTED_RUNS}이어야 한다)"
((${#ROWS[@]} == EXPECTED_RUNS)) || die "색인의 실험 줄이 ${#ROWS[@]}개다 — ${EXPECTED_RUNS}개여야 한다(색인이 빠졌거나 바뀌었다)"
sel=()
for row in "${ROWS[@]}"; do picked "${row%%$'\t'*}" && sel+=("$row"); done
((${#sel[@]} > 0)) || die "고른 실행이 없다: ${SELECT[*]}"
# 새 기대 파일이 하나도 없는 키는 실패(기대 파일을 잊은 조건이 조용히 통과하지 않게)
for k in $(printf '%s\n' "${sel[@]}" | cut -f1 | sort -u); do
  compgen -G "$X/$k.json" > /dev/null || compgen -G "$X/$k.*.json" > /dev/null || die "키 $k 의 기대 파일이 하나도 없다($X/$k[.<분석>].json)"
done
log "고른 실행 ${#sel[@]}개 — 분석기 이미지 짓기"
"${COMPOSE_LOCAL[@]}" --profile tools build analyzer > /dev/null

bad=0
for row in "${sel[@]}"; do
  key=${row%%$'\t'*}; run=${row#*$'\t'}; run=${run%/}
  name=$(basename "$run")
  [[ -f $run/raw.tar.gz && -f $run/config.env ]] || die "$run 에 raw.tar.gz나 config.env가 없다"
  tmp=runs/.recompute/$name-expect
  rm -rf "$tmp"; mkdir -p "$tmp"
  tar -xzf "$run/raw.tar.gz" -C "$tmp"
  cp "$run/config.env" "$tmp/config.env" # (task 06) 분석기가 시계 어긋남을 읽는다(docs/measurement.md 12절 — 노트북의 실행에는 그 칸이 없다)
  in=/runs/.recompute/$name-expect
  conf() { sed -n "s/^$1=//p" "$run/config.env"; }
  planned=$(conf planned_answers); [[ -n $planned ]] || planned=$(conf planned_requests)
  codes=""; notes=""; fails=""
  analyze() { # <이름> <보고 파일> <기대 꼬리(없으면 "")> <옛 기대 파일 이름> <하위 명령 인자...>
    local label=$1 report=$2 suf=$3 old=$4 rc; shift 4
    local args=("$@")
    if [[ -f $X/$key$suf.json ]]; then
      cp "$X/$key$suf.json" "$tmp/expect6$suf.json"
      args+=(--expect "$in/expect6$suf.json")
    elif [[ -f $tmp/$old ]]; then
      notes+=" [$label: 새 기대 없음, 옛 기대만 있음($old)]"
    fi
    set +e
    "${COMPOSE_LOCAL[@]}" run --rm --no-deps --user "$(id -u):$(id -g)" analyzer "${args[@]}" > "$tmp/out-$label.txt" 2>&1
    rc=$?
    set -e
    codes+="${codes:+, }$label $rc"
    if ((rc != 0)); then
      bad=1
      if [[ -f $tmp/$report ]]; then
        fails+=$(jq -r --arg l "$label" '.differences[] | "    " + $l + ": " + .' "$tmp/$report")$'\n'
      else
        fails+=$(tail -3 "$tmp/out-$label.txt" | sed "s/^/    $label(입력 오류): /")$'\n'
      fi
    fi
  }
  analyze 대조 report.json "" expect.json --run "$in" --planned "$planned"
  [[ -f $run/report-transport.json ]] && analyze 전송 report-transport.json .transport expect.transport.json transport --run "$in"
  [[ -f $run/report-retry.json ]] && analyze 재시도 report-retry.json .retry expect.retry.json retry --run "$in"
  if [[ -f $run/report-timing.json ]]; then
    targs=(timing --run "$in")
    slow=$(conf slow_label); [[ -n $slow ]] && targs+=(--slow-label "$slow")
    analyze 시각 report-timing.json .timing expect.timing.json "${targs[@]}"
  fi
  if [[ -f $run/report-idem.json ]]; then
    iargs=(idem --run "$in")
    [[ $(conf row_ids_exported) == yes ]] || iargs+=(--no-row-ids)
    analyze "멱등" report-idem.json .idem expect.idem.json "${iargs[@]}"
  fi
  [[ -f $run/report-overload.json ]] && analyze 한도 report-overload.json .overload expect.overload.json overload --run "$in"
  # 새 기대 파일이 있는데 그 분석을 이 실행이 돌리지 않았다면(보고가 없다) 그 기대가 걸리지 않은 것 — 실패로 센다
  for suf in transport retry timing idem overload; do
    [[ -f $X/$key.$suf.json && ! -f $run/report-$suf.json ]] && { bad=1; fails+="    $key.$suf.json 이 있는데 이 실행은 $suf 분석을 돌리지 않았다"$'\n'; }
  done
  echo "$key $name $codes$notes"
  [[ -z $fails ]] || printf '%s' "$fails"
done
[[ ${KEEP:-no} == yes ]] || rm -rf runs/.recompute/*-expect
exit "$bad"
