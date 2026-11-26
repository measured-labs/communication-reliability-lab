// 주문 입구(order)에 정해진 도착률(open model)로 보내고, 요청마다 정답 한 줄을 남긴다.
// 실행은 scripts/run.sh가 한다. 정답 줄의 형식: docs/measurement.md "1. 정답".
//
// 받는 값(모두 꼭 준다 — 기본값에 기대지 않는다):
//   SCENARIO         warmup | calibration | duplicate | (task 02) false-timeout | slow-layer | split-timeout | cal-false-timeout | cal-sampler
//                    | (task 03) retry-mix | retry-outage-burst | retry-budget | cal-retry-first | cal-retry-second | cal-retry-outage | cal-retry-burst
//                    | (task 04) cal-idem | idem-retry-mix | idem-dup-seq | idem-dup-conc | idem-plain | idem-reconcile
//                    | (task 04-b) overload-spike | cal-overload | overload-low | overload-edge | overload-burst
//   PHASE            (task 04, 주지 않으면 load) load | resend — resend는 맞추기 단계: ANSWERS_FILE의 결과 모름을 같은 키로 다시 보낸다
//   ANSWERS_FILE     (resend만) 부하 단계의 answers.jsonl 절대 경로. RESEND_RATE(초당)도 준다
//   BASE_URL         예: http://localhost:8081
//   RATE             초당 요청 수
//   DURATION_SECONDS 보내는 시간(초)
//   REQUEST_TIMEOUT  k6의 요청 타임아웃(예: 5s). k6 기본은 60s
//   RUN_ID           requestId 앞에 붙인다(실행마다 다르게)
//   MAX_VUS          사슬 시나리오의 최대 VU(도착률 × 가장 긴 응답 시간보다 크게 — 모자라면 dropped_iterations로 실행 실패)
//   LOOKUP_RATE      slow-layer에서만: 가벼운 조회(GET /orders/{id})의 초당 수
//   SPIKE_RATE, SPIKE_FROM_SECONDS, SPIKE_SECONDS  (task 04-b만) 평소 도착률(RATE) 위에 SPIKE_FROM_SECONDS부터 SPIKE_SECONDS 동안 더하는 초당 수
//   CLOCK_OFFSET_MS  (구간 장애 시나리오만 — task 06) 서비스 서버의 시계 − 이 컴퓨터의 시계(ms, 10진 수 — 실행 앞에 잰 값, 같은 컴퓨터면 0)
import http from 'k6/http';
import exec from 'k6/execution';
import { sleep } from 'k6';

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

// ── task 03 (docs/experiments/task-03-design.md) ── 시도마다 넣는 실패는 X-Attempt-Fault(docs/measurement.md 8-1)로 넣는다.
// 실험 가·다의 섞음: 요청 i의 u = mulberry32(씨앗 + i·0x9e3779b9)의 첫 값(VU 차례와 무관). [u의 위 끝, 지시] — 지어낸 분포다.
export const RETRY_SEED = 20261125;
export const RETRY_MIX = [
  [0.50, ''],
  [0.70, 'payment:fail-first=1'],
  [0.80, 'payment:fail-first=2'],
  [0.85, 'payment:fail-first=3'],
  [0.90, 'payment:fail-first=4'],
  [0.94, 'payment:fail-first=9'],
  [1.00, 'payment:slow-first=1@1500'],
];
export function retryU(i) {
  return mulberry32((RETRY_SEED + Math.imul(i, 0x9e3779b9)) >>> 0)();
}
export function mixAttemptFault(i) {
  const u = retryU(i);
  for (const [p, f] of RETRY_MIX) if (u < p) return f;
  return RETRY_MIX[RETRY_MIX.length - 1][1];
}
// 구간 장애: 시나리오 시작(exec.scenario.startTime, 벽시계 ms)에서 [from, to) ms. 결제 서비스가 입구에 든 시각으로 판정한다.
const OUTAGES = { 'cal-retry-outage': [5000, 10000], 'retry-outage-burst': [10000, 20000], 'retry-budget': [10000, 30000] };
// (task 06) 결제는 그 시각을 **자기 시계로** 판정하므로, 헤더의 from·to를 시계의 어긋남(ms 정수로 반올림 — .5는 0에서 먼 쪽)만큼 옮겨
// 서비스의 시계로 적는다 — 구간이 이 컴퓨터의 시계로 [시작 + from, 시작 + to)에 놓이게. 규칙: docs/measurement.md 12절.
const OUTAGE_CLOCK_SHIFT_MS = OUTAGES[SCENARIO] ? (() => {
  const v = required('CLOCK_OFFSET_MS');
  if (!/^-?[0-9]+(\.[0-9]+)?$/.test(v)) throw new Error(`CLOCK_OFFSET_MS '${v}' 는 10진 수(ms)가 아니다`);
  const x = Number(v);
  return Math.sign(x) * Math.round(Math.abs(x));
})() : 0;
// 무리 지은 도착: BURST_PERIOD_MS마다 BURST_SIZE건을 같은 순간에 보낸다(RATE = BURST_SIZE × 1000 / BURST_PERIOD_MS여야 한다).
// 요청 i는 무리 floor(i / BURST_SIZE)에 들고, 시나리오 시작 + (무리 + 1) × 주기까지 기다렸다 보낸다(k6가 반복을 고르게 시작하므로 기다림은 한 주기 이하).
const BURSTS = ['cal-retry-burst', 'retry-outage-burst'];
export const BURST_SIZE = 20;
export const BURST_PERIOD_MS = 500;
const TASK03 = ['retry-mix', 'retry-outage-burst', 'retry-budget', 'cal-retry-first', 'cal-retry-second', 'cal-retry-outage', 'cal-retry-burst'];
if (BURSTS.includes(SCENARIO) && RATE * BURST_PERIOD_MS !== BURST_SIZE * 1000) throw new Error(`무리 지은 도착은 RATE = ${BURST_SIZE * 1000 / BURST_PERIOD_MS} 이어야 한다`);

function attemptFault(i) {
  if (SCENARIO === 'retry-mix') return mixAttemptFault(i);
  if (SCENARIO === 'cal-retry-first') return 'payment:fail-first=1';
  if (SCENARIO === 'cal-retry-second') return 'payment:fail-first=2';
  const o = OUTAGES[SCENARIO];
  if (o) {
    const st = exec.scenario.startTime;
    return `payment:outage=${st + o[0] + OUTAGE_CLOCK_SHIFT_MS}-${st + o[1] + OUTAGE_CLOCK_SHIFT_MS}`;
  }
  return '';
}

// ── task 04 (docs/experiments/task-04-design.md) ── 키는 헤더 Idempotency-Key(값 = requestId), 끊김은 X-Drop-Fault(docs/measurement.md 9-1).
// 키를 켜든 끄든 같은 요청을 보낸다(꺼진 서비스는 키를 읽지 않는다). 보내는 꼴(send): one 한 번 · seq 같은 요청을 잇달아 두 번 ·
// conc 동시에 두 번(http.batch) · seq-amount 잇달아 두 번, 둘째는 금액 +1.
const TASK04 = ['cal-idem', 'idem-retry-mix', 'idem-dup-seq', 'idem-dup-conc', 'idem-plain', 'idem-reconcile'];
export const dropAt = (svc) => `${svc}:drop-after-commit=1`;
// 보정: 요청 i의 칸 = i % 8
export const CAL_IDEM_SLOTS = [
  { send: 'one' },                                  // 0 정상
  { send: 'one', dropFault: dropAt('order') },      // 1~3 층마다 커밋 뒤 끊김
  { send: 'one', dropFault: dropAt('inventory') },
  { send: 'one', dropFault: dropAt('payment') },
  { send: 'one', attemptFault: 'payment:slow-first=1@1500' }, // 4 재고가 1000ms에 포기, 결제는 1500ms에 처리
  { send: 'seq' },                                  // 5 잇달아 두 번
  { send: 'conc' },                                 // 6 동시에 두 번
  { send: 'seq-amount' },                           // 7 같은 키, 둘째는 금액 +1
];
// 실험 라: 요청 i의 칸 = i % 10
export const RECONCILE_SLOTS = [
  { send: 'one' }, { send: 'one' }, { send: 'one' }, { send: 'one' }, { send: 'one' }, // 0~4 정상
  { send: 'one', attemptFault: 'inventory:slow-first=1@2500' },                        // 5 주문이 2000ms에 포기
  { send: 'one', dropFault: dropAt('order') },
  { send: 'one', dropFault: dropAt('inventory') },
  { send: 'one', dropFault: dropAt('payment') },
  { send: 'one', attemptFault: 'payment:slow-first=1@1500' },                          // 9 재고가 1000ms에 포기
];
function task04Request(i) {
  switch (SCENARIO) {
    case 'cal-idem': return CAL_IDEM_SLOTS[i % CAL_IDEM_SLOTS.length];
    case 'idem-reconcile': return RECONCILE_SLOTS[i % RECONCILE_SLOTS.length];
    case 'idem-dup-seq': return { send: 'seq' };
    case 'idem-dup-conc': return { send: 'conc' };
    case 'idem-plain': return { send: 'one' };
    case 'idem-retry-mix': {
      // task 03 retry-mix와 같은 u·같은 칸. "정상"(u < 0.50)의 위 몫 [0.40, 0.50)만 결제에서 커밋 뒤 끊김
      const u = retryU(i);
      if (u >= 0.40 && u < 0.50) return { send: 'one', dropFault: dropAt('payment') };
      return { send: 'one', attemptFault: mixAttemptFault(i) };
    }
  }
  throw new Error(`task 04가 아닌 시나리오 ${SCENARIO}`);
}

const PHASE = __ENV.PHASE || 'load';
if (PHASE !== 'load' && PHASE !== 'resend') throw new Error(`PHASE '${PHASE}' 는 load | resend`);
if (PHASE === 'resend' && !TASK04.includes(SCENARIO)) throw new Error('resend는 task 04 시나리오에서만');
// 맞추기 단계: 부하 단계의 정답에서 requestId마다 2xx가 하나도 없고 결과 모름(status 0 또는 504)이 하나라도 있는 것을 고른다.
// 다시 보내는 것은 그 requestId의 첫째(copy 1) 정답과 같은 요청(같은 헤더·키·금액).
const RESEND = PHASE === 'resend' ? (() => {
  const byId = new Map();
  for (const line of open(required('ANSWERS_FILE')).split('\n')) {
    if (!line) continue;
    const a = JSON.parse(line);
    if (!byId.has(a.requestId)) byId.set(a.requestId, []);
    byId.get(a.requestId).push(a);
  }
  const out = [];
  for (const list of byId.values()) {
    const ok = list.some((a) => a.status >= 200 && a.status < 300);
    const unknown = list.some((a) => a.status === 0 || a.status === 504);
    if (!ok && unknown) out.push(list.find((a) => a.copy === 1) || list[0]);
  }
  return out.sort((a, b) => a.iteration - b.iteration);
})() : [];

// ── task 04-b (docs/experiments/task-04b-design.md) ── 장애 헤더 없음: 평소 RATE 위에 정해진 구간 동안 SPIKE_RATE를 더한다(부하가 원인).
// 평소 요청은 기본 시나리오(chain), 더하는 요청은 시나리오 spike(startTime = SPIKE_FROM_SECONDS). 정답 줄에 phase(base|spike)와 attemptFault ""를 더한다.
const OVERLOAD = ['overload-spike', 'cal-overload', 'overload-low', 'overload-edge', 'overload-burst'];
// 리뷰 뒤 더 잰 자리 ③(설계 10절): 평소 요청을 OVERLOAD_BURST_PERIOD_MS마다 OVERLOAD_BURST_SIZE건씩 같은 순간에 보낸다(task 03의 무리와 같은 꼴 —
// 요청 i는 무리 floor(i / 크기)에 들고 시나리오 시작 + (무리 + 1) × 주기까지 기다렸다 보낸다). 평균 도착률 = RATE. 구간 요청은 고르게 그대로.
export const OVERLOAD_BURST_SIZE = 30;
export const OVERLOAD_BURST_PERIOD_MS = 2000;
if (SCENARIO === 'overload-burst' && RATE * OVERLOAD_BURST_PERIOD_MS !== OVERLOAD_BURST_SIZE * 1000) throw new Error(`overload-burst는 RATE = ${OVERLOAD_BURST_SIZE * 1000 / OVERLOAD_BURST_PERIOD_MS} 이어야 한다`);
const SPIKE_RATE = OVERLOAD.includes(SCENARIO) ? Number(required('SPIKE_RATE')) : 0;
const SPIKE_FROM_SECONDS = OVERLOAD.includes(SCENARIO) ? Number(required('SPIKE_FROM_SECONDS')) : 0;
const SPIKE_SECONDS = OVERLOAD.includes(SCENARIO) ? Number(required('SPIKE_SECONDS')) : 0;
const SPIKE_PLANNED = SPIKE_RATE * SPIKE_SECONDS;
// 구간 요청의 금액: 평소 요청(1000 + i)과 겹치지 않게
export const SPIKE_AMOUNT_BASE = 100000;

const SCENARIOS = ['warmup', 'calibration', 'duplicate', 'false-timeout', 'slow-layer', 'split-timeout', 'cal-false-timeout', 'cal-sampler', ...TASK03, ...TASK04, ...OVERLOAD];
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
    // VU를 모두 미리 만든다 — 필요한 동시 수(도착률 × 응답 시간)에 딱 맞게 두면 실행 중에 VU를 늘리는 사이
    // 반복이 떨어졌다(cal-sampler 50/s × 2s에서 dropped_iterations 4 — 2026-11-24).
    preAllocatedVUs: MAX_VUS,
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
    preAllocatedVUs: 300,
    maxVUs: 300, // 10/s × k6 요청 타임아웃 20s = 200보다 크게
    gracefulStop: GRACEFUL_STOP,
  };
}

if (OVERLOAD.includes(SCENARIO)) {
  if (!(SPIKE_RATE > 0 && SPIKE_SECONDS > 0 && SPIKE_FROM_SECONDS >= 0 && SPIKE_FROM_SECONDS + SPIKE_SECONDS <= DURATION_SECONDS)) {
    throw new Error('SPIKE_RATE·SPIKE_SECONDS는 양수, 구간은 부하 시간 안이어야 한다');
  }
  scenarios.spike = {
    executor: 'constant-arrival-rate',
    exec: 'spike',
    rate: SPIKE_RATE,
    timeUnit: '1s',
    startTime: `${SPIKE_FROM_SECONDS}s`,
    duration: `${SPIKE_SECONDS}s`,
    preAllocatedVUs: MAX_VUS,
    maxVUs: MAX_VUS,
    gracefulStop: GRACEFUL_STOP,
  };
}

// task 04 맞추기 단계: 부하 시나리오 대신 다시 보내기 하나만(고른 수 n을 RESEND_RATE로 — n이 0이면 실행 스크립트가 이 단계를 건너뛴다)
const RESEND_RATE = PHASE === 'resend' ? Number(required('RESEND_RATE')) : 0;
if (PHASE === 'resend') {
  if (RESEND.length === 0) throw new Error('다시 보낼 결과 모름이 없다 — 실행 스크립트가 건너뛰어야 한다');
  for (const k of Object.keys(scenarios)) delete scenarios[k];
  scenarios.resend = {
    executor: 'constant-arrival-rate',
    exec: 'resend',
    rate: RESEND_RATE,
    timeUnit: '1s',
    duration: `${Math.ceil(RESEND.length / RESEND_RATE)}s`,
    preAllocatedVUs: MAX_VUS,
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

// task 04: 요청 하나(또는 같은 요청 둘)를 보내고 보낸 것마다 정답 한 줄. 정답 줄에 key·attemptFault·dropFault·copy·replayed를 더한다.
function task04Headers(requestId, r) {
  const headers = { 'Content-Type': 'application/json', 'X-Request-Id': requestId, 'Idempotency-Key': requestId };
  if (r.attemptFault) headers['X-Attempt-Fault'] = r.attemptFault;
  if (r.dropFault) headers['X-Drop-Fault'] = r.dropFault;
  return headers;
}
function task04Line(type, i, requestId, r, amount, copy, sentAt, res, elapsedMs) {
  return JSON.stringify({
    type,
    requestId,
    key: requestId,
    fault: '',
    attemptFault: r.attemptFault || '',
    dropFault: r.dropFault || '',
    amount,
    copy,
    iteration: i,
    sentAt: sentAt.toISOString(),
    sentAtMs: sentAt.getTime(),
    status: res.status,
    errorCode: res.error_code,
    error: res.error,
    elapsedMs,
    replayed: res.headers['Idempotent-Replayed'] === 'true', // 저장한 응답을 돌려받았나(판정에 쓰지 않고 기록만)
  });
}
function task04Send(i) {
  const requestId = `${RUN_ID}-${i}`;
  const r = task04Request(i);
  const amount = 1000 + i;
  const params = { headers: task04Headers(requestId, r), timeout: REQUEST_TIMEOUT, responseType: 'none' };
  const url = `${BASE_URL}/orders`;
  if (r.send === 'conc') {
    // 동시에 두 번: 두 요청을 한 batch로(연결 재사용을 끈 실행에서는 새 연결 둘을 함께 맺는다). elapsedMs는 둘 다 끝난 때까지.
    const sentAt = new Date();
    const body = JSON.stringify({ amount });
    const rs = http.batch([['POST', url, body, params], ['POST', url, body, params]]);
    const elapsedMs = Date.now() - sentAt.getTime();
    rs.forEach((res, k) => console.log(task04Line('answer', i, requestId, r, amount, k + 1, sentAt, res, elapsedMs)));
    return;
  }
  const copies = r.send === 'one' ? 1 : 2;
  for (let copy = 1; copy <= copies; copy++) {
    const a = r.send === 'seq-amount' && copy === 2 ? amount + 1 : amount;
    const sentAt = new Date();
    const res = http.post(url, JSON.stringify({ amount: a }), params);
    console.log(task04Line('answer', i, requestId, r, a, copy, sentAt, res, Date.now() - sentAt.getTime()));
  }
}

// task 04 맞추기 단계의 반복 하나: 고른 정답 하나를 같은 헤더·키·금액으로 다시 보낸다. 줄의 type은 "resend".
export function resend() {
  const i = exec.scenario.iterationInTest;
  if (i >= RESEND.length) return;
  const a = RESEND[i];
  const r = { attemptFault: a.attemptFault, dropFault: a.dropFault };
  const params = { headers: task04Headers(a.requestId, r), timeout: REQUEST_TIMEOUT, responseType: 'none' };
  const sentAt = new Date();
  const res = http.post(`${BASE_URL}/orders`, JSON.stringify({ amount: a.amount }), params);
  console.log(task04Line('resend', a.iteration, a.requestId, r, a.amount, 1, sentAt, res, Date.now() - sentAt.getTime()));
}

// task 04-b: 사슬 요청 하나(장애 헤더 없음). 정답 줄은 task 03의 꼴 + phase.
function overloadSend(requestId, amount, i, phase) {
  const sentAt = new Date();
  const res = http.post(`${BASE_URL}/orders`, JSON.stringify({ amount }), {
    headers: { 'Content-Type': 'application/json', 'X-Request-Id': requestId },
    timeout: REQUEST_TIMEOUT,
    responseType: 'none',
  });
  const elapsedMs = Date.now() - sentAt.getTime();
  console.log(JSON.stringify({
    type: 'answer',
    requestId,
    fault: '',
    amount,
    iteration: i,
    sentAt: sentAt.toISOString(),
    sentAtMs: sentAt.getTime(),
    status: res.status,
    errorCode: res.error_code,
    error: res.error,
    elapsedMs,
    attemptFault: '', // 재시도 분석(docs/measurement.md 8-2)이 읽는 칸 — 넣는 실패는 없다
    phase, // base | spike — 분석기가 구간을 가린다(docs/measurement.md 10-2)
  }));
}

// task 04-b: 구간 동안 더하는 요청(시나리오 spike). requestId와 금액이 평소 요청과 겹치지 않는다.
export function spike() {
  const i = exec.scenario.iterationInTest;
  if (i >= SPIKE_PLANNED) return;
  overloadSend(`${RUN_ID}-spike-${i}`, SPIKE_AMOUNT_BASE + i, i, 'spike');
}

export default function () {
  const i = exec.scenario.iterationInTest;
  if (i >= PLANNED) return;
  if (TASK04.includes(SCENARIO)) return task04Send(i);
  if (OVERLOAD.includes(SCENARIO)) {
    if (SCENARIO === 'overload-burst') {
      const wait = exec.scenario.startTime + (Math.floor(i / OVERLOAD_BURST_SIZE) + 1) * OVERLOAD_BURST_PERIOD_MS - Date.now();
      if (wait > 0) sleep(wait / 1000);
    }
    return overloadSend(`${RUN_ID}-${i}`, 1000 + i, i, 'base');
  }
  const { requestId, fault } = pick(i);
  // 요청마다 다른 금액 — 서비스가 금액을 바꾸거나 다른 요청의 금액을 쓰면 분석기가 amountMismatch로 잡는다.
  const amount = 1000 + i;
  const headers = { 'Content-Type': 'application/json', 'X-Request-Id': requestId };
  if (fault) headers['X-Fault'] = fault;
  // task 04: 키가 켜진 서비스의 워밍업은 키가 있어야 한다(없으면 400). 실행 스크립트가 키를 켰을 때만 WARMUP_KEYS=true를 준다.
  if (SCENARIO === 'warmup' && __ENV.WARMUP_KEYS === 'true') headers['Idempotency-Key'] = requestId;
  const task03 = TASK03.includes(SCENARIO);
  const af = task03 ? attemptFault(i) : '';
  if (af) headers['X-Attempt-Fault'] = af;
  if (BURSTS.includes(SCENARIO)) {
    const target = exec.scenario.startTime + (Math.floor(i / BURST_SIZE) + 1) * BURST_PERIOD_MS;
    const wait = target - Date.now();
    if (wait > 0) sleep(wait / 1000);
  }
  const sentAt = new Date();
  const res = http.post(`${BASE_URL}/orders`, JSON.stringify({ amount }), {
    headers,
    timeout: REQUEST_TIMEOUT,
    responseType: 'none', // 본문은 판정에 쓰지 않는다(판정은 DB 행으로)
  });
  const elapsedMs = Date.now() - sentAt.getTime();
  // 응답이 없어도(타임아웃·연결 오류: status 0) 정답 줄은 반드시 남긴다.
  const line = {
    type: 'answer',
    requestId,
    fault,
    amount,
    iteration: i,
    sentAt: sentAt.toISOString(),
    sentAtMs: sentAt.getTime(), // 벽시계(ms) — 서비스의 시각 기록과 같은 시계(task 02 분석이 읽는다)
    status: res.status,
    errorCode: res.error_code,
    error: res.error, // k6의 오류 문장(없으면 ""). errorCode 1000(일반 오류)의 까닭을 가르려고 — 판정에 쓰지 않는다
    elapsedMs,
  };
  if (task03) line.attemptFault = af; // task 03만 — 보낸 X-Attempt-Fault 그대로(""=없음). task 01·02의 정답 줄은 그대로
  console.log(JSON.stringify(line));
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
    error: res.error,
    elapsedMs: Date.now() - sentAt.getTime(),
  }));
}
