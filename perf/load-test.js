import http from 'k6/http';
import { check, fail } from 'k6';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';

const BASE_URL = __ENV.BASE_URL || 'http://app:8080';
const PRODUCTS = Number(__ENV.PRODUCTS || 1000);
const PRICES_PER_PRODUCT = 5;
const SEED_BATCH = 50;
const JSON_HEADERS = { headers: { 'Content-Type': 'application/json' } };

// Seeded prices cover 2020-01-01..2024-12-31, one year each. Load-test writes start in 2030.
const SEED_FIRST_YEAR = 2020;
const WRITE_EPOCH = Date.UTC(2030, 0, 1);
const DAY_MS = 24 * 60 * 60 * 1000;

const RAMP_UP = __ENV.RAMP_UP || '30s';
const STEADY = __ENV.STEADY || '90s';
const RATE = Number(__ENV.RATE || 0);

// Default: closed loop, each VU sends its next request as soon as the last one answers (the statement's scenario).
// RATE=n: open model at n requests per second whatever the latency, to compare configurations under the same load.
const MIXED = RATE > 0
  ? {
      executor: 'ramping-arrival-rate',
      startRate: 0,
      timeUnit: '1s',
      preAllocatedVUs: 200,
      maxVUs: 400,
      stages: [{ duration: RAMP_UP, target: RATE }, { duration: STEADY, target: RATE }],
    }
  : {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: RAMP_UP, target: Number(__ENV.VUS || 200) },
        { duration: STEADY, target: Number(__ENV.VUS || 200) },
      ],
      gracefulRampDown: '5s',
    };

export const options = {
  setupTimeout: '180s',
  scenarios: { mixed: MIXED },
  thresholds: {
    'http_req_failed{scenario:mixed}': ['rate<0.01'],
    'http_req_duration{scenario:mixed}': ['p(95)<50'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  const ids = [];
  for (let start = 0; start < PRODUCTS; start += SEED_BATCH) {
    const size = Math.min(SEED_BATCH, PRODUCTS - start);
    const created = http.batch(Array.from({ length: size }, (_, i) => [
      'POST', `${BASE_URL}/products`,
      JSON.stringify({ name: `Product ${start + i}`, description: 'Seeded for the load test' }), JSON_HEADERS,
    ]));
    created.forEach((res) => {
      if (res.status !== 201) fail(`product seed failed: ${res.status} ${res.body}`);
      ids.push(res.json('id'));
    });
  }

  const prices = [];
  ids.forEach((id, n) => {
    for (let year = 0; year < PRICES_PER_PRODUCT; year++) {
      prices.push(['POST', `${BASE_URL}/products/${id}/prices`, JSON.stringify({
        value: Number((10 + n % 90 + year + 0.99).toFixed(2)),
        initDate: `${SEED_FIRST_YEAR + year}-01-01`,
        endDate: `${SEED_FIRST_YEAR + year}-12-31`,
      }), JSON_HEADERS]);
    }
  });
  for (let start = 0; start < prices.length; start += SEED_BATCH) {
    http.batch(prices.slice(start, start + SEED_BATCH)).forEach((res) => {
      if (res.status !== 201) fail(`price seed failed: ${res.status} ${res.body}`);
    });
  }
  return { ids };
}

export default function ({ ids }) {
  const id = ids[Math.floor(Math.random() * ids.length)];
  const roll = Math.random();

  if (roll < 0.7) {
    const res = http.get(`${BASE_URL}/products/${id}/prices?date=${randomSeededDate()}`, { tags: { name: 'price-on-date' } });
    check(res, { 'price 200': (r) => r.status === 200 });
  } else if (roll < 0.9) {
    const res = http.get(`${BASE_URL}/products/${id}/prices`, { tags: { name: 'price-history' } });
    check(res, { 'history 200': (r) => r.status === 200 });
  } else {
    const res = http.post(`${BASE_URL}/products/${id}/prices`, JSON.stringify(uniqueFuturePrice()),
      { ...JSON_HEADERS, tags: { name: 'add-price' } });
    check(res, { 'add price 201': (r) => r.status === 201 });
  }
}

function randomSeededDate() {
  const start = Date.UTC(SEED_FIRST_YEAR, 0, 1);
  const days = Math.floor(Math.random() * PRICES_PER_PRODUCT * 365);
  return isoDate(start + days * DAY_MS);
}

// Every (VU, iteration) gets its own two-day window, so concurrent writes never overlap.
function uniqueFuturePrice() {
  const slot = __VU * 5000 + __ITER;
  const init = WRITE_EPOCH + slot * 2 * DAY_MS;
  return { value: 49.99, initDate: isoDate(init), endDate: isoDate(init + DAY_MS) };
}

function isoDate(epochMs) {
  return new Date(epochMs).toISOString().slice(0, 10);
}

export function handleSummary(data) {
  return {
    [`/results/summary-${__ENV.RUN || 'latest'}.json`]: JSON.stringify(data, null, 2),
    stdout: textSummary(data, { indent: ' ', enableColors: false }),
  };
}
