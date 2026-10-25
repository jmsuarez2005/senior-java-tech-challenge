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

App memory stayed between 260 and 270 MB in every run.

## What changed between runs

- **1 to 2, Redis:** MongoDB stops serving most reads. Throughput +43%, p95 -33%, p99 -29%.
- **1 to 3, virtual threads:** same infrastructure, the web and persistence adapters rewritten on Spring MVC with
  blocking drivers (branch `experiment/virtual-threads`). Throughput +36%, p95 -20%, p99 -10%.
- **2 to 4, virtual threads with Redis:** throughput +17 to +38%, p95 -11 to -21%, p99 about -15%, depending on
  which WebFlux run is taken. The virtual-thread runs were also the most repeatable.

In every run the app container sat at its 1 CPU limit, so the comparison is about CPU spent per request. The
blocking stack does less work per request here: no operator chains, no per-signal scheduling, and the Mongo and
Lettuce sync clients are cheaper per call than their reactive wrappers. It starts slower (Tomcat and the larger
context), which the CDS archive only partly offsets.

## Caveats

- Measured on a 2 CPU / 2 GB Colima VM. The containers' limits add up to 3 CPU, so they compete for the host
  and absolute numbers are pessimistic. All runs share the same conditions, so the comparison holds.
- The k6 thresholds (`p95 < 50 ms`) are not met on this machine in any run.
- Each configuration was run once, the two cached ones twice. Differences under about 10% should be read as noise.

Raw k6 reports are written to `perf/results/summary-<run>.json` (ignored by git).
