# Load-test runs

One change per run, same scenario every time: 1,000 products seeded with 5 prices each, then 2 minutes
(30 s ramp-up to 200 VUs, 90 s steady) of 70% price in force, 20% history and 10% new prices.
Container limits are the ones in the statement: app and k6 at 1 CPU / 1 GB, MongoDB and Redis at 0.5 CPU / 1 GB.

Every experiment lives on its own branch, frozen at the exact code that was measured, so any row can be checked
out and run again with `docker compose up --build`. `main` carries the chosen design plus the challenge features.

## Evolution of the component

One line per experiment, with its best configuration (averages of the repeats).

| # | Branch | What changed | req/s | median | p95 | p99 | Startup | Memory |
|---|---|---|---|---|---|---|---|---|
| 1 | [`experiment/01-spring-webflux`](../../../tree/experiment/01-spring-webflux) | Starting point: Spring Boot 4, WebFlux, reactive drivers, optional Redis | 1,060 | 118 ms | 248 ms | 352 ms | 7 s | ~265 MB |
| 2 | [`experiment/02-spring-mvc-virtual-threads`](../../../tree/experiment/02-spring-mvc-virtual-threads) | Spring MVC on virtual threads, blocking drivers | 1,330 | 91 ms | 208 ms | 301 ms | 12 s | ~265 MB |
| 3 | [`experiment/03-quarkus-virtual-threads`](../../../tree/experiment/03-quarkus-virtual-threads) | Quarkus, same design, only adapters rewritten | 1,789 | 72 ms | 161 ms | 221 ms | 2.5 s | ~150 MB |
| 4 | [`experiment/04-quarkus-reactive`](../../../tree/experiment/04-quarkus-reactive) | Quarkus reactive: Mutiny, Vert.x event loop, reactive drivers | 1,984 | 66 ms | 148 ms | 198 ms | 2.3 s | ~150 MB |
| 5 | [`experiment/05-review-fixes`](../../../tree/experiment/05-review-fixes) | Fixes from the technical review, Redis on by default | 1,945 | 68 ms | 152 ms | 201 ms | 2.3 s | ~155 MB |
| 6 | [`experiment/06-near-cache`](../../../tree/experiment/06-near-cache) | In-process Caffeine cache in front of Redis (now on `main`, see below) | 3,290 | 32 ms | 130 ms | 208 ms | 2.7 s | ~160 MB |

Rows 5 and 6 were measured alternately in the same session (control and candidate); rows 1 to 4 earlier. The
machine drifted by about 8% over the day (see run 8c), so compare neighbouring rows rather than distant ones.

## All runs

| Run | Branch | Stack | Cache | req/s | median | p95 | p99 | Errors | Startup |
|---|---|---|---|---|---|---|---|---|---|
| 1 | 01 | WebFlux + reactive drivers | none | 806 | 157 ms | 349 ms | 500 ms | 0% | 6.6 s |
| 2 | 01 | WebFlux + reactive drivers | Redis | 1,149 | 107 ms | 234 ms | 354 ms | 0% | 7.2 s |
| 2b | 01 | WebFlux + reactive drivers (repeat) | Redis | 971 | 128 ms | 261 ms | 350 ms | 0% | |
| 3 | 02 | MVC + virtual threads + blocking drivers | none | 1,095 | 120 ms | 281 ms | 452 ms | 0% | 8.3 s |
| 4 | 02 | MVC + virtual threads + blocking drivers | Redis | 1,343 | 89 ms | 209 ms | 301 ms | 0% | 11.9 s |
| 4b | 02 | MVC + virtual threads (repeat) | Redis | 1,316 | 93 ms | 206 ms | 301 ms | 0% | |
| 5 | 03 | Quarkus + virtual threads + blocking drivers | none | 1,500 | 94 ms | 199 ms | 263 ms | 0% | 2.5 s |
| 5b | 03 | Quarkus + virtual threads (repeat) | none | 1,446 | 97 ms | 200 ms | 262 ms | 0% | |
| 6 | 03 | Quarkus + virtual threads | Redis, default pool | 919 | 109 ms | 443 ms | 665 ms | 0% | 2.7 s |
| 6b | 03 | Quarkus + virtual threads | Redis, pool of 32 | 1,813 | 71 ms | 159 ms | 224 ms | 0% | 2.4 s |
| 6c | 03 | Quarkus + virtual threads (repeat) | Redis, pool of 32 | 1,765 | 73 ms | 163 ms | 218 ms | 0% | |
| 7 | 04 | Quarkus reactive | none | 1,580 | 88 ms | 196 ms | 254 ms | 0% | 2.6 s |
| 7b | 04 | Quarkus reactive (repeat) | none | 1,532 | 90 ms | 192 ms | 240 ms | 0% | |
| 8 | 04 | Quarkus reactive | Redis, pool of 32 | 1,939 | 67 ms | 150 ms | 195 ms | 0% | 2.3 s |
| 8b | 04 | Quarkus reactive (repeat) | Redis, pool of 32 | 2,029 | 65 ms | 146 ms | 201 ms | 0% | |
| 8c | 04 | Run 8 code again, hours later, as a control | Redis, pool of 32 | 1,834 | 71 ms | 155 ms | 199 ms | 0% | 2.6 s |
| 9c | 05 | Review fixes: value size check, delete on failed write, timeouts, circuit breaker | Redis, default on | 1,789 | 71 ms | 158 ms | 212 ms | 0% | 2.7 s |
| 9d | 05 | Same as 9c (repeat) | Redis, default on | 1,740 | 72 ms | 168 ms | 222 ms | 0% | |
| 10 | 05 | Same as 9c without the circuit breaker (local change, not committed) | Redis, default on | 1,825 | 71 ms | 157 ms | 203 ms | 0% | |
| A1 | 05 | Control for the near-cache A/B | Redis | 1,963 | 67 ms | 150 ms | 194 ms | 0% | 2.3 s |
| B1 | 06 | Caffeine near cache in front of Redis | Redis + in-process | 3,342 | 31 ms | 128 ms | 205 ms | 0% | 2.7 s |
| A2 | 05 | Control (repeat) | Redis | 1,927 | 68 ms | 154 ms | 208 ms | 0% | |
| B2 | 06 | Near cache (repeat) | Redis + in-process | 3,237 | 32 ms | 131 ms | 211 ms | 0% | |

App memory under load: 260 to 270 MB with Spring Boot, 135 to 160 MB with Quarkus.

## What changed between runs

- **1 to 2, Redis:** MongoDB stops serving most reads. Throughput +43%, p95 -33%, p99 -29%.
- **1 to 3, virtual threads (branch 02):** the web and persistence adapters rewritten on Spring MVC with blocking
  drivers. Throughput +36%, p95 -20%, p99 -10%. With Redis (2 to 4): throughput +17 to +38%, p99 about -15%.
- **Spring to Quarkus, same virtual-thread design (branch 03):** only the adapters and the wiring change; the domain
  is untouched and the use cases only swap `@Service` for `@ApplicationScoped`. Throughput +35% with and without
  cache, p99 -27 to -42%, startup from 8 to 12 s down to about 2.5 s, memory roughly halved.
- **Run 6, a pool that was too small:** the Quarkus Redis client defaults to 6 connections and 24 waiters. Under 200
  concurrent requests the queue overflowed, the cache failed open on most reads and every request paid for Redis and
  MongoDB. Sizing the pool to 32 connections (run 6b) removed every cache failure.
- **Virtual threads to reactive on Quarkus (branch 04):** ports and use cases return Mutiny `Uni`, endpoints run on
  the Vert.x event loop, MongoDB and Redis use their reactive clients. Without cache the difference is within noise;
  with Redis it is consistent across repeats: throughput +11%, p95 -8%, p99 -10%.
- **Review fixes (branch 05, runs 9c, 9d, 10 against control 8c):** the same run-8 code measured hours later gave
  1,834 req/s instead of about 1,980, so part of any later difference is the machine drifting. Against that
  control, the value size check, the delete on a failed write and the timeouts cost nothing measurable (run 10); the
  circuit breaker costs about 4% of throughput and some p99. A first version with a 250 ms deadline inside the
  breaker tripped on queueing and dropped throughput to about 1,600 req/s; the deadline was removed.
- **Near cache (branch 06, A/B alternated with branch 05):** a Caffeine map in the app holds the built `Product` for
  5 s in front of Redis. Throughput +69%, median -53%, p95 -15%, p99 unchanged. The app container dropped from 89%
  to 60% CPU while serving 70% more requests, and k6 climbed to 88% of its CPU: the load generator is now close to
  being the limit. The slowest requests are most likely the writes, which still go to MongoDB.

In every Spring run the app container sat at its 1 CPU limit, so the comparison is about CPU spent per request.
Quarkus does its dependency injection and most framework setup at build time, so less runs per request and at
startup.

## Cost of the bonus features and observability on `main`

`main` adds OAuth 2.0 (Keycloak plus token validation in the API), update and delete endpoints, history paging,
JSON logs with request ids and Prometheus metrics on top of branch 05. Measuring their cost met a problem: from
run A4 on, a process outside the benchmark (the IDE scanning a large git repository on the host) took 50 to 60% of a
host CPU on and off, and the same control code dropped from about 1,950 to about 800 req/s. Throughput stopped being
comparable between sessions, so these runs compare **app CPU per request**, read from the container's cgroup during
the run, with control and candidate alternated back to back. It is affected far less by other load on the host.

| Run | Code | App CPU per request | req/s | p95 | p99 |
|---|---|---|---|---|---|
| A8 | Control: branch 05 | 858 us | 900 | 289 ms | 374 ms |
| C5 | `main` with per-request HTTP metrics | 1,331 us | 637 | 367 ms | 452 ms |
| A9 | Control (repeat) | 907 us | 871 | 286 ms | 367 ms |
| C6 | `main` with per-request HTTP metrics (repeat) | 1,304 us | 645 | 361 ms | 453 ms |
| D1 | `main` without per-request HTTP metrics (as shipped) | 1,076 us | 733 | 331 ms | 419 ms |
| D2 | D1 without the OIDC extension (experiment only) | 998 us | 772 | 324 ms | 412 ms |

What each piece costs, per request, against a control of about 880 us:

- **Per-request HTTP timers in Micrometer: about 240 us (18%).** They also switch on Vert.x instrumentation for every
  operation. Off by default; one build flag turns them back on. JVM, cache and circuit-breaker metrics stay.
- **The OIDC extension: about 80 us (7%)**, close to noise, even with authentication running only on the endpoints
  that require it. Kept: it is the security requirement.
- **Everything else (JSON logs, request ids, cache counters, paging code): about 120 us (13%).**
- **Proactive authentication**, the Quarkus default, ran the security chain on every request; it was switched off
  before these runs, so it is not in the table. Moving the request id from a JAX-RS filter pair to a router handler
  was done for the same reason; its effect was within noise.
- **Keycloak** itself idles at under 1% CPU but holds about 500 MB, which on the 2 GB test machine leaves less room
  for everything else. It is not on the request path.

The shipped `main` costs about 20% more CPU per request than branch 05, in exchange for authentication, the extra
endpoints and observability.

## Near cache on `main`

The near cache went into `main`, on by default, after the features above. Measured A/B against the same `main` with
Redis only, alternated, without Keycloak (it is not on the request path), on the host affected by the external
process:

| Run | Code | App CPU per request | req/s | median | p95 | p99 |
|---|---|---|---|---|---|---|
| E1 | `main`, Redis only | 1,294 us | 622 | 205 ms | 390 ms | 502 ms |
| F1 | `main`, near cache + Redis | 970 us | 776 | 138 ms | 410 ms | 642 ms |
| E2 | `main`, Redis only (repeat) | 1,270 us | 624 | 206 ms | 382 ms | 491 ms |
| F2 | `main`, near cache + Redis (repeat) | 947 us | 785 | 139 ms | 402 ms | 664 ms |

Throughput +26%, app CPU per request -25%, median -32%, p95 +5%, p99 +31%. Redis CPU halved while MongoDB's went
from about 8% to 13 to 29%. The higher p99 raised a doubt: in a closed loop, a faster app also sends more writes per
second to MongoDB (half a CPU), so the tail could come from queueing there rather than from the cache. Comparing at
the same arrival rate settles it.

**Same load, fixed arrival rate (k6 `RATE=450`, open model, alternated):**

| Run | Code | median | p95 | p99 | Dropped |
|---|---|---|---|---|---|
| G1 | `main`, Redis only | 7.7 ms | 122 ms | 295 ms | 0 |
| H1 | `main`, local cache + Redis | 2.9 ms | 47 ms | 114 ms | 0 |
| G2 | `main`, Redis only (repeat) | 5.7 ms | 86 ms | 158 ms | 0 |
| H2 | `main`, local cache + Redis (repeat) | 2.5 ms | 40 ms | 91 ms | 0 |

At the same load the local cache is faster at every percentile: median -60%, p95 -59%, p99 -55%, and it is the only
configuration under the statement's p95 threshold of 50 ms. The closed-loop p99 increase was the queueing effect,
not the cache. This is why `main` runs with it.

## The near cache does not scale out by itself

The gain comes from keeping a copy of each product inside each app instance, and that is exactly what breaks when
the service scales out. The README explains the implications for microservices and the options; in short:

- **No shared invalidation.** When instance A adds a price, it refreshes its own in-process copy and Redis. Instance
  B keeps serving its own copy until it expires. A client load-balanced across instances can read a new price and
  then the old one again: with a 5 s local TTL, every instance can be up to 5 s behind.
- **For prices that is a correctness problem, not a detail.** A checkout must charge the price that is in force; two
  instances answering different prices for the same product and date is a bug.
- **The TTL is not a fix.** Shortening it trades the inconsistency window for hit ratio; at 1 s most of the gain is
  gone, and the window is still there.
- **The benchmark hides it.** The compose file runs one app instance, where local writes update the only copy that
  exists, so the measurement shows the gain and none of the cost.

What a scalable version needs, in order of increasing guarantees:

1. **Invalidation broadcast:** every write publishes the product id on a Redis pub/sub channel (or Redis 6+ client-side
   caching with tracking) and every instance evicts its local copy. This shrinks the window to the broadcast latency
   but does not close it: a message can arrive late or be lost while an instance reconnects.
2. **Version check on read:** keep the local copy, but compare its `version` with a cheap read of the version in
   Redis on every request. Always consistent, but it brings back one Redis round trip per read and keeps only the
   saving on JSON decoding.
3. **No local copy:** Redis as the only cache (`PRODUCTS_LOCAL_CACHE_ENABLED=false`). Every instance sees the same
   data at the cost measured in runs E1 and E2.

`main` runs with the local cache on because the challenge deploys one instance and grades performance; option 3 is
one environment variable away for a distributed deployment, and option 1 is the next experiment worth running.

## Caveats

- Measured on a 2 CPU / 2 GB Colima VM. The containers' limits add up to 3 CPU, so they compete for the host and
  absolute numbers are pessimistic. Runs compared with each other share the same conditions.
- The k6 thresholds (`p95 < 50 ms`) are not met on this machine in any run.
- k6 reported negative minimum latencies (down to -68 ms) in most Quarkus runs, a sign that the load generator itself
  was short of CPU. Treat absolute latencies from those runs with care; the comparisons share the problem.
- From run A4 on, an unrelated process on the host competed for CPU; those runs compare CPU per request and
  alternate control and candidate, and their throughput must not be compared with earlier runs.
- Each configuration was run once or twice; repeats landed within 3% for virtual threads and Quarkus, within 15% for
  WebFlux. Differences under about 10% should be read as noise.

Raw k6 reports are written to `perf/results/summary-<run>.json` (ignored by git).
