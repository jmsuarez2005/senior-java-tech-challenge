# Products API

Products with historical prices. A product can have many prices over time, but only one price in force per day
and currency. Built with Java 21 on the Quarkus reactive stack (Mutiny on the Vert.x event loop) and MongoDB, with
a Redis cache, OAuth 2.0 client-credentials security for service-to-service calls, structured logs and Prometheus
metrics.

The original statement is in [SPEC.md](SPEC.md). Every design decision, one line each, is in
[docs/DECISIONS.md](docs/DECISIONS.md). Why this stack was chosen is explained at the end, in
[Stack decision](#stack-decision).

## Running it

Everything (API, MongoDB, Redis, Keycloak and the k6 load test) with one command:

```bash
docker compose up --build
```

Only the API with its dependencies:

```bash
docker compose up --build app
```

Redis is on by default. `PRODUCTS_CACHE_ENABLED=false` runs without the cache (see [Load test](#load-test)).

| URL | What |
|---|---|
| `http://localhost:8080/swagger-ui` | Swagger UI: the interactive API documentation (see [Swagger UI](#swagger-ui)) |
| `http://localhost:8080/actuator/health` | Health (MongoDB connection included) |
| `http://localhost:8080/q/metrics` | Prometheus metrics |
| `http://localhost:8180` | Keycloak, the OAuth 2.0 authorization server (admin / admin) |

Keycloak runs on half a CPU, so on a small machine the first start of the whole stack takes one to three minutes;
the API itself starts in about 2.5 s once its dependencies are up.

For local development, start Keycloak from the compose file and run Quarkus dev mode with the Gradle wrapper
(Java 21); Dev Services start MongoDB and Redis in Docker for you:

```bash
docker compose up -d keycloak
sh gradlew quarkusDev
```

Tests need a running Docker daemon (Dev Services start MongoDB and Redis; OAuth is tested against a mock
authorization server):

```bash
sh gradlew build
```

The wrapper shipped with the challenge has no executable bit, hence `sh gradlew`.

## API

The statement's four endpoints are served **exactly as specified, at their original paths**, because the
recruiter's automated tests call them there; they never change. Next to them, the same API is versioned under
`/v1` with the improvements. Both share one implementation, so they answer the same way. The full reference, with
every parameter, schema, example and error, is in [Swagger UI](#swagger-ui), where each set is its own group.

**Challenge endpoints** (unchanged):

| Method | Path | Result |
|---|---|---|
| `POST` | `/products` | `201`, `Location` pointing to `/products/{id}`, body `{id, name, description}` |
| `POST` | `/products/{id}/prices` | `201`, the created price |
| `GET` | `/products/{id}/prices?date=YYYY-MM-DD` | `200`, `{"value": 99.99}` |
| `GET` | `/products/{id}/prices` | `200`, `{name, description, prices: [...]}` ordered by `initDate` |

**Products v1** (versioned, with the improvements):

| Method | Path | Result |
|---|---|---|
| `POST` | `/v1/products` | `201`, `Location` pointing to `/products/{id}`, body `{id, name, description}` |
| `GET` | `/v1/products/{id}` | `200`, the product |
| `POST` | `/v1/products/{id}/prices` | `201`, the created price |
| `GET` | `/v1/products/{id}/prices?date=YYYY-MM-DD` | `200`, `{"value": 99.99}` |
| `GET` | `/v1/products/{id}/price?date=YYYY-MM-DD` | same as above, preferred form |
| `GET` | `/v1/products/{id}/prices` | `200`, `{name, description, prices: [...]}` ordered by `initDate`; optional filtering, sorting and paging |
| `PUT` | `/v1/products/{id}/prices/{currency}/{initDate}` | `200`, the price with its new value and/or end date (OAuth, `products:write`) |
| `DELETE` | `/v1/products/{id}/prices/{currency}/{initDate}` | `204` (OAuth, `products:write`) |

```bash
curl -s -X POST localhost:8080/v1/products -H 'Content-Type: application/json' \
  -d '{"name":"Zapatillas deportivas","description":"Modelo 2025 edición limitada"}'

curl -s -X POST localhost:8080/v1/products/{id}/prices -H 'Content-Type: application/json' \
  -d '{"value":99.99,"initDate":"2024-01-01","endDate":"2024-06-30"}'

curl -s 'localhost:8080/v1/products/{id}/prices?date=2024-06-30'   # {"value":99.99}
```

### Rules

- **Validity is a closed interval.** `[initDate, endDate]` includes both days, which is how the statement's examples
  read: a price ending on `2024-06-30` is still the price on that day. Consequently a price starting on the same
  day another one ends overlaps it.
- `endDate` may be `null`, meaning "until further notice". `initDate` must be strictly before `endDate`.
- `value` must be positive and cannot have more decimals than its currency allows (`9.999` EUR or `100.5` JPY are
  rejected, never rounded). Amounts are limited to 18 digits in minor units, which also stops crafted values like
  `1e10000000` from costing CPU.
- Two prices of the same product and currency cannot overlap. Different currencies have independent timelines.

### Changes and additions to the endpoints

The statement says about its four endpoints:

> Los endpoints anteriores se utilizarán en las pruebas automáticas.
> Sin embargo, **si consideras que alguno puede mejorarse para alinearse mejor con la semántica REST**, puedes hacerlo
> libremente, justificándolo en el README de tu proyecto.

Both halves are honoured. The four endpoints are served exactly as specified (same paths without a version prefix,
same verbs, same request and response bodies), so the automated tests pass unchanged; the test
`theStatementsUnversionedPathsStillWork` checks it. Every improvement is added next to them, never in their place,
and justified here:

- `/v1` prefix for the versioned API, so a future incompatible change can ship as `/v2` next to it. The improvements
  below live there; the challenge endpoints keep the statement's paths and behaviour.
- `GET /v1/products/{id}/price?date=` is added as the RESTful way to ask for *the* price in force: it is a single
  resource, so the path is singular. `GET /v1/products/{id}/prices?date=` is kept because the automated tests
  use that form, and Swagger points to the singular one as preferred.
- `GET /v1/products/{id}` returns the product without its prices.
- Multi-currency (bonus): `POST /v1/products/{id}/prices` accepts an optional `currency` (ISO 4217, default `EUR`),
  and both price reads accept `?currency=`. Responses keep exactly the shape of the statement; the currency is
  implied by the request instead of added to every price.
- `POST /v1/products/{id}/prices` returns no `Location`: a price has no identity of its own, it is reached through
  its product and a date.
- Update and delete (bonus): a price is identified by its currency and `initDate`, which are unique because periods
  never overlap. `PUT /v1/products/{id}/prices/{currency}/{initDate}` changes the value and/or the end date in one
  atomic write that re-checks overlaps against the other prices; `DELETE` removes it. Both need an OAuth 2.0 token
  (see [Authentication](#authentication)).
- History filtering, sorting and paging (bonus): `GET /v1/products/{id}/prices` accepts `from` and `to` (prices in force
  in that window), `sort=initDate,asc|initDate,desc`, `page` (zero-based) and `size` (1 to 500). Without them the
  response is exactly the statement's. The body never changes shape: paging goes in an `X-Total-Count` header and
  RFC 8288 `Link` headers with `rel="prev"` and `rel="next"`.

```bash
curl -si 'localhost:8080/v1/products/{id}/prices?from=2024-01-01&sort=initDate,desc&page=0&size=2'
```

### Errors

Errors follow RFC 9457 (`application/problem+json`):

| Status | When |
|---|---|
| `400` | Invalid body or parameters. `errors` lists every offending field with a message. |
| `404` | Unknown product, or no price in force on that date. |
| `409` | The new price overlaps an existing one in the same currency. |

```json
{
  "status": 400,
  "title": "Bad Request",
  "detail": "Request validation failed",
  "errors": [
    { "field": "value", "message": "must have at most 2 decimals for EUR" },
    { "field": "endDate", "message": "must be after initDate and not after 9999-12-31" }
  ]
}
```

## Swagger UI

The API is documented with Swagger UI at **http://localhost:8080/swagger-ui**, and that page is the complete
reference. Endpoints come in two groups: **Challenge endpoints**, the statement's four, unchanged, and **Products**,
the versioned `/v1` API with the improvements. The overview at the top explains the rules (closed intervals, currencies and decimals, no overlap),
versioning, authentication with the test credentials, the error format and paging; every endpoint lists its
parameters, request and response bodies with examples, response headers (`Location`, `X-Total-Count`, `Link`) and
every error it can return, with the `application/problem+json` body and an example. Requests can be sent from the
page.

The overview, the shared error responses and their schemas are written in
[src/main/resources/META-INF/openapi.yaml](src/main/resources/META-INF/openapi.yaml); Quarkus merges them with what
the annotations on the resource describe.

The endpoints that change or delete prices are marked with a lock. To call them from Swagger UI:

1. Click **Authorize**.
2. Under `oauth2 (OAuth2, clientCredentials)` enter client id `products-writer`, client secret `products-writer-secret`
   and tick the `products:write` scope.
3. Click **Authorize**: Swagger UI gets the token from Keycloak and sends it on every protected call.

The same description is available as a machine-readable document at `/q/openapi` (YAML, or JSON with
`?format=json`) for client generation.

## Authentication

Service-to-service calls use **OAuth 2.0 client credentials**, the standard flow between microservices: a calling
service authenticates with its own client id and secret against an authorization server, receives a short-lived JWT
access token and sends it as `Authorization: Bearer <token>`. The API is a resource server: it validates the token's
signature locally against the authorization server's public keys (fetched once and cached), so there is no call to
the authorization server per request.

The authorization server is **Keycloak**, started by `docker compose` with the `products` realm imported from
[keycloak/products-realm.json](keycloak/products-realm.json). It defines two service clients:

| Client | Secret | Scopes | Can |
|---|---|---|---|
| `products-writer` | `products-writer-secret` | `products:write` | change and delete prices |
| `products-reader` | `products-reader-secret` | none | call protected endpoints and get `403` |

These are local test credentials; a real deployment loads secrets from a vault and rotates them.

```bash
TOKEN=$(curl -s -d grant_type=client_credentials -d client_id=products-writer -d client_secret=products-writer-secret \
  http://localhost:8180/realms/products/protocol/openid-connect/token | jq -r .access_token)

curl -s -X PUT localhost:8080/v1/products/{id}/prices/EUR/2024-01-01 -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"value":89.99,"endDate":"2024-03-31"}'
```

What is protected, and why:

- `PUT` and `DELETE` on prices require a valid token with the `products:write` scope: `401` without a token,
  `403` with a token that lacks the scope.
- The four mandatory endpoints stay public by default, because the statement's automated tests call them without a
  token. `PRODUCTS_AUTH_ALL=true` makes every call under `/v1/products` and `/products` require a valid token, which is how it would run in
  production. Health, metrics and Swagger UI stay open.
- Authentication runs only where an endpoint asks for it. The Quarkus default authenticates every request up front,
  which would run the security chain on every public request as well.

## Observability

- **Structured logs.** In production every log line is one JSON object (timestamp, level, logger, message, thread,
  host and `requestId`), ready for a log shipper such as Loki, Elasticsearch or Datadog. Dev mode and tests print
  plain text. Business events are logged at `INFO`: product created, price added, replaced, removed or rejected for
  overlapping. Reads are not logged one by one; their volume is covered by metrics.
- **Request ids.** Every request gets an `X-Request-Id` (the caller's, if it sends one, so a request can be followed
  across services) that appears in its log lines and is echoed in the response.
- **Metrics.** Prometheus format at `/q/metrics`: cache reads by result
  (`products_cache_reads_total{result=hit|miss|error}`), cache write errors, circuit breaker state, and JVM memory,
  GC and threads. Per-endpoint request timers (`http_server_requests_seconds`) are available with
  `-Dquarkus.micrometer.binder.http-server.enabled=true` at build time; they are off by default because they
  measured at about 18% more CPU per request, the largest single cost of the observability added.
- **Logging API.** The code logs through JBoss Logging, the API Quarkus itself uses; Quarkus routes Log4j 2, SLF4J
  and java.util.logging calls into the same log manager, so libraries using Log4j end up in the same JSON stream.
  Using Log4j 2 directly as the backend is not supported by Quarkus and would bypass that.

## Design

### Architecture

Hexagonal, with the dependency rule checked by ArchUnit on every build:

```
adapters/in/web      JAX-RS resource, request/response records, input validation, problem details
application          one class per use case returning Mutiny Uni, plus the outbound ports ProductRepository and ProductCache
domain               Product, Price, Money, DateRange, ProductId and their invariants (plain Java)
adapters/out/mongo   MongoDB document mapping and the ProductRepository implementation
adapters/out/redis   the ProductCache implementation (adapters/out/nocache when the cache is off)
config               composition root: picks the cache implementation at startup
```

The architecture paid off when the stack changed. The project started on Spring Boot; moving to Quarkus meant
rewriting the adapters and the wiring, while the domain did not change and the use cases only swapped
`@Service` for `@ApplicationScoped`. Moving from virtual threads to the reactive model changed the ports and use
cases to return `Uni`; the domain still did not change.

Every endpoint returns a `Uni` and runs on the Vert.x event loop, and all I/O goes through the reactive MongoDB and
Redis clients. Anything that can fail synchronously, such as building a `Price` from the request, runs inside the
pipeline, so every failure reaches the same exception mappers.

A request is validated twice, on purpose. The web adapter checks it first (field constraints plus a cross-field
`@ValidPrice`) and answers with all the violations at once, before any use case runs. The domain then enforces the
same rules as invariants when the objects are built. Both use the same domain predicates, so each rule is written
once.

### MongoDB with embedded prices

One `products` collection; each document holds its prices:

```json
{ "_id": ObjectId, "name": "...", "description": "...", "version": 2,
  "prices": [ { "amountMinor": 9999, "currency": "EUR", "initDate": "2024-01-01", "endDate": "2024-06-30" } ] }
```

Every operation the API needs is a lookup by `_id`, so the default index is the only one. The whole product,
history included, is one document read; the price in force on a date is then picked in memory by the aggregate
(`Product.priceAt`). `version` grows by one with every price added.

### No overlap, enforced atomically

Adding a price is a single `findOneAndUpdate` that returns the stored document:

```
filter: { _id: id, prices: { $not: { $elemMatch: { currency: c, initDate: { $lte: newEnd }, endDate: { $gte: newInit } } } } }
update: { $push: { prices: newPrice }, $inc: { version: 1 } }
```

MongoDB applies the filter and the update atomically on one document, so two concurrent requests cannot both
pass the check. There are no locks in Java, no transactions and no read-check-write. If nothing matched, a
second query tells a 404 from a 409; that query only runs when the insert is rejected. An integration
test fires 50 overlapping inserts at once and expects exactly one to succeed.

The relational equivalent is PostgreSQL's exclusion constraint
(`EXCLUDE USING gist (product_id WITH =, currency WITH =, daterange(init_date, end_date, '[]') WITH &&)`).
It is just as correct and arguably more declarative. MongoDB wins here on the read path: the history and the price
in force are one document lookup with no join, and the prices of a product are always read together.

### Redis cache

The first load test showed MongoDB pinned at its 0.5 CPU limit while the app was mostly idle, with 90% of the
traffic being reads. Products are now cached in Redis, cache-aside, behind the `ProductCache` port:

- A read tries Redis first. On a miss it loads the product from MongoDB and stores it.
- Adding a price stores the product returned by the atomic write, so the next read already sees the new price.
- Every write to the cache is a compare-and-set on `version` (a small Lua script). A slow reader that loaded the
  product before a price was added can never put that older copy back over the newer one.
- If storing the new version after a write fails, the entry is deleted, so a stale price is never served for the
  TTL.
- The cache fails open: if Redis is down or slow (500 ms timeout), the request is served from MongoDB. A circuit
  breaker opens after half of 20 calls fail, so only the first few requests pay the timeout, and a connection to an
  unreachable Redis gives up after 1 s. Stopping Redis under traffic was tested: no request failed, and the worst
  one took 3 s while the circuit was opening. Redis is not part of the health check.
- Redis runs without persistence, with `maxmemory 256mb`, LRU eviction and a 1-minute TTL that bounds staleness
  in the worst case.
- The client pool is sized for the load: 32 connections and 512 waiters. With the defaults (6 and 24) the queue
  overflowed under 200 concurrent requests, the cache failed open on most reads and tail latency tripled.

Redis was chosen over an in-process cache because it stays correct when the API runs on several instances.
The cost is a network round trip and JSON decoding on every read, which an in-process near cache in front of
Redis would remove if profiling shows it matters.

### Open end as a sentinel

A `null` end date is stored as `9999-12-31`. Every comparison, in Java and in MongoDB filters, is then the same
two inequalities with no null branches. The API turns the sentinel back into `null`. Dates are stored as ISO
`yyyy-MM-dd` strings, which compare like dates and carry no time zone.

### Money in minor units

Amounts are stored as a `long` in the currency's minor unit with the ISO currency code. The scale comes from
`Currency.getDefaultFractionDigits()`, not from a fixed 100: JPY has no decimals and KWD has three.
`BigDecimal` only exists at the HTTP boundary and `double` is never used.

### Explicit prices per currency, not conversion

Each currency has its own timeline of explicit prices. Retail prices in another currency are set for that market
(rounded to price points, with local taxes and positioning), not derived from an exchange rate at read time.
Converting on the fly would make a published price change whenever the rate moves.

### Price lists as rules, materialized into this model

Promotions, customer-specific price lists or "10% off this category" are rules evaluated on the write side. Their
result is materialized into these explicit, non-overlapping periods. The read side then stays a lookup, which is
what makes it fast, and any rule engine can be added without touching it.

## Performance

Startup and footprint:

- Quarkus does dependency injection and configuration at build time; the app starts in about 2.5 s inside its
  1 CPU container and uses about 150 MB under load.
- A CDS archive is created at image build with a training run (start, then a graceful stop), so the first start in
  a fresh container already loads classes from it.
- Serial GC (the app has a single CPU) and `MaxRAMPercentage=75`.
- WARN logging, no per-request log, and nothing but health under `/actuator`.
- A native image is possible with Quarkus but not built: it needs GraalVM in the build, and the JVM image already
  starts fast enough for this workload.

### Load test

`docker compose up --build` waits for the API to be healthy, then k6 drives the statement's own endpoints (the ones
the recruiter will test):

1. seeds 1,000 products with 5 yearly prices each (2020 to 2024),
2. runs 2 minutes: 30 s ramp-up to 200 virtual users, then 90 s steady,
3. mixes 70% price in force, 20% history and 10% new prices (on dates that never overlap),
4. fails if `http_req_failed >= 1%` or `p(95) >= 50 ms`,
5. writes the full report to `perf/results/summary-<run>.json`.

The script is [perf/load-test.js](perf/load-test.js). Load parameters can be overridden, for example:

```bash
docker compose run --rm -e VUS=50 -e STEADY=30s benchmark
```

Every run gets a name for its report. To compare against the minimal setup, switch the cache off:

```bash
RUN=no-cache PRODUCTS_CACHE_ENABLED=false docker compose up --build
```

[scripts/seed.sh](scripts/seed.sh) seeds any running instance without k6:

```bash
scripts/seed.sh http://localhost:8080 1000 5
```

Resource limits in `docker-compose.yml` are the ones provided by the challenge for `app` and `benchmark` and are
left untouched. MongoDB and Redis are auxiliary containers, each capped at 0.5 CPU and 1 GB.

### Results

Every run, what changed and how it was measured: [perf/RESULTS.md](perf/RESULTS.md).

## Bonus challenges

| Challenge | Status | Where |
|---|---|---|
| Automated performance test | Done: k6 in `docker compose`, seeding, thresholds, one report per run, every experiment on its own branch | [Load test](#load-test), [perf/RESULTS.md](perf/RESULTS.md) |
| Multiple currencies per price | Done: optional `currency` on writes and reads, one timeline per currency | [Changes and additions](#changes-and-additions-to-the-endpoints) |
| Update or delete prices | Done: `PUT` and `DELETE` on `/v1/products/{id}/prices/{currency}/{initDate}`, atomic overlap check | [Changes and additions](#changes-and-additions-to-the-endpoints) |
| Basic or token authentication | Done: OAuth 2.0 client credentials with Keycloak, JWT validated by the API, scope `products:write` | [Authentication](#authentication) |
| Swagger/OpenAPI documentation | Done: Swagger UI with examples, error codes and OAuth login | [Swagger UI](#swagger-ui) |
| Scripts to seed test data | Done: `scripts/seed.sh`, and k6 seeds 1,000 products before every load run | [Load test](#load-test) |
| Paging, sorting or filtering of the history | Done: `from`, `to`, `sort`, `page`, `size`, with `X-Total-Count` and `Link` headers | [Changes and additions](#changes-and-additions-to-the-endpoints) |

## Known limits

- A MongoDB document is capped at 16 MB, about 150k embedded prices per product. Past that, the bucket pattern
  (one document per product and period) keeps the same queries.
- The near cache measured in branch `experiment/06-near-cache` is the biggest single gain (+69% throughput) and is
  not used, because it is not consistent across instances; see
  [perf/RESULTS.md](perf/RESULTS.md#why-the-near-cache-was-not-adopted-it-does-not-scale-out).
- The mandatory endpoints are public unless `PRODUCTS_AUTH_ALL=true`, so the statement's tests can call them.
- Keycloak runs in dev mode with an embedded database, which is fine for the challenge and not for production.
- MongoDB, capped at half a CPU, is the busiest container once the cache is on: writes always go there. It is the
  next thing to scale (a replica for reads that miss the cache, or a write-behind buffer for prices).
- Absolute numbers in `perf/RESULTS.md` come from a 2 CPU machine where the containers compete for the host; the
  comparisons between runs hold, the k6 thresholds (`p95 < 50 ms`) are not met there.
- Paging of the history happens in memory over the aggregate, which is already read whole; it bounds the response,
  not the read.

## Stack decision

The statement leaves the stack open and puts performance first: fast startup, quick responses and low resource
usage, with the app limited to 1 CPU and 1 GB. The stack was therefore settled by measuring, not up front.

### What was chosen and why

| Piece | Choice | Why |
|---|---|---|
| Language | Java 21 | Current LTS; records, sealed types and pattern matching keep the code short. |
| Framework | Quarkus 3 | Build-time dependency injection: fastest startup and lowest memory of the options measured. |
| Concurrency | Reactive, Mutiny on the Vert.x event loop | Non-blocking end to end; with Redis it beat virtual threads on throughput and tail latency. |
| Database | MongoDB | A product and all its prices are one document: every read is one lookup by `_id`, and the no-overlap rule is one atomic update. |
| Cache | Redis | Takes reads off MongoDB, which only gets half a CPU; shared, so it stays correct with several app instances. |
| Load test | k6 | Scripted scenarios, thresholds and a JSON report, in a small container. |

### How it was decided

The infrastructure started at the minimum the statement allows (app, MongoDB and k6) and each change was measured
on its own under the same limits. The full log is in [perf/RESULTS.md](perf/RESULTS.md).

1. **Spring Boot on WebFlux** was the starting point: a non-blocking stack looked like the natural fit for little
   CPU. The first, smaller run showed MongoDB saturated at its half CPU while the app was mostly idle.
2. **Redis** was added as an auxiliary container. Throughput went up 43% and p99 dropped 29%, and the app itself
   became the limit.
3. **Spring MVC with virtual threads** replaced WebFlux to test whether the reactive machinery paid off. It did
   not: with the app at its CPU limit, the blocking version served more requests with a lower tail latency.
4. **Quarkus with virtual threads** kept the same design and only replaced the adapters. It beat both Spring
   variants in every metric.
5. **Quarkus reactive** (Mutiny, reactive drivers) was measured last, because the reactive model deserved a
   second chance on a different framework. Without cache it tied with virtual threads; with Redis it was
   consistently better, so it is the chosen model.
6. **Review fixes** from a technical lead and an architect review (a CPU denial of service through huge numbers, a
   stale cache after a failed write, missing timeouts) cost about 4% of throughput, all of it the circuit breaker.
7. **A near cache** in the app was the biggest gain of all (+69% throughput) and was rejected: it cannot keep several
   instances consistent, which matters more for prices than speed.


| Branch | Stack (with Redis) | req/s | p95 | p99 | Startup | Memory |
|---|---|---|---|---|---|---|
| `experiment/01-spring-webflux` | Spring Boot 4, WebFlux, reactive drivers | ~1,060 | ~250 ms | ~350 ms | 7 s | ~265 MB |
| `experiment/02-spring-mvc-virtual-threads` | Spring Boot 4, MVC, virtual threads | ~1,330 | ~207 ms | ~301 ms | 12 s | ~265 MB |
| `experiment/03-quarkus-virtual-threads` | Quarkus 3, virtual threads | ~1,790 | ~161 ms | ~221 ms | 2.5 s | ~150 MB |
| `experiment/04-quarkus-reactive` | **Quarkus 3, reactive (Mutiny)** | **~1,980** | **~148 ms** | **~198 ms** | **2.5 s** | **~150 MB** |
| `experiment/05-review-fixes` | + fixes from the technical review | ~1,945 | ~152 ms | ~201 ms | 2.3 s | ~155 MB |
| `experiment/06-near-cache` | + in-process cache in front of Redis (not adopted) | ~3,290 | ~130 ms | ~208 ms | 2.7 s | ~160 MB |

Each branch is frozen at the code that was measured and can be run again with `docker compose up --build`.

Reactive lost on Spring and won on Quarkus. The most likely reason is the layer underneath: Mutiny on Vert.x is
thinner than Reactor on WebFlux. That is why the model was measured again instead of being ruled out.

### Trade-offs accepted

- **Quarkus has a smaller ecosystem than Spring Boot.** Everything this service needs (REST, validation, OpenAPI,
  health, MongoDB, Redis) is covered by official extensions; the gap would matter for a larger system.
- **Reactive code is harder to read than imperative code**, and the application layer now depends on Mutiny
  through the `Uni` in its ports. The gain (about 10% in throughput and p99 with Redis) was judged worth it for a
  challenge that puts performance first; the virtual-thread version stays on the branch
  `experiment/03-quarkus-virtual-threads`.
- **The event loop must never block.** Vert.x warns when it does; the tests and every load-test run were checked
  for those warnings and showed none. Any new library must have a non-blocking client.
- **MongoDB instead of PostgreSQL.** An exclusion constraint would express the no-overlap rule just as well and
  more declaratively; MongoDB was preferred for the read path, where a product and its whole history are one lookup.
- **Redis adds a container and a network hop.** It fails open behind a circuit breaker, so the API works
  the same without it.
- **A shared cache instead of a faster local one.** The near cache would have served 69% more requests, but each
  instance would answer from its own copy for up to its TTL after another instance changed a price. Scaling out
  correctly comes first.
- **Keycloak is a fifth container.** It adds memory (about 500 MB) and startup time to the compose stack, in exchange
  for the standard microservice security model instead of credentials checked inside the API.

The stack can still change cheaply: as described in [Architecture](#architecture), the move to Quarkus only
touched the adapters and the wiring, and the move to reactive added the ports and use cases; the domain never
changed.
