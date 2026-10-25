# Load-test runs

One change per run, same scenario every time: 1,000 products seeded with 5 prices each, then 2 minutes
(30 s ramp-up to 200 VUs, 90 s steady) of 70% price in force, 20% history and 10% new prices.
Container limits are the ones in the statement: app and k6 at 1 CPU / 1 GB, MongoDB and Redis at 0.5 CPU / 1 GB.

## Results

| Run | Stack | Cache | req/s | median | p95 | p99 | Errors | Startup |
|---|---|---|---|---|---|---|---|---|
| 1 | WebFlux + reactive drivers | none | 806 | 157 ms | 349 ms | 500 ms | 0% | 6.6 s |
| 2 | WebFlux + reactive drivers | Redis | 1,149 | 107 ms | 234 ms | 354 ms | 0% | 7.2 s |
| 2b | WebFlux + reactive drivers (repeat) | Redis | 971 | 128 ms | 261 ms | 350 ms | 0% | |
| 3 | MVC + virtual threads + blocking drivers | none | 1,095 | 120 ms | 281 ms | 452 ms | 0% | 8.3 s |
| 4 | MVC + virtual threads + blocking drivers | Redis | 1,343 | 89 ms | 209 ms | 301 ms | 0% | 11.9 s |
| 4b | MVC + virtual threads + blocking drivers (repeat) | Redis | 1,316 | 93 ms | 206 ms | 301 ms | 0% | |
| 5 | Quarkus + virtual threads + blocking drivers | none | 1,500 | 94 ms | 199 ms | 263 ms | 0% | 2.5 s |
| 5b | Quarkus + virtual threads + blocking drivers (repeat) | none | 1,446 | 97 ms | 200 ms | 262 ms | 0% | |
| 6 | Quarkus + virtual threads + blocking drivers | Redis, default pool | 919 | 109 ms | 443 ms | 665 ms | 0% | 2.7 s |
| 6b | Quarkus + virtual threads + blocking drivers | Redis, pool of 32 | 1,813 | 71 ms | 159 ms | 224 ms | 0% | 2.4 s |
| 6c | Quarkus + virtual threads + blocking drivers (repeat) | Redis, pool of 32 | 1,765 | 73 ms | 163 ms | 218 ms | 0% | |
| 7 | Quarkus reactive (Mutiny, Vert.x event loop, reactive drivers) | none | 1,580 | 88 ms | 196 ms | 254 ms | 0% | 2.6 s |
| 7b | Quarkus reactive (repeat) | none | 1,532 | 90 ms | 192 ms | 240 ms | 0% | |
| 8 | Quarkus reactive | Redis, pool of 32 | 1,939 | 67 ms | 150 ms | 195 ms | 0% | 2.3 s |
| 8b | Quarkus reactive (repeat) | Redis, pool of 32 | 2,029 | 65 ms | 146 ms | 201 ms | 0% | |

App memory under load: 260 to 270 MB with Spring Boot, 135 to 155 MB with Quarkus (both models).

## What changed between runs

- **1 to 2, Redis:** MongoDB stops serving most reads. Throughput +43%, p95 -33%, p99 -29%.
- **1 to 3, virtual threads:** same infrastructure, the web and persistence adapters rewritten on Spring MVC with
  blocking drivers (branch `experiment/virtual-threads`). Throughput +36%, p95 -20%, p99 -10%.
- **2 to 4, virtual threads with Redis:** throughput +17 to +38%, p95 -11 to -21%, p99 about -15%, depending on
  which WebFlux run is taken. The virtual-thread runs were also the most repeatable.

- **Spring to Quarkus, same virtual-thread design** (branch `experiment/quarkus`): only the adapters and the wiring
  change; the domain is untouched and the use cases only swap `@Service` for `@ApplicationScoped`. Without cache:
  throughput +35%, p95 -29%, p99 -42%. With Redis: throughput +35%, p95 -23%, p99 -27%. Startup drops from
  8 to 12 s to about 2.5 s and memory roughly halves.
- **Quarkus virtual threads to Quarkus reactive** (branch `experiment/quarkus-reactive`): ports and use cases return
  Mutiny `Uni`, endpoints run on the Vert.x event loop, MongoDB and Redis use their reactive clients; the domain is
  untouched. Without cache the difference is within noise (throughput +6%, p95 -3%, p99 -6%). With Redis it is
  consistent across repeats: throughput +11%, p95 -8%, p99 -10%, and the app container had more CPU left
  (about 67% against 80%). No event-loop blocking warnings in any run.
- **Run 6, a pool that was too small:** the Quarkus Redis client defaults to 6 connections and 24 waiters. Under 200
  concurrent requests the queue overflowed, the cache failed open on most reads and every request paid for Redis and
  MongoDB. Sizing the pool to 32 connections (run 6b) removed every cache failure.

In every Spring run the app container sat at its 1 CPU limit, so the comparison is about CPU spent per request. The
blocking stack does less work per request here: no operator chains, no per-signal scheduling, and the Mongo and
Lettuce sync clients are cheaper per call than their reactive wrappers. It starts slower (Tomcat and the larger
context), which the CDS archive only partly offsets.

Quarkus does its dependency injection and most framework setup at build time, so less runs per request and at
startup. Its app container stayed below its CPU limit (70 to 80%), and MongoDB, capped at half a CPU, became the
busiest container again. That makes MongoDB the next thing to look at.

## Caveats

- Measured on a 2 CPU / 2 GB Colima VM. The containers' limits add up to 3 CPU, so they compete for the host
  and absolute numbers are pessimistic. All runs share the same conditions, so the comparison holds.
- The k6 thresholds (`p95 < 50 ms`) are not met on this machine in any run.
- Each configuration was run once or twice; repeats landed within 3% for virtual threads and Quarkus, within 15% for WebFlux. Differences under about 10% should be read as noise.

Raw k6 reports are written to `perf/results/summary-<run>.json` (ignored by git).
