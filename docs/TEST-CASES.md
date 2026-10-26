# Functional test cases

Black-box test cases for every endpoint, automated with [Karate](https://karatelabs.io) in
[src/functionalTest](../src/functionalTest/resources/functional). Each case below is one Karate scenario tagged with
its id (`@TC-030`), so a failing run points straight to the case. Scenario outlines expand one case into several
inputs: 66 cases run as 154 scenarios.

## Strategy

| Level | Tool | What it covers | Runs against |
|---|---|---|---|
| Unit | JUnit, AssertJ | Domain invariants: dates, money, overlap rules | Plain Java |
| Integration | Quarkus tests, RestAssured, Testcontainers | Adapters: MongoDB atomic writes, Redis cache, HTTP mapping, signed test JWTs | Dev Services containers |
| Architecture | ArchUnit | The hexagonal dependency rule | Compiled classes |
| **Functional** | **Karate** | **The API as a client sees it: contracts, business rules, errors, security, caching** | **The running stack** |
| Performance | k6 | Latency, throughput and errors under load | The running stack |

Unit, integration and architecture tests together cover 96% of instructions and 85% of branches
(`build/jacoco-report/index.html`, written by `sh gradlew build`).

The functional suite is the only one that runs against the packaged application with its real dependencies:
the production profile (JSON logs, Quarkus error handling in prod mode), Redis and the local cache, and tokens issued
by Keycloak. Integration tests run in test mode, which is how the defects listed at the end went unnoticed.

Design rules:

- **Independent cases.** Every scenario that writes creates its own product, so cases run in any order and can be
  repeated against the same database.
- **Known data, read only.** The dummy catalogue in [seed/catalog.json](../seed/catalog.json) is loaded with fixed
  ids on every start. The catalogue cases read the same file to know what to expect and never change those products,
  so the catalogue is the single source of truth for the data and for the expected results.
- **Exact contracts.** Responses are compared whole (`match response == {...}`), so an extra or missing field fails.
- **Every error is checked as RFC 9457**: `application/problem+json` with `title`, `status`, `detail` and `instance`,
  plus `errors` naming the field for validation failures.
- **Read after write.** Writes are always followed by a read through the public endpoints, which also checks that the
  caches never serve a stale price.
- **Boundaries first.** Dates on both ends of a period, text at its maximum length, and the day before and after.

## Running it

Start the stack, then run the suite (Java 21, from the project root):

```bash
docker compose up -d --build app keycloak
sh gradlew functionalTest
```

`BASE_URL` (default `http://localhost:8080`) and `AUTH_URL` (Keycloak, default `http://localhost:8180`) point it at
another environment. The HTML report is written to `build/karate-reports/karate-summary.html`. The suite is not part
of `sh gradlew build`, which must run without a deployed stack.

## Cases

Priority: **P1** a statement requirement or a security rule, **P2** a bonus feature or a contract detail, **P3**
robustness. Type: **+** positive, **−** negative, **B** boundary, **S** security.

### `POST /products` ([create-product.feature](../src/functionalTest/resources/functional/challenge/create-product.feature))

| Id | Case | Type | P | Expected |
|---|---|---|---|---|
| TC-001 | Create a product with the statement's body | + | P1 | `201`, body `{id, name, description}`, `id` is 24 hex characters, `Location` ends in `/products/{id}` |
| TC-002 | Create without description | + | P2 | `201`, `description: null` |
| TC-003 | Name missing, empty, blank or null | − | P1 | `400`, problem with `errors[0].field = name` |
| TC-004 | Name 255 / 256 and description 2,000 / 2,001 characters | B | P2 | `201` at the limit, `400` one past it |
| TC-005 | Empty body | − | P2 | `400`, `errors[0].field = body` |
| TC-006 | Malformed JSON | − | P2 | `400`, problem, `detail = Malformed request body` |
| TC-007 | Body that is not JSON (`text/plain`) | − | P3 | `415`, problem |
| TC-008 | Unknown fields in the body | + | P3 | `201`, unknown fields ignored |
| TC-009 | Follow the `Location` header | + | P2 | The product's history, with no prices |

### `POST /products/{id}/prices` ([add-price.feature](../src/functionalTest/resources/functional/challenge/add-price.feature))

| Id | Case | Type | P | Expected |
|---|---|---|---|---|
| TC-010 | Add the statement's price | + | P1 | `201`, `{value: 99.99, initDate, endDate}` |
| TC-011 | `endDate` null or absent | + | P1 | `201`, `endDate: null` |
| TC-012 | `endDate` equal to, the day before, or long before `initDate` | B − | P1 | `400`, `errors[0].field = endDate` |
| TC-013 | Overlap with `[2024-01-01, 2024-06-30]`: same period, starts before, ends after, contains, inside, starts on its last day, ends on its first day, open-ended inside or before | − B | P1 | `409`, problem, history unchanged |
| TC-014 | Periods right before and right after an existing one | B + | P1 | `201`, history ordered by `initDate` |
| TC-015 | After an open-ended price: a closed period, another open-ended one, a period before it | − + | P1 | `409`, `409`, `201` |
| TC-016 | Value zero, negative, 3 decimals in EUR, over 18 digits (`1e10000000`), text, null | − | P1 | `400`, `errors[0]` names `value` with the exact reason |
| TC-017 | `initDate` missing, in another format or not a calendar day (`2024-02-30`); `endDate` in another format | − | P1 | `400` naming the date field |
| TC-018 | Unknown product, id that is not an ObjectId | − | P1 | `404`, problem |
| TC-019 | Empty body | − | P2 | `400`, `errors[0].field = body` |
| TC-020 | A rejected price is not stored | − | P1 | `400`, history still empty |
| TC-021 | Values `0.01`, `99.99`, `1234567.89`, `100` | + | P2 | Read back exactly, no rounding |

### `GET /products/{id}/prices?date=` ([price-in-force.feature](../src/functionalTest/resources/functional/challenge/price-in-force.feature))

Prices: 99.99 on `[2024-01-01, 2024-06-30]`, 199.99 on `[2025-01-01, 2025-06-30]`, 299.99 from `2026-01-01` on.

| Id | Case | Type | P | Expected |
|---|---|---|---|---|
| TC-030 | The statement's example, `date=2024-04-15` | + | P1 | `200`, exactly `{"value": 99.99}` |
| TC-031 | First and last day of each period | B | P1 | The price of that period: both ends are included |
| TC-032 | Before the first price, the day after a period, inside a gap, the day before a period | B − | P1 | `404`, problem |
| TC-033 | Open-ended price on `9999-12-31` | B | P2 | `200`, 299.99 |
| TC-035 | `date` = `2024-13-01`, `2024-02-30`, `15-04-2024`, `yesterday` | − | P1 | `400`, `errors[0].field = date` |
| TC-036 | Unknown product | − | P1 | `404`, problem |
| TC-037 | Read, add a price, read again (the first read fills the caches) | + | P1 | The second read sees the new price |

### `GET /products/{id}/prices` ([price-history.feature](../src/functionalTest/resources/functional/challenge/price-history.feature))

| Id | Case | Type | P | Expected |
|---|---|---|---|---|
| TC-040 | Two prices added newest first | + | P1 | Exactly the statement's shape, ordered by `initDate`, no paging headers |
| TC-041 | Product without prices | B | P2 | `prices: []` |
| TC-042 | Open-ended price | + | P2 | `endDate` present and `null` |
| TC-043 | Unknown product | − | P1 | `404`, problem |
| TC-044 | Read, add a price, read again | + | P1 | The new price appears at once |

### `/v1` products ([products.feature](../src/functionalTest/resources/functional/v1/products.feature))

| Id | Case | Type | P | Expected |
|---|---|---|---|---|
| TC-050 | `POST /v1/products`, then `GET` the `Location` | + | P2 | `Location` ends in `/v1/products/{id}`; the product equals the creation response |
| TC-051 | `GET /v1/products/{unknown}` | − | P2 | `404`, problem |
| TC-052 | `GET /v1/products/{id}/price?date=`, and without `date` | + − | P2 | `{value}`; `400` with `date is required` |
| TC-053 | Price added under `/v1`, read under the statement path | + | P2 | Same data through both |

### Currencies ([currencies.feature](../src/functionalTest/resources/functional/v1/currencies.feature))

| Id | Case | Type | P | Expected |
|---|---|---|---|---|
| TC-060 | Same period in EUR and USD; a USD overlap; reads with and without `currency` | + − | P2 | Both `201`; USD overlap `409`; each currency its own price; EUR by default |
| TC-061 | JPY (0 decimals), KWD (3), USD (2), at and past the limit | B | P2 | `201` at the currency's decimals, `400` past them |
| TC-062 | Unknown currency in the body and in the query | − | P2 | `400` naming `currency` |
| TC-063 | History filtered by `currency=USD` | + | P2 | Only the USD prices |

### History paging, sorting and filtering ([history-paging.feature](../src/functionalTest/resources/functional/v1/history-paging.feature))

| Id | Case | Type | P | Expected |
|---|---|---|---|---|
| TC-070 | `page=0&size=2` of 3 prices | + | P2 | 2 prices, `X-Total-Count: 3`, `Link` with `next` only |
| TC-071 | `page=1&size=2` | + | P2 | Last price, `Link` with `prev` only |
| TC-072 | Page past the end | B | P3 | `prices: []`, `X-Total-Count: 3` |
| TC-073 | `sort=initDate,desc` | + | P2 | Newest first |
| TC-074 | Windows `from`/`to` around, across and outside the prices | + B | P2 | Prices in force in the window, including those that only touch it |
| TC-075 | `size=-1`, `size=501`, `page=-1`, `sort=value,asc`, `from` after `to`, `from` in another format | − | P2 | `400` naming the parameter |
| TC-076 | `size=0`, the documented default | B | P3 | Whole history, no paging headers |

### Changing and deleting prices ([change-prices.feature](../src/functionalTest/resources/functional/v1/change-prices.feature))

Tokens come from Keycloak with the client credentials flow, as a calling service would get them.

| Id | Case | Type | P | Expected |
|---|---|---|---|---|
| TC-080 | `PUT` value and end date with a writer token | + | P1 | `200`; the new value applies up to the new end, nothing after it |
| TC-081 | `PUT` and `DELETE` with no token, a token that is not a JWT, a token without `products:write` | S | P1 | `401`, `401`, `403`; the price is unchanged |
| TC-082 | `PUT` an end date that overlaps the next price | − | P1 | `409`, price unchanged |
| TC-083 | `PUT` an end date that extends the price over its own period | + | P2 | `200` |
| TC-084 | `PUT` and `DELETE` a day where no price starts, or on an unknown product | − | P2 | `404`, problem |
| TC-085 | `PUT` with value zero, end before start, no value, start day in another format | − | P2 | `400` naming the field |
| TC-086 | `DELETE`, read, `DELETE` again | + | P1 | `204`; no price in force; history without it; second delete `404` |
| TC-087 | `DELETE`, then add a price on the same period | + | P2 | `201`: the period is free |
| TC-088 | Scopes in the issued tokens | S | P1 | Writer has `products:write`, reader does not |
| TC-089 | `GET` the product's `ETag`, `PUT` with it, then `PUT` and `DELETE` again with the old one | + − | P2 | `ETag: "2"`; first `PUT` `200` with `ETag: "3"`; the stale ones `412`, problem; the price keeps the first change |

### Dummy catalogue ([seeded-catalog.feature](../src/functionalTest/resources/functional/catalog/seeded-catalog.feature))

Read-only cases over the 8 products loaded at startup from [seed/catalog.json](../seed/catalog.json).

| Id | Case | Type | P | Expected |
|---|---|---|---|---|
| TC-100 | Each of the 8 seeded products, in every currency it has | + | P1 | Product and history equal the catalogue, field by field |
| TC-101 | The statement's example on the seeded product, through the statement paths | + | P1 | `{"value": 99.99}` and exactly the statement's history |
| TC-102 | 15 price-in-force checks: around a sale, after an open end, per currency, JPY and KWD, across a year without price, on a leap day | + B | P1 | The expected value |
| TC-103 | Before the first price, the day after a USD price ends, the year without price, a product without prices | − B | P1 | `404`, problem |
| TC-104 | Paging, sorting and a window over twelve monthly prices | + | P2 | The expected page, order and prices; `X-Total-Count: 12` |

### Platform ([platform.feature](../src/functionalTest/resources/functional/platform/platform.feature))

| Id | Case | Type | P | Expected |
|---|---|---|---|---|
| TC-090 | `GET /actuator/health` | + | P1 | `UP` |
| TC-091 | `X-Request-Id` sent, and not sent | + | P3 | Echoed as sent; assigned when missing |
| TC-092 | Swagger UI and `/q/openapi` | + | P2 | Page served; "Challenge endpoints" listed before "Products"; OAuth scheme with `products:write` |

## Covered at other levels

| Case | Where | Why not here |
|---|---|---|
| 50 concurrent overlapping inserts, exactly one accepted | `MongoProductRepositoryIT` | Needs precise concurrency control; it tests the atomic write in MongoDB |
| Redis down or slow: requests served from MongoDB | Manual run under load, recorded in [DESIGN.md](DESIGN.md#redis-cache) | Needs to stop a container in the middle of the run; not automated |
| A failed cache write removes the entry instead of leaving a stale one | `RedisProductCacheIT` | Needs Redis to fail on demand |
| An older cache entry never replaces a newer one | `NearProductCacheTest`, `RedisProductCacheIT` | Depends on timing between two readers |
| `PRODUCTS_AUTH_ALL=true`: every product call needs a token | Manual check; the behaviour is described in [DESIGN.md](DESIGN.md#authentication) | Needs the stack started with another setting |
| Behaviour under load | k6, `perf/load-test.js` | A performance concern, not a functional one |

## Defects found

The first run against the packaged API found three defects, all fixed, each with a regression test in
`ProductApiIT` and a case here:

| Defect | Case | Fix |
|---|---|---|
| `POST /products` with an empty body answered `500` | TC-005, TC-019 | Bodies are required; `400` naming `body` |
| Malformed JSON, a field of the wrong type (`"value": "abc"`, a date in another format, an unknown currency) and unsupported media types answered `400`/`415` with no body in production | TC-006, TC-007, TC-016, TC-017, TC-062 | Those errors are mapped to problem details naming the field |
| `value: 1e10000000` was rejected for having "more than 2 decimals" | TC-016 | The message now says the value has more than 18 digits |

They went unnoticed because Quarkus answers those errors differently in test mode, where the integration tests run.
