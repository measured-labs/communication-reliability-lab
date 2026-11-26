#!/usr/bin/env bash
# task 06: 서버 배치에서 06-a(보정 전부)와 06-b(실험 다시 재기)를 돌린다(docs/experiments/task-06-design.md 3·4절).
# 부하 서버에서 돌린다 — 서비스 서버는 SERVICES_SSH, k6는 K6(scripts/lib.sh·run.sh 머리, scripts/servers-sync.sh가 둔 것):
#   SERVICES_SSH=<계정>@<서비스 서버> K6=.tools/k6-v2.4.0/k6 scripts/task06-all.sh [cal|restart|t02|t03|t04|t04b|t05 ...]
#   (인자가 없으면 cal restart — 06-b의 묶음은 인자로만 돈다. 노트북의 세션이 끊겨도 돌게 띄우는 법: 설계 1절)
#   cal: task 01~05의 보정 전부를 원래의 기대 파일 그대로. 맨 앞의 calibration은 버리는 실행(r0 — 서비스를 새로 띄운 직후) 뒤 세 번(r1~r3),
#        나머지는 한 번씩. 조건과 기대 파일은 앞 단계의 것 그대로: scripts/task04b-all.sh의 base·cal, scripts/task05-all.sh의 cal·cal2
#        (task05-all.sh의 cal-h-*(HTTP 기본)는 base-calibration·base-ft-t300·base-ft-t1000과 조건이 같아 다시 돌리지 않는다).
#   restart: 멱등 키가 서비스의 재기동을 넘어 남는지(scripts/task04-restart-check.sh — 실행 기록은 runs/restart-check-*).
#   t02·t03·t04·t04b·t05: 06-b의 묶음(설계 4절의 표 — 조건·횟수·차례). 조건의 값은 노트북의 scripts/task0*-all.sh 그대로이고, 기대 파일은
#        load/expectations/<키>[.<분석>].json 가운데 있는 것을 RECON_EXPECT·TIMING_EXPECT·RETRY_EXPECT·IDEM_EXPECT·OVERLOAD_EXPECT·
#        TRANSPORT_EXPECT로 건다(키 = t02-<조건>·t03-<조건>·t04-<조건>·t04b-<조건>-<spike|low|edge|burst>·t05-<배치>-<조건> — 하나도 없으면 멈춘다).
#        같은 실험의 조건은 바퀴(1~3)마다 한 번씩 번갈아 돌고 바퀴마다 차례를 한 칸 돌린다. t04 다·t04-b는 노트북의 차례 그대로.
# 블록 = scripts/task04.sh 한 번: 서비스를 그 조건으로 다시 띄우고 → 버리는 실행 r0 → 그 블록의 잰 반복(REP_LIST).
#   t04-b 세 자리처럼 떠 있는 서비스로 이어 재는 블록(UP=no)은, 앞 블록을 이번에 돌지 않았으면(이어 돌기에서 건너뜀) 다시 띄우고 r0을 버린다.
# 색인(INDEX로 바꿀 수 있다): cal·restart → runs/task-06a-final-index.tsv, 06-b → runs/task-06b-index.tsv,
#   SMOKE=yes(묶음마다 첫 블록 하나만 — 실행기의 경로를 끝까지 보는 것, 잰 실행으로 세지 않는다) → runs/task-06b-smoke-index.tsv.
#   두 서버의 `uptime`은 블록마다 runs/task-06-uptime.txt(실행마다의 값은 config.env의 host_loadavg_*·services_loadavg_*).
# 이어 돌기: 색인에 이미 종료 0으로 있는(비고가 "대체됨"이 아닌) (실험, 조건, 시나리오, 반복)은 건너뛴다 — 끊긴 뒤 같은 명령을 다시 부르면 이어 돈다.
#   전에 실패한 (…, 반복)을 다시 돌면 옛 줄의 비고에 "대체됨: 이어 돌기에서 다시 잼(앞 종료 <코드>)"을 단다.
# 실패: 실행이 실패해도 색인에 종료 코드를 남기고 다음으로 간다. 잇달아 CONSECUTIVE_FAIL_MAX(기본 5)번 실패하면 멈춘다(종료 1).
#   시계의 앞·뒤 차가 문턱(config.env의 clock_offset_drift_max_ms)을 넘어 실패한 잰 실행은 같은 서비스로 한 번만 다시 돈다
#   (옛 줄 비고 "대체됨: 시계 앞·뒤 차 <ms>ms > 문턱 <ms>ms"). 다시 돈 것도 같은 까닭으로 실패하면 멈춘다(종료 1 — 사람이 본다).
#   끝에 실패가 있으면 종료 코드 1(06-a: 06-b로 넘어가지 않는다).
# DRY_RUN=yes: 실행하지 않고 돌 차례(블록마다 실험·조건·시나리오·r0·잰 반복·띄우기·조건 값과 기대 파일)와 묶음마다의 블록·잰 실행·버리는 실행 수를
#   찍는다(서버 없이 돈다. 이어 돌기의 건너뜀도 색인대로 보인다).
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/lib.sh

parts=("$@")
((${#parts[@]} > 0)) || parts=(cal restart)
DRY_RUN=${DRY_RUN:-no}
SMOKE=${SMOKE:-no}
CONSECUTIVE_FAIL_MAX=${CONSECUTIVE_FAIL_MAX:-5}
[[ $DRY_RUN == yes || $DRY_RUN == no ]] || die "DRY_RUN '$DRY_RUN' 는 yes|no"
[[ $SMOKE == yes || $SMOKE == no ]] || die "SMOKE '$SMOKE' 는 yes|no"
[[ $DRY_RUN == yes || -n $SERVICES_SSH ]] || die "SERVICES_SSH(서비스 서버)를 준다 — 노트북의 보정은 scripts/task04b-all.sh base 등으로 돌린다"
INDEX_OVERRIDE=${INDEX:-}
UPTIME_LOG=runs/task-06-uptime.txt
X=load/expectations
HEADER=$'experiment\tcondition\tscenario\trep\trun\texit\tconditions\tnote'
failed=()
fails_in_row=0
SERVICE_STATE="" # 지금 떠 있는 서비스의 조건(블록의 "조건 이름 + 값") — 이어 재는 블록이 그 위에서 돌 수 있나
declare -A RETRIED=()
declare -A SMOKE_DONE=()
n_blocks=0; n_measured=0; n_discard=0; n_skipped=0

index_of() { # <묶음> → 그 묶음의 색인
  if [[ -n $INDEX_OVERRIDE ]]; then echo "$INDEX_OVERRIDE"
  elif [[ $1 == cal || $1 == restart ]]; then echo runs/task-06a-final-index.tsv
  elif [[ $SMOKE == yes ]]; then echo runs/task-06b-smoke-index.tsv
  else echo runs/task-06b-index.tsv; fi
}

stamp() { # 원격 uptime이 실패하면 멈춘다(빈 값을 남기지 않는다 — R06-6)
  local here there
  here=$(uptime)
  there=$(on_services uptime) || die "서비스 서버의 uptime을 읽지 못했다"
  printf '%s\t%s\t%s\t%s\n' "$(date -Iseconds)" "$1" "load: $here" "services: $there" >> "$UPTIME_LOG"
}

# 색인에 이 (실험, 조건, 시나리오, 반복)이 종료 0으로 있나(비고가 "대체됨"으로 시작하는 줄은 빼고)
ok_in_index() {
  [[ -f $INDEX ]] || return 1
  awk -F'\t' -v e="$1" -v c="$2" -v s="$3" -v r="$4" \
    'NR > 1 && $1 == e && $2 == c && $3 == s && $4 == r && $6 == 0 && $8 !~ /^대체됨/ { f = 1 } END { exit !f }' "$INDEX"
}
# 비고를 단다: 그 (실험, 조건, 시나리오, 반복)의 비고가 빈 줄 가운데 실행 폴더가 <run>인 줄(<run>이 "-"면 종료 0이 아닌 줄 모두)
note_rows() { # <실험> <조건> <시나리오> <반복> <run 또는 -> <비고>
  local tmp
  tmp=$(mktemp "$INDEX.XXXXXX")
  awk -F'\t' -v OFS='\t' -v e="$1" -v c="$2" -v s="$3" -v r="$4" -v run="$5" -v note="$6" \
    'NR > 1 && $1 == e && $2 == c && $3 == s && $4 == r && $8 == "" && (run == "-" ? $6 != 0 : $5 == run) { $8 = note } { print }' "$INDEX" > "$tmp"
  mv "$tmp" "$INDEX"
}
prior_failed_codes() { # <실험> <조건> <시나리오> <반복> → 비고가 빈, 종료 0이 아닌 줄의 종료 코드들
  [[ -f $INDEX ]] || return 0
  awk -F'\t' -v e="$1" -v c="$2" -v s="$3" -v r="$4" 'NR > 1 && $1 == e && $2 == c && $3 == s && $4 == r && $6 != 0 && $8 == "" { printf "%s ", $6 }' "$INDEX"
}
# 실행 폴더의 config.env에서 시계의 앞·뒤 차가 문턱을 넘었나(분석기의 규칙과 같다 — docs/measurement.md 12-1). 넘었으면 "<차> <문턱>"을 내고 0.
clock_over() {
  local c=$1/config.env s e m
  [[ -f $c ]] || return 1
  s=$(sed -n 's/^clock_offset_services_minus_load_ms_start=//p' "$c"); e=$(sed -n 's/^clock_offset_services_minus_load_ms_end=//p' "$c")
  m=$(sed -n 's/^clock_offset_drift_max_ms=//p' "$c"); m=${m:-5}
  [[ -n $s && -n $e ]] || return 1
  awk -v s="$s" -v e="$e" -v m="$m" 'BEGIN { d = e - s; if (d < 0) d = -d; if (d > m) { printf "%.3f %s\n", d, m; exit 0 } exit 1 }'
}

# 키의 기대 파일 → "VAR=파일" 들(있는 것만). 하나도 없으면 멈춘다.
expect_vars() {
  local key=$1 pair suf var out=()
  for pair in ":RECON_EXPECT" ".timing:TIMING_EXPECT" ".retry:RETRY_EXPECT" ".idem:IDEM_EXPECT" ".overload:OVERLOAD_EXPECT" ".transport:TRANSPORT_EXPECT"; do
    suf=${pair%%:*}; var=${pair#*:}
    if [[ -f $X/$key$suf.json ]]; then out+=("$var=$X/$key$suf.json"); fi
  done
  ((${#out[@]} > 0)) || die "키 $key 의 기대 파일이 하나도 없다($X/$key[.<분석>].json)"
  echo "${out[*]}"
}

# 블록의 실행이 색인에 남긴 줄을 센다: 실패를 세고, 시계 문턱으로 실패한 잰 반복을 RETRY_REPS에 모은다.
tally() { # <이 블록 앞의 색인 줄 수>
  local before=$1 e c s rep run rc rest d
  RETRY_REPS=()
  if (($(wc -l < "$INDEX") == before)); then # 띄우기에서 실패 등 — 실행이 하나도 남지 않았다
    failed+=("${BLOCK_DESC}(실행 없음)"); fails_in_row=$((fails_in_row + 1))
  else
    while IFS=$'\t' read -r e c s rep run rc rest; do
      if [[ $rc == 0 ]]; then fails_in_row=0; continue; fi
      failed+=("$c r$rep(종료 $rc)"); fails_in_row=$((fails_in_row + 1))
      if [[ $rep != 0 && $rc == 1 && -d $run ]] && d=$(clock_over "$run"); then
        RETRY_REPS+=("$rep|$run|$d")
      fi
    done < <(tail -n +"$((before + 1))" "$INDEX")
  fi
  ((fails_in_row < CONSECUTIVE_FAIL_MAX)) || die "잇달아 ${fails_in_row}번 실패했다 — 멈춘다(색인 $INDEX)"
}

# 블록 하나. <키(기대 파일 — 비우면 걸지 않는다)> <실험> <조건> <시나리오> [VAR=값 ...]. 환경: REP_LIST(잰 반복), UP, DISCARD(기본 yes)
block() {
  local key=$1 exp=$2 cond=$3 sc=$4; shift 4
  local vars=("$@") up=${UP:-yes} discard=${DISCARD:-yes} want=() rep codes state before item r run d ev
  state="$cond ${vars[*]}" # 서비스의 조건(기대 파일은 빼고 — 같은 서비스로 시나리오만 바꿔 이어 재는 블록이 있다)
  if [[ -n $key ]]; then ev=$(expect_vars "$key") || exit 1; read -ra ev <<< "$ev"; vars+=("${ev[@]}"); fi
  if [[ $SMOKE == yes ]]; then
    [[ -z ${SMOKE_DONE[$PART]:-} ]] || return 0
    SMOKE_DONE[$PART]=yes
  fi
  for rep in $REP_LIST; do ok_in_index "$exp" "$cond" "$sc" "$rep" || want+=("$rep"); done
  if ((${#want[@]} == 0)); then
    n_skipped=$((n_skipped + 1))
    if [[ $DRY_RUN == yes ]]; then printf '건너뜀\t%s\t%s\t%s\t반복 %s(색인에 종료 0)\n' "$exp" "$cond" "$sc" "$REP_LIST"; fi
    return 0
  fi
  # 이어 재는 블록인데 지금 떠 있는 서비스가 이 조건이 아니면(앞 블록을 건너뛰었다) 다시 띄우고 r0을 버린다
  if [[ $up == no && $SERVICE_STATE != "$state" ]]; then up=yes; discard=yes; fi
  n_blocks=$((n_blocks + 1)); n_measured=$((n_measured + ${#want[@]}))
  if [[ $discard == yes ]]; then n_discard=$((n_discard + 1)); fi
  if [[ $DRY_RUN == yes ]]; then
    printf '블록\t%s\t%s\t%s\tr0 %s\t반복 %s\t띄움 %s\t%s\n' "$exp" "$cond" "$sc" "$([[ $discard == yes ]] && echo 있음 || echo 없음)" "${want[*]}" "$up" "${vars[*]}"
    SERVICE_STATE=$state
    return 0
  fi
  for rep in "${want[@]}"; do
    codes=$(prior_failed_codes "$exp" "$cond" "$sc" "$rep")
    if [[ -n $codes ]]; then note_rows "$exp" "$cond" "$sc" "$rep" - "대체됨: 이어 돌기에서 다시 잼(앞 종료 ${codes% })"; fi
  done
  BLOCK_DESC="$exp $cond"
  stamp "$exp $cond ${want[*]}"
  before=$(wc -l < "$INDEX")
  INDEX=$INDEX UP=$up DISCARD=$discard REP_LIST="${want[*]}" scripts/task04.sh "$exp" "$cond" "$sc" "${vars[@]}" || true
  SERVICE_STATE=$state
  tally "$before"
  # 시계 문턱으로 실패한 잰 반복: 같은 서비스로 한 번만 다시
  for item in "${RETRY_REPS[@]}"; do
    IFS='|' read -r r run d <<< "$item"
    if [[ -n ${RETRIED[$exp|$cond|$sc|$r]:-} ]]; then
      die "$cond r$r 가 다시 돈 뒤에도 시계의 앞·뒤 차 문턱을 넘었다(${d% *}ms > ${d#* }ms — $run) — 멈춘다"
    fi
    RETRIED[$exp|$cond|$sc|$r]=yes
    note_rows "$exp" "$cond" "$sc" "$r" "$run" "대체됨: 시계 앞·뒤 차 ${d% *}ms > 문턱 ${d#* }ms"
    log "$cond r$r: 시계의 앞·뒤 차가 문턱을 넘었다(${d% *}ms) — 같은 서비스로 한 번 다시 돈다"
    before=$(wc -l < "$INDEX")
    INDEX=$INDEX UP=no DISCARD=no REP_LIST="$r" scripts/task04.sh "$exp" "$cond" "$sc" "${vars[@]}" || true
    tally "$before"
    if ((${#RETRY_REPS[@]} > 0)); then
      IFS='|' read -r _ run d <<< "${RETRY_REPS[0]}"
      die "$cond r$r 가 다시 돈 뒤에도 시계의 앞·뒤 차 문턱을 넘었다(${d% *}ms > ${d#* }ms — $run) — 멈춘다"
    fi
  done
}

# 같은 실험의 조건을 바퀴 1~3에서 번갈아(바퀴 r은 r−1칸 돌린 차례). <키 앞> <실험> <조건 값 함수> <조건...>
# 함수는 "<시나리오> VAR=값 ..."을 낸다.
alternate() {
  local pre=$1 exp=$2 fn=$3; shift 3
  local conds=("$@") k=$# r i c v vv
  for r in 1 2 3; do
    for ((i = 0; i < k; i++)); do
      c=${conds[$(((i + r - 1) % k))]}
      v=$("$fn" "$c") || exit 1
      read -ra vv <<< "$v"
      REP_LIST=$r block "$pre-$c" "$exp" "$c" "${vv[@]}"
    done
  done
}

# ── 조건의 값(노트북의 scripts/task0*-all.sh 그대로) ──
G1=(CHAIN_TRANSPORT=grpc GRPC_DEADLINE_PROPAGATION=false GRPC_OBSERVE_CANCELLATION=false)
G2=(CHAIN_TRANSPORT=grpc GRPC_DEADLINE_PROPAGATION=true GRPC_OBSERVE_CANCELLATION=false)
G3=(CHAIN_TRANSPORT=grpc GRPC_DEADLINE_PROPAGATION=true GRPC_OBSERVE_CANCELLATION=true)
G1o=(CHAIN_TRANSPORT=grpc GRPC_DEADLINE_PROPAGATION=false GRPC_OBSERVE_CANCELLATION=true)
H=(CHAIN_TRANSPORT=http)
FT300=(ORDER_TO_INVENTORY_TIMEOUT=5000ms INVENTORY_TO_PAYMENT_TIMEOUT=300ms)
FT1000=(ORDER_TO_INVENTORY_TIMEOUT=5000ms INVENTORY_TO_PAYMENT_TIMEOUT=1000ms)
P1=(ORDER_TO_INVENTORY_TIMEOUT=2000ms INVENTORY_TO_PAYMENT_TIMEOUT=1000ms)
P2=(ORDER_TO_INVENTORY_TIMEOUT=1000ms INVENTORY_TO_PAYMENT_TIMEOUT=2000ms)
LIMIT=(PAYMENT_OVERLOAD_ENABLED=true PAYMENT_OVERLOAD_LIMIT=50 PAYMENT_OVERLOAD_WINDOW=1000ms ORDER_TO_INVENTORY_TIMEOUT=5000ms)
ON=IDEMPOTENCY_ENABLED=true
pre() { local p=$1 kv o=(); shift; for kv in "$@"; do o+=("${p}_RETRY_$kv"); done; echo "${p}_RETRY_ENABLED=true ${o[*]}"; }
EXP3=(BACKOFF=exponential BASE_DELAY=100ms MAX_DELAY=1000ms ON=failed,unknown) # task 03 가·다·라의 간격(task03-all.sh EXP)
EXP4=(MAX_ATTEMPTS=3 "${EXP3[@]}")                                               # task 04 가(task04-all.sh EXP)

t02_val() {
  case "$1" in
    ga-t80|ga-t173|ga-t323|ga-t2000) echo "false-timeout ORDER_TO_INVENTORY_TIMEOUT=5000ms INVENTORY_TO_PAYMENT_TIMEOUT=${1#ga-t}ms VIRTUAL_THREADS=false" ;;
    na-none-platform)  echo "slow-layer ORDER_TO_INVENTORY_TIMEOUT=60000ms INVENTORY_TO_PAYMENT_TIMEOUT=60000ms VIRTUAL_THREADS=false" ;;
    na-split-platform) echo "slow-layer ORDER_TO_INVENTORY_TIMEOUT=2000ms INVENTORY_TO_PAYMENT_TIMEOUT=1000ms VIRTUAL_THREADS=false" ;;
    na-none-virtual)   echo "slow-layer ORDER_TO_INVENTORY_TIMEOUT=60000ms INVENTORY_TO_PAYMENT_TIMEOUT=60000ms VIRTUAL_THREADS=true" ;;
    na-split-virtual)  echo "slow-layer ORDER_TO_INVENTORY_TIMEOUT=2000ms INVENTORY_TO_PAYMENT_TIMEOUT=1000ms VIRTUAL_THREADS=true" ;;
    da-inside) echo "split-timeout ORDER_TO_INVENTORY_TIMEOUT=2000ms INVENTORY_TO_PAYMENT_TIMEOUT=1000ms VIRTUAL_THREADS=false" ;;
    da-longer) echo "split-timeout ORDER_TO_INVENTORY_TIMEOUT=1000ms INVENTORY_TO_PAYMENT_TIMEOUT=2000ms VIRTUAL_THREADS=false" ;;
    *) die "모르는 조건 '$1'" ;;
  esac
}
t03_val() {
  case "$1" in
    ga-max1|ga-max2|ga-max3|ga-max5) echo "retry-mix ORDER_TO_INVENTORY_TIMEOUT=5000ms $(pre INVENTORY "MAX_ATTEMPTS=${1#ga-max}" "${EXP3[@]}")" ;;
    na-none)        echo "retry-outage-burst ORDER_TO_INVENTORY_TIMEOUT=5000ms $(pre INVENTORY MAX_ATTEMPTS=4 BACKOFF=none)" ;;
    na-fixed)       echo "retry-outage-burst ORDER_TO_INVENTORY_TIMEOUT=5000ms $(pre INVENTORY MAX_ATTEMPTS=4 BACKOFF=fixed BASE_DELAY=200ms)" ;;
    na-exponential) echo "retry-outage-burst ORDER_TO_INVENTORY_TIMEOUT=5000ms $(pre INVENTORY MAX_ATTEMPTS=4 BACKOFF=exponential BASE_DELAY=100ms MAX_DELAY=1000ms)" ;;
    na-jitter)      echo "retry-outage-burst ORDER_TO_INVENTORY_TIMEOUT=5000ms $(pre INVENTORY MAX_ATTEMPTS=4 BACKOFF=exponential-jitter BASE_DELAY=100ms MAX_DELAY=1000ms)" ;;
    na-fixed500)    echo "retry-outage-burst ORDER_TO_INVENTORY_TIMEOUT=5000ms $(pre INVENTORY MAX_ATTEMPTS=4 BACKOFF=fixed BASE_DELAY=500ms)" ;;
    da-1L) echo "retry-mix ORDER_TO_INVENTORY_TIMEOUT=3000ms $(pre INVENTORY MAX_ATTEMPTS=3 "${EXP3[@]}")" ;;
    da-1S) echo "retry-mix ORDER_TO_INVENTORY_TIMEOUT=250ms $(pre INVENTORY MAX_ATTEMPTS=3 "${EXP3[@]}")" ;;
    da-2)  echo "retry-mix ORDER_TO_INVENTORY_TIMEOUT=3000ms $(pre ORDER MAX_ATTEMPTS=3 "${EXP3[@]}")" ;;
    da-3L) echo "retry-mix ORDER_TO_INVENTORY_TIMEOUT=3000ms $(pre INVENTORY MAX_ATTEMPTS=3 "${EXP3[@]}") $(pre ORDER MAX_ATTEMPTS=3 "${EXP3[@]}")" ;;
    da-3S) echo "retry-mix ORDER_TO_INVENTORY_TIMEOUT=250ms $(pre INVENTORY MAX_ATTEMPTS=3 "${EXP3[@]}") $(pre ORDER MAX_ATTEMPTS=3 "${EXP3[@]}")" ;;
    ra-off) echo "retry-budget ORDER_TO_INVENTORY_TIMEOUT=5000ms $(pre INVENTORY MAX_ATTEMPTS=4 "${EXP3[@]}")" ;;
    ra-on)  echo "retry-budget ORDER_TO_INVENTORY_TIMEOUT=5000ms $(pre INVENTORY MAX_ATTEMPTS=4 "${EXP3[@]}" BUDGET_ENABLED=true BUDGET_CAPACITY=10 BUDGET_DEPOSIT=0.1)" ;;
    *) die "모르는 조건 '$1'" ;;
  esac
}
t04_val() {
  case "$1" in
    ga-1-off) echo "idem-retry-mix ORDER_TO_INVENTORY_TIMEOUT=3000ms $(pre INVENTORY "${EXP4[@]}")" ;;
    ga-1-on)  echo "idem-retry-mix ORDER_TO_INVENTORY_TIMEOUT=3000ms $(pre INVENTORY "${EXP4[@]}") $ON" ;;
    ga-2-off) echo "idem-retry-mix ORDER_TO_INVENTORY_TIMEOUT=250ms $(pre INVENTORY "${EXP4[@]}") $(pre ORDER "${EXP4[@]}")" ;;
    ga-2-on)  echo "idem-retry-mix ORDER_TO_INVENTORY_TIMEOUT=250ms $(pre INVENTORY "${EXP4[@]}") $(pre ORDER "${EXP4[@]}") $ON" ;;
    ga-2-on-409u) echo "idem-retry-mix ORDER_TO_INVENTORY_TIMEOUT=250ms $(pre INVENTORY "${EXP4[@]}") $(pre ORDER "${EXP4[@]}") $ON IDEMPOTENCY_IN_PROGRESS_AS_UNKNOWN=true" ;;
    na-seq-off)  echo "idem-dup-seq" ;;
    na-seq-on)   echo "idem-dup-seq $ON" ;;
    na-conc-off) echo "idem-dup-conc" ;;
    na-conc-on)  echo "idem-dup-conc $ON" ;;
    da-off) echo "idem-plain" ;;
    da-on)  echo "idem-plain $ON" ;;
    ra-off) echo "idem-reconcile" ;;
    ra-on)  echo "idem-reconcile $ON" ;;
    *) die "모르는 조건 '$1'" ;;
  esac
}
# task 04-b: 재고→결제의 재시도(task04b-all.sh inv) — <조건 off|none|fixed|exp|jitter> [budget]
t04b_retry() {
  local o=(INVENTORY_RETRY_ENABLED=true INVENTORY_RETRY_MAX_ATTEMPTS=3 INVENTORY_RETRY_ON=failed,unknown)
  case "$1" in
    off) echo; return 0 ;;
    none) o+=(INVENTORY_RETRY_BACKOFF=none) ;;
    fixed) o+=(INVENTORY_RETRY_BACKOFF=fixed INVENTORY_RETRY_BASE_DELAY=200ms) ;;
    exp) o+=(INVENTORY_RETRY_BACKOFF=exponential INVENTORY_RETRY_BASE_DELAY=100ms INVENTORY_RETRY_MAX_DELAY=1000ms) ;;
    jitter) o+=(INVENTORY_RETRY_BACKOFF=exponential-jitter INVENTORY_RETRY_BASE_DELAY=100ms INVENTORY_RETRY_MAX_DELAY=1000ms) ;;
    *) die "모르는 조건 '$1'" ;;
  esac
  if [[ ${2:-} == budget ]]; then o+=(INVENTORY_RETRY_BUDGET_ENABLED=true INVENTORY_RETRY_BUDGET_CAPACITY=10 INVENTORY_RETRY_BUDGET_DEPOSIT=0.1); fi
  echo "${o[*]}"
}
b_ga() { local r v; v=$(t04b_retry "$1") || exit 1; read -ra r <<< "$v"; REP_LIST="$2" block "t04b-ga-$1-spike" t04b-ga "ga-$1" overload-spike "${LIMIT[@]}" "${r[@]}"; }
b_na() { # 실험 나: 노트북에서 고른 간격 = 고정 200ms(설계 4절) — 켬은 예산
  local r v; v=$(t04b_retry fixed "$([[ $1 == on ]] && echo budget)") || exit 1; read -ra r <<< "$v"
  REP_LIST="$2" block "t04b-na-$1-spike" t04b-na "na-$1" overload-spike "${LIMIT[@]}" "${r[@]}"
}
LAST_PL=""
b_pl() { # <조건> <바퀴> <시나리오...> — 조건이 앞과 같으면 다시 띄우지 않고 이어 잰다(task04b-all.sh pl)
  local c=$1 rep=$2 r v sc first=yes; shift 2
  v=$(t04b_retry "$c") || exit 1; read -ra r <<< "$v"
  for sc in "$@"; do
    if [[ $first == yes && $c != "$LAST_PL" ]]; then
      REP_LIST="$rep" block "t04b-pl-$c-${sc#overload-}" t04b-pl "pl-$c" "$sc" "${LIMIT[@]}" "${r[@]}"
    else
      UP=no DISCARD=no REP_LIST="$rep" block "t04b-pl-$c-${sc#overload-}" t04b-pl "pl-$c" "$sc" "${LIMIT[@]}" "${r[@]}"
    fi
    first=no
  done
  LAST_PL=$c
}
t05_val() { # <배치-조건> 예: P1-G1o
  local p=${1%%-*} c=${1#*-} pv cv
  case "$p" in P1) pv="${P1[*]}" ;; P2) pv="${P2[*]}" ;; *) die "모르는 배치 '$p'" ;; esac
  case "$c" in H) cv="${H[*]}" ;; G1) cv="${G1[*]}" ;; G1o) cv="${G1o[*]}" ;; G2) cv="${G2[*]}" ;; G3) cv="${G3[*]}" ;; *) die "모르는 조건 '$c'" ;; esac
  echo "split-timeout $cv $pv"
}

summary() { # <묶음>
  printf '요약\t%s\t블록 %d\t잰 실행 %d\t버리는 실행 %d\t건너뛴 블록 %d\n' "$1" "$n_blocks" "$n_measured" "$n_discard" "$n_skipped"
  n_blocks=0; n_measured=0; n_discard=0; n_skipped=0
}

for PART in "${parts[@]}"; do
  INDEX=$(index_of "$PART")
  if [[ $DRY_RUN == no && ! -f $INDEX ]]; then echo "$HEADER" > "$INDEX"; fi
  if [[ $DRY_RUN == yes ]]; then printf '묶음\t%s\t색인 %s\n' "$PART" "$INDEX"; fi
  SERVICE_STATE=""; LAST_PL=""
  case "$PART" in
    cal)
      # task 01~04 (scripts/task04b-all.sh base와 같은 줄)
      DISCARD=yes REP_LIST="1 2 3" block "" cal base-calibration calibration
      DISCARD=no; REP_LIST=1
      block "" cal base-duplicate duplicate
      block "" cal base-ft-t300 cal-false-timeout "${FT300[@]}" TIMING_EXPECT=$X/cal-false-timeout-t300.timing.json
      block "" cal base-ft-t1000 cal-false-timeout "${FT1000[@]}" TIMING_EXPECT=$X/cal-false-timeout-t1000.timing.json
      block "" cal base-sampler cal-sampler ORDER_TO_INVENTORY_TIMEOUT=2000ms INVENTORY_TO_PAYMENT_TIMEOUT=1000ms
      block "" cal base-c1-max1 cal-retry-first ORDER_TO_INVENTORY_TIMEOUT=5000ms INVENTORY_RETRY_ENABLED=true INVENTORY_RETRY_MAX_ATTEMPTS=1 RETRY_EXPECT=$X/cal-retry-c1-max1.retry.json
      block "" cal base-c2-fixed200 cal-retry-first ORDER_TO_INVENTORY_TIMEOUT=5000ms INVENTORY_RETRY_ENABLED=true INVENTORY_RETRY_MAX_ATTEMPTS=2 INVENTORY_RETRY_BACKOFF=fixed INVENTORY_RETRY_BASE_DELAY=200ms RETRY_EXPECT=$X/cal-retry-c2-fixed200.retry.json
      block "" cal base-c6-outage cal-retry-outage ORDER_TO_INVENTORY_TIMEOUT=5000ms INVENTORY_RETRY_ENABLED=true INVENTORY_RETRY_MAX_ATTEMPTS=2 INVENTORY_RETRY_BACKOFF=fixed INVENTORY_RETRY_BASE_DELAY=200ms RETRY_EXPECT=$X/cal-retry-c6-outage.retry.json
      block "" cal base-c7-burst cal-retry-burst ORDER_TO_INVENTORY_TIMEOUT=5000ms INVENTORY_RETRY_ENABLED=true INVENTORY_RETRY_MAX_ATTEMPTS=2 INVENTORY_RETRY_BACKOFF=fixed INVENTORY_RETRY_BASE_DELAY=200ms RETRY_EXPECT=$X/cal-retry-c7-burst.retry.json
      block "" cal base-c3-exponential cal-retry-second ORDER_TO_INVENTORY_TIMEOUT=5000ms INVENTORY_RETRY_ENABLED=true INVENTORY_RETRY_MAX_ATTEMPTS=3 INVENTORY_RETRY_BACKOFF=exponential INVENTORY_RETRY_BASE_DELAY=100ms INVENTORY_RETRY_MAX_DELAY=1000ms RETRY_EXPECT=$X/cal-retry-c3-exponential.retry.json
      block "" cal base-c4-jitter cal-retry-second ORDER_TO_INVENTORY_TIMEOUT=5000ms INVENTORY_RETRY_ENABLED=true INVENTORY_RETRY_MAX_ATTEMPTS=3 INVENTORY_RETRY_BACKOFF=exponential-jitter INVENTORY_RETRY_BASE_DELAY=100ms INVENTORY_RETRY_MAX_DELAY=1000ms RETRY_EXPECT=$X/cal-retry-c4-jitter.retry.json
      block "" cal base-c5-budget0 cal-retry-first ORDER_TO_INVENTORY_TIMEOUT=5000ms INVENTORY_RETRY_ENABLED=true INVENTORY_RETRY_MAX_ATTEMPTS=2 INVENTORY_RETRY_BUDGET_ENABLED=true INVENTORY_RETRY_BUDGET_CAPACITY=0 INVENTORY_RETRY_BUDGET_DEPOSIT=0 RETRY_EXPECT=$X/cal-retry-c5-budget0.retry.json
      block "" cal base-cal-idem-off cal-idem RECON_EXPECT=$X/cal-idem-off.json IDEM_EXPECT=$X/cal-idem-off.idem.json
      block "" cal base-cal-idem-on cal-idem IDEMPOTENCY_ENABLED=true RECON_EXPECT=$X/cal-idem-on.json IDEM_EXPECT=$X/cal-idem-on.idem.json
      # task 04-b (scripts/task04b-all.sh cal)
      block "" cal cal-overload cal-overload "${LIMIT[@]}"
      # task 05 (scripts/task05-all.sh cal·cal2 — cal-h-*는 위와 같은 조건이라 뺀다)
      block "" cal cal-g1-calibration calibration "${G1[@]}"
      block "" cal cal-g1-ft300 cal-false-timeout "${G1[@]}" "${FT300[@]}" TIMING_EXPECT=$X/cal-false-timeout-t300.timing.json
      block "" cal cal-g1-ft1000 cal-false-timeout "${G1[@]}" "${FT1000[@]}" TIMING_EXPECT=$X/cal-false-timeout-t1000.timing.json
      block "" cal cal-g2-calibration calibration "${G2[@]}" RECON_EXPECT=$X/calibration-grpc-g2.json
      block "" cal cal-g3-calibration calibration "${G3[@]}" RECON_EXPECT=$X/calibration-grpc-g3.json
      block "" cal cal-g3-ft300 cal-false-timeout "${G3[@]}" "${FT300[@]}" RECON_EXPECT=$X/cal-false-timeout-grpc-g3.json TIMING_EXPECT=$X/cal-false-timeout-grpc-g3.timing.json
      block "" cal cal-g1o-calibration calibration "${G1o[@]}" RECON_EXPECT=$X/calibration-grpc-g3.json
      block "" cal cal-g1o-ft300 cal-false-timeout "${G1o[@]}" "${FT300[@]}" RECON_EXPECT=$X/cal-false-timeout-grpc-g3.json TIMING_EXPECT=$X/cal-false-timeout-grpc-g3.timing.json
      unset DISCARD REP_LIST
      ;;
    restart)
      if ok_in_index cal restart-check restart-check 1; then
        n_skipped=$((n_skipped + 1)); if [[ $DRY_RUN == yes ]]; then printf '건너뜀\tcal\trestart-check\n'; fi
      elif [[ $DRY_RUN == yes ]]; then
        n_blocks=$((n_blocks + 1)); printf '블록\tcal\trestart-check\t(scripts/task04-restart-check.sh)\n'
      else
        stamp restart-check
        before=$(ls -d runs/restart-check-* 2>/dev/null | sort || true)
        rc=0; scripts/task04-restart-check.sh || rc=$?
        run=$(comm -13 <(echo "$before") <(ls -d runs/restart-check-* 2>/dev/null | sort) | tail -1)
        printf 'cal\trestart-check\trestart-check\t1\t%s\t%s\t\t\n' "${run:-없음}" "$rc" >> "$INDEX"
        ((rc == 0)) || failed+=(restart-check)
      fi
      ;;
    t02) # 설계 4절: 가 4 × 3 · 나 4 × 3 · 다 2 × 3
      alternate t02 t02-ga t02_val ga-t80 ga-t173 ga-t323 ga-t2000
      alternate t02 t02-na t02_val na-none-platform na-split-platform na-none-virtual na-split-virtual
      alternate t02 t02-da t02_val da-inside da-longer
      ;;
    t03) # 가 4 × 3 · 나 5 × 3 · 다 5 × 3 · 라 2 × 3
      alternate t03 t03-ga t03_val ga-max1 ga-max2 ga-max3 ga-max5
      alternate t03 t03-na t03_val na-none na-fixed na-exponential na-jitter na-fixed500
      alternate t03 t03-da t03_val da-1L da-1S da-2 da-3L da-3S
      alternate t03 t03-ra t03_val ra-off ra-on
      ;;
    t04) # 가 5 × 3 · 나 4 × 3 · 다 2 × 6(노트북 da-alt의 차례) · 라 2 × 3
      alternate t04 t04-ga t04_val ga-1-off ga-1-on ga-2-off ga-2-on ga-2-on-409u
      alternate t04 t04-na t04_val na-seq-off na-seq-on na-conc-off na-conc-on
      # 다: 끔 r1 · 켬 r1·r2 · 끔 r2·r3 · 켬 r3·r4 · 끔 r4·r5 · 켬 r5·r6 · 끔 r6 — 조건을 바꿀 때마다 다시 띄우고 r0을 버린다(task04-all.sh da-alt)
      for step in "off 1" "on 1 2" "off 2 3" "on 3 4" "off 4 5" "on 5 6" "off 6"; do
        c=da-${step%% *}; v=$(t04_val "$c") || exit 1; read -ra vv <<< "$v"
        REP_LIST="${step#* }" block "t04-$c" t04-da "$c" "${vv[@]}"
      done
      alternate t04 t04-ra t04_val ra-off ra-on
      ;;
    t04b) # 가 5 × 3 · 나 2 × 3 · 세 자리 5 × (3 + 5 + 3) — 노트북 task04b-all.sh ga·na·pl의 차례 그대로
      b_ga off 1; b_ga none 1; b_ga fixed 1; b_ga exp 1; b_ga jitter "1 2"
      b_ga exp 2; b_ga fixed 2; b_ga none 2; b_ga off "2 3"
      b_ga none 3; b_ga fixed 3; b_ga exp 3; b_ga jitter 3
      b_na on 1; b_na off "1 2"; b_na on "2 3"; b_na off 3
      fwd=(off none fixed exp jitter); rev=(jitter exp fixed none off)
      for c in "${fwd[@]}"; do b_pl "$c" 1 overload-low overload-edge overload-burst; done
      for c in "${rev[@]}"; do b_pl "$c" 2 overload-burst overload-edge overload-low; done
      for c in "${fwd[@]}"; do b_pl "$c" 3 overload-low overload-edge overload-burst; done
      for c in "${rev[@]}"; do b_pl "$c" 4 overload-edge; done
      for c in "${fwd[@]}"; do b_pl "$c" 5 overload-edge; done
      ;;
    t05) # H · G1 · G1o · G2 · G3 × P1 · P2 를 한 바퀴에(바퀴마다 P1의 다섯 → P2의 다섯, 차례는 r−1칸 돌림) × 3
      conds=(H G1 G1o G2 G3)
      for r in 1 2 3; do
        for p in P1 P2; do
          for ((i = 0; i < 5; i++)); do
            c=$p-${conds[$(((i + r - 1) % 5))]}
            v=$(t05_val "$c") || exit 1; read -ra vv <<< "$v"
            REP_LIST=$r block "t05-$c" "t05-$p" "$c" "${vv[@]}"
          done
        done
      done
      ;;
    *) die "모르는 묶음 '$PART'" ;;
  esac
  if [[ $DRY_RUN == yes ]]; then summary "$PART"; fi
done
if [[ $DRY_RUN == yes ]]; then exit 0; fi
stamp "끝"
((${#failed[@]} == 0)) || { log "실패한 실행: ${failed[*]} — 색인 $INDEX"; exit 1; }
log "모두 종료 코드 0"
