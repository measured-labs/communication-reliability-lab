// 주문 입구(order)에 정해진 도착률(open model)로 보내고, 요청마다 정답 한 줄을 남긴다.
// 실행은 scripts/run.sh가 한다. 정답 줄의 형식: docs/measurement.md "1. 정답".
//
// 받는 값(모두 꼭 준다 — 기본값에 기대지 않는다):
//   SCENARIO         warmup | calibration | duplicate | (task 02) false-timeout | slow-layer | split-timeout | cal-false-timeout | cal-sampler
//   BASE_URL         예: http://localhost:8081
//   RATE             초당 요청 수
//   DURATION_SECONDS 보내는 시간(초)
//   REQUEST_TIMEOUT  k6의 요청 타임아웃(예: 5s). k6 기본은 60s
//   RUN_ID           requestId 앞에 붙인다(실행마다 다르게)
//   MAX_VUS          사슬 시나리오의 최대 VU(도착률 × 가장 긴 응답 시간보다 크게 — 모자라면 dropped_iterations로 실행 실패)
//   LOOKUP_RATE      slow-layer에서만: 가벼운 조회(GET /orders/{id})의 초당 수
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
const MAX_VUS = Number(required('MAX_VUS'));

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

// ── task 02 (docs/experiments/task-02-design.md) ──
// 실험 가: 결제 지연을 로그정규에서 뽑는다 — 가운데값 80ms, σ 0.6. 요청 i의 값은 씨앗과 i로만 정해진다
// (VU 차례와 무관 — 조건과 반복마다 같은 i는 같은 지연).
export const DELAY_SEED = 20261124;
export const DELAY_MEDIAN_MS = 80;
export const DELAY_SIGMA = 0.6;

// mulberry32 — 32비트 상태의 의사 난수. [0, 1).
function mulberry32(seed) {
  let a = seed >>> 0;
  return function () {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

export function lognormalDelayMs(i) {
  const next = mulberry32((DELAY_SEED + Math.imul(i, 0x9e3779b9)) >>> 0);
  const u1 = 1 - next(); // (0, 1] — log(0)을 피한다
  const u2 = next();
  const z = Math.sqrt(-2 * Math.log(u1)) * Math.cos(2 * Math.PI * u2); // Box–Muller
  return Math.max(1, Math.round(DELAY_MEDIAN_MS * Math.exp(DELAY_SIGMA * z)));
}

// 실험 나: 요청 번호 [SLOW_FROM, SLOW_TO)(60/s에서 10s~30s)의 결제가 5s 늦는다.
export const SLOW_FAULT = 'payment:delay=5000';
const SLOW_FROM = 10 * RATE;
const SLOW_TO = 30 * RATE;

// 실험 다: 결제 지연 네 값의 섞음(i % 4).
export const SPLIT_MIX = ['', 'payment:delay=500', 'payment:delay=1500', 'payment:delay=2500'];

const SCENARIOS = ['warmup', 'calibration', 'duplicate', 'false-timeout', 'slow-layer', 'split-timeout', 'cal-false-timeout', 'cal-sampler'];
if (!SCENARIOS.includes(SCENARIO)) throw new Error(`SCENARIO '${SCENARIO}' 는 ${SCENARIOS} 가 아니다`);

// 끝난 뒤 아직 응답을 기다리는 반복을 끊지 않게: k6 요청 타임아웃보다 길게(k6 기본 30s에 기대지 않는다).
const GRACEFUL_STOP = `${parseInt(REQUEST_TIMEOUT, 10) + 10}s`;
if (!/^[0-9]+s$/.test(REQUEST_TIMEOUT)) throw new Error(`REQUEST_TIMEOUT '${REQUEST_TIMEOUT}' 는 <초>s 꼴이어야 한다`);

const scenarios = {
  chain: {
    executor: 'constant-arrival-rate',
    rate: RATE,
    timeUnit: '1s',
    duration: `${DURATION_SECONDS}s`,
    preAllocatedVUs: Math.min(100, MAX_VUS),
    maxVUs: MAX_VUS,
    gracefulStop: GRACEFUL_STOP,
  },
};
if (SCENARIO === 'slow-layer') {
  scenarios.lookup = {
    executor: 'constant-arrival-rate',
    exec: 'lookup',
    rate: Number(required('LOOKUP_RATE')),
    timeUnit: '1s',
    duration: `${DURATION_SECONDS}s`,
    preAllocatedVUs: 50,
    maxVUs: MAX_VUS,
    gracefulStop: GRACEFUL_STOP,
  };
}

export const options = {
  scenarios,
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
  const id = `${RUN_ID}-${i}`;
  if (SCENARIO === 'false-timeout') return { requestId: id, fault: `payment:delay=${lognormalDelayMs(i)}` };
  if (SCENARIO === 'slow-layer') return { requestId: id, fault: i >= SLOW_FROM && i < SLOW_TO ? SLOW_FAULT : '' };
  if (SCENARIO === 'split-timeout') return { requestId: id, fault: SPLIT_MIX[i % SPLIT_MIX.length] };
  if (SCENARIO === 'cal-false-timeout') return { requestId: id, fault: 'payment:delay=600' };
  if (SCENARIO === 'cal-sampler') return { requestId: id, fault: 'order:delay=2000' };
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
  // 요청마다 다른 금액 — 서비스가 금액을 바꾸거나 다른 요청의 금액을 쓰면 분석기가 amountMismatch로 잡는다.
  const amount = 1000 + i;
  const headers = { 'Content-Type': 'application/json', 'X-Request-Id': requestId };
  if (fault) headers['X-Fault'] = fault;
  const sentAt = new Date();
  const res = http.post(`${BASE_URL}/orders`, JSON.stringify({ amount }), {
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
    amount,
    iteration: i,
    sentAt: sentAt.toISOString(),
    sentAtMs: sentAt.getTime(), // 벽시계(ms) — 서비스의 시각 기록과 같은 시계(task 02 분석이 읽는다)
    status: res.status,
    errorCode: res.error_code,
    elapsedMs,
  }));
}

// slow-layer의 가벼운 조회. 정답 줄과 섞이지 않게 type을 "lookup"으로 남긴다(실행 스크립트가 lookups.jsonl로 가른다).
const LOOKUP_PLANNED = SCENARIO === 'slow-layer' ? Number(__ENV.LOOKUP_RATE) * DURATION_SECONDS : 0;
export function lookup() {
  const i = exec.scenario.iterationInTest;
  if (i >= LOOKUP_PLANNED) return;
  const sentAt = new Date();
  const res = http.get(`${BASE_URL}/orders/${RUN_ID}-lookup-${i}`, { timeout: REQUEST_TIMEOUT, responseType: 'none' });
  console.log(JSON.stringify({
    type: 'lookup',
    iteration: i,
    sentAtMs: sentAt.getTime(),
    status: res.status,
    errorCode: res.error_code,
    elapsedMs: Date.now() - sentAt.getTime(),
  }));
}
