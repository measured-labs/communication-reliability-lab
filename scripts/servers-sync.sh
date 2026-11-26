#!/usr/bin/env bash
# (task 06) 지금 커밋(HEAD)의 코드를 두 서버의 ~/communication-reliability-lab 에 보내고, 부하 서버에 k6를 둔다. 노트북에서 돌린다.
#   SERVICES_SSH=<계정>@<서비스 서버> LOAD_SSH=<계정>@<부하 서버> scripts/servers-sync.sh
# 서버에 git 저장소·push/pull 설정을 만들지 않는다. 키는 ssh agent로 받는다(이 스크립트는 키 파일을 다루지 않는다).
# 주소는 커밋하는 파일에 적지 않는다 — 환경 변수로만 준다.
# 보내는 것: `git archive HEAD`에서 docs/·runs/를 뺀 것 + .deployed-commit(커밋) + .deployed-sha256(보낸 파일마다의 sha256 —
#   scripts/run.sh가 실행 앞에 두 서버에서 견주고, 실행 기록의 git_commit·git_dirty로 쓴다). rsync --delete로 서버의 폴더를 보낸 것과
#   같게 맞추되 서버의 runs/(실행 기록)와 .tools/(k6)는 지우지 않는다.
# 커밋하지 않은 변경이 docs/·runs/ 밖에 있으면 멈춘다 — 실행 기록의 커밋이 돈 코드와 같게.
# k6: 부하 서버의 .tools/k6-<판>/k6 — 공식 릴리스 묶음을 받아 sha256을 아래 값과 견준 뒤 푼다(이미 있으면 받지 않는다).
#   쓰는 법: 부하 서버에서 K6=.tools/k6-v2.4.0/k6. 까닭(컨테이너가 아니라 바이너리): docs/dependencies.md "task 06".
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/lib.sh

: "${LOAD_SSH:?부하 서버(<계정>@<주소>)를 준다}"
[[ -n $SERVICES_SSH ]] || die "SERVICES_SSH(서비스 서버 <계정>@<주소>)를 준다"
K6_VERSION=v2.4.0
K6_TGZ_SHA256=5a5ecf23529ccf93135bc7e5b0c1cd5126ecf5f2672e22eed1556a0ab74abb87 # 공식 k6-v2.4.0-checksums.txt의 k6-v2.4.0-linux-amd64.tar.gz

[[ -z $(git status --porcelain --untracked-files=no -- . ':!docs' ':!runs') ]] || die "커밋하지 않은 변경이 있다(docs/·runs/ 밖) — 커밋한 뒤 보낸다"
commit=$(git rev-parse HEAD)
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
git archive HEAD -- . ':!docs' ':!runs' | tar -x -C "$tmp"
(cd "$tmp" && find . -type f ! -name .deployed-sha256 -printf '%P\n' | LC_ALL=C sort | xargs -d '\n' sha256sum > .deployed-sha256)
echo "$commit" > "$tmp/.deployed-commit"

for dest in "$SERVICES_SSH" "$LOAD_SSH"; do
  ssh -n -o BatchMode=yes "$dest" "mkdir -p ~/$SERVICES_DIR/runs"
  rsync -a --delete --exclude=/runs/ --exclude=/.tools/ -e 'ssh -o BatchMode=yes' "$tmp/" "$dest:$SERVICES_DIR/"
  log "보냄: .${dest##*.} ← $commit"
done

k6_dir=.tools/k6-$K6_VERSION
ssh -n -o BatchMode=yes "$LOAD_SSH" "cd ~/$SERVICES_DIR && mkdir -p .tools && if [[ ! -x $k6_dir/k6 ]]; then
    curl -fsSL -o .tools/k6.tar.gz https://github.com/grafana/k6/releases/download/$K6_VERSION/k6-$K6_VERSION-linux-amd64.tar.gz \
      && echo '$K6_TGZ_SHA256  .tools/k6.tar.gz' | sha256sum --quiet -c \
      && mkdir -p $k6_dir && tar -xzf .tools/k6.tar.gz -C $k6_dir --strip-components=1 && rm .tools/k6.tar.gz
  fi && $k6_dir/k6 version" || die "부하 서버에 k6 $K6_VERSION 을 두지 못했다"
