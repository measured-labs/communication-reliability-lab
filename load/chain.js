// 주문 입구(order)에 정해진 도착률(open model)로 보내고, 요청마다 정답 한 줄을 남긴다.
// 실행은 scripts/run.sh가 한다. 정답 줄의 형식: docs/measurement.md "1. 정답".
//
// 받는 값(모두 꼭 준다 — 기본값에 기대지 않는다):
//   SCENARIO         warmup | calibration | duplicate
//   BASE_URL         예: http://localhost:8081
//   RATE             초당 요청 수
//   DURATION_SECONDS 보내는 시간(초)
//   REQUEST_TIMEOUT  k6의 요청 타임아웃(예: 5s). k6 기본은 60s
//   RUN_ID           requestId 앞에 붙인다(실행마다 다르게)
import http from 'k6/http';
import exec from 'k6/execution';

function required(name) {
  const v = __ENV[name];
  if (v === undefined || v === '') throw new Error(`${name} 가 없다`);
  return v;
}

const SCENARIO = required('SCENARIO');
const BASE_URL = required('BASE_URL');
const RATE = Number(required('RATE'));
const DURATION_SECONDS = Number(required('DURATION_SECONDS'));
const REQUEST_TIMEOUT = required('REQUEST_TIMEOUT');
const RUN_ID = required('RUN_ID');

// 보정 실행의 섞음(답을 아는 것). i번째 요청 = CALIBRATION_MIX[i % 12] — 종류마다 건수가 정확히 같다.
// 층 사이의 타임아웃(compose.yaml): order→inventory 2000ms, inventory→payment 1000ms, k6→order REQUEST_TIMEOUT(5s).
export const CALIBRATION_MIX = [
  '',                            // 정상
  'order:error',                 // 층마다의 오류
  'inventory:error',
  'payment:error',
  'inventory:delay=300',         // 타임아웃보다 짧은 지연
  'payment:delay=300',
  'order:delay=6000',            // 타임아웃보다 긴 지연: k6(5s)보다 길다
  'inventory:delay=2500',        //   order→inventory(2000ms)보다 길다
  'payment:delay=1500',          //   inventory→payment(1000ms)보다 길다
  'order:fail-after-commit',     // 처리(커밋)한 뒤 응답 전에 실패
  'inventory:fail-after-commit',
  'payment:fail-after-commit',
];

// 일부러 틀린 경우: 처음 2×DUPLICATE_PAIRS개 요청은 둘씩 같은 requestId를 쓴다(장애 없음).
export const DUPLICATE_PAIRS = 50;

const SCENARIOS = ['warmup', 'calibration', 'duplicate'];
if (!SCENARIOS.includes(SCENARIO)) throw new Error(`SCENARIO '${SCENARIO}' 는 ${SCENARIOS} 가 아니다`);

export const options = {
  scenarios: {
    chain: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: `${DURATION_SECONDS}s`,
      preAllocatedVUs: 100,
      maxVUs: 300,
    },
  },
  // 요약은 보고에 쓰지 않는다(보고는 정답 줄에서 계산한다). 실행 기록에 남기기만 한다.
  summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'p(99)', 'max'],
};

function pick(i) {
  if (SCENARIO === 'calibration') {
    return { requestId: `${RUN_ID}-${i}`, fault: CALIBRATION_MIX[i % CALIBRATION_MIX.length] };
  }
  if (SCENARIO === 'duplicate' && i < 2 * DUPLICATE_PAIRS) {
    return { requestId: `${RUN_ID}-dup-${Math.floor(i / 2)}`, fault: '' };
  }
  return { requestId: `${RUN_ID}-${i}`, fault: '' };
}

// constant-arrival-rate는 끝 경계에서 반복을 하나 더 시작할 때가 있다(30s × 20/s에 601번 — 실측 2026-11-24).
// 보낸 수를 계획한 수(RATE × DURATION_SECONDS)에 정확히 맞추려고 넘는 반복은 보내지 않는다.
// 모자란 경우(VU 부족으로 떨어진 반복)는 분석기가 --planned로 잡는다.
const PLANNED = RATE * DURATION_SECONDS;

export default function () {
  const i = exec.scenario.iterationInTest;
  if (i >= PLANNED) return;
  const { requestId, fault } = pick(i);
  const headers = { 'Content-Type': 'application/json', 'X-Request-Id': requestId };
  if (fault) headers['X-Fault'] = fault;
  const sentAt = new Date();
  const res = http.post(`${BASE_URL}/orders`, JSON.stringify({ amount: 1000 }), {
    headers,
    timeout: REQUEST_TIMEOUT,
    responseType: 'none', // 본문은 판정에 쓰지 않는다(판정은 DB 행으로)
  });
  const elapsedMs = Date.now() - sentAt.getTime();
  // 응답이 없어도(타임아웃·연결 오류: status 0) 정답 줄은 반드시 남긴다.
  console.log(JSON.stringify({
    type: 'answer',
    requestId,
    fault,
    iteration: i,
    sentAt: sentAt.toISOString(),
    status: res.status,
    errorCode: res.error_code,
    elapsedMs,
  }));
}
