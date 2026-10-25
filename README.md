# Products API

Products with historical prices. A product can have many prices over time, but only one price in force per day
and currency. Built with Quarkus on Java 21 virtual threads and MongoDB, with an optional Redis cache.

The original statement is in [SPEC.md](SPEC.md). Every design decision, one line each, is in
[docs/DECISIONS.md](docs/DECISIONS.md).

## Running it

Everything (API, MongoDB and the k6 load test) with one command:

```bash
docker compose up --build
```

Only the API and MongoDB:

```bash
docker compose up --build app
```

Redis is optional and off by default; see [Load test](#load-test) to switch it on.

The API listens on `http://localhost:8080`, Swagger UI is at `/swagger-ui`, the OpenAPI document at
`/q/openapi` and health at `/actuator/health`.

For local development, run Quarkus dev mode with the Gradle wrapper (Java 21). Dev Services start MongoDB and Redis
in Docker for you:

```bash
sh gradlew quarkusDev
```

Tests need a running Docker daemon for the same reason (Dev Services start MongoDB and Redis):

```bash
sh gradlew build
```

The wrapper shipped with the challenge has no executable bit, hence `sh gradlew`.

## API

| Method | Path | Result |
|---|---|---|
| `POST` | `/products` | `201`, `Location` pointing to `/products/{id}`, body `{id, name, description}` |
| `GET` | `/products/{id}` | `200`, the product |
| `POST` | `/products/{id}/prices` | `201`, the created price |
| `GET` | `/products/{id}/prices?date=YYYY-MM-DD` | `200`, `{"value": 99.99}` |
| `GET` | `/products/{id}/price?date=YYYY-MM-DD` | same as above, preferred form |
| `GET` | `/products/{id}/prices` | `200`, `{name, description, prices: [...]}` ordered by `initDate` |

```bash
curl -s -X POST localhost:8080/products -H 'Content-Type: application/json' \
  -d '{"name":"Zapatillas deportivas","description":"Modelo 2025 edición limitada"}'

curl -s -X POST localhost:8080/products/{id}/prices -H 'Content-Type: application/json' \
  -d '{"value":99.99,"initDate":"2024-01-01","endDate":"2024-06-30"}'

curl -s 'localhost:8080/products/{id}/prices?date=2024-06-30'   # {"value":99.99}
```

### Rules

- **Validity is a closed interval.** `[initDate, endDate]` includes both days, which is how the statement's examples
  read: a price ending on `2024-06-30` is still the price on that day. Consequently a price starting on the same
  day another one ends overlaps it.
- `endDate` may be `null`, meaning "until further notice". `initDate` must be strictly before `endDate`.
- `value` must be positive and cannot have more decimals than its currency allows (`9.999` EUR or `100.5` JPY are
  rejected, never rounded).
- Two prices of the same product and currency cannot overlap. Different currencies have independent timelines.

### Changes and additions to the endpoints

- `GET /products/{id}/price?date=` is added as the RESTful way to ask for *the* price in force: it is a single
  resource, so the path is singular. `GET /products/{id}/prices?date=` is kept unchanged because the automated
  tests use it, and it is marked as legacy in the OpenAPI description.
- `GET /products/{id}` returns the product without its prices.
- Multi-currency (bonus): `POST /products/{id}/prices` accepts an optional `currency` (ISO 4217, default `EUR`),
  and both price reads accept `?currency=`. Responses keep exactly the shape of the statement; the currency is
  implied by the request instead of added to every price.
- `POST /products/{id}/prices` returns no `Location`: a price has no identity of its own, it is reached through
  its product and a date.

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

## Design

### Architecture

Hexagonal, with the dependency rule checked by ArchUnit on every build:

```
adapters/in/web      JAX-RS resource, request/response records, input validation, problem details
application          one class per use case, plus the outbound ports ProductRepository and ProductCache
domain               Product, Price, Money, DateRange, ProductId and their invariants (plain Java)
adapters/out/mongo   MongoDB document mapping and the ProductRepository implementation
adapters/out/redis   the ProductCache implementation (adapters/out/nocache when the cache is off)
config               composition root: picks the cache implementation at startup
```

The architecture paid off when the framework changed. The project started on Spring Boot; moving to Quarkus meant
rewriting the adapters and the wiring, while the domain did not change and the use cases only swapped
`@Service` for `@ApplicationScoped`.

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
- The cache fails open: if Redis is down or slow (500 ms timeout), the request is served from MongoDB and the
  error is logged. Redis is not part of the health check for the same reason.
- Redis runs without persistence, with `maxmemory 256mb`, LRU eviction and a 10-minute TTL as a safety net.
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

### Quarkus and virtual threads, chosen by measurement

The statement weights startup, response times and resource usage, and the app only gets 1 CPU and 1 GB. Three
implementations of the same design were load-tested under identical limits (details in
[perf/RESULTS.md](perf/RESULTS.md)):

| Stack (with Redis) | req/s | p95 | p99 | Startup | Memory |
|---|---|---|---|---|---|
| Spring Boot 4, WebFlux, reactive drivers | ~1,060 | ~250 ms | ~350 ms | 7 s | ~265 MB |
| Spring Boot 4, MVC, virtual threads | ~1,330 | ~207 ms | ~301 ms | 12 s | ~265 MB |
| **Quarkus 3, virtual threads** | **~1,790** | **~161 ms** | **~221 ms** | **2.5 s** | **~150 MB** |

- **Virtual threads over a reactive pipeline.** With one CPU, the cost that matters is CPU per request. The
  imperative code on blocking drivers spent less of it than the reactive operator chains, and it is simpler to read
  and debug. The Mongo and Redis clients were checked for pinning under load and showed none.
- **Quarkus over Spring Boot.** Quarkus resolves dependency injection and most framework setup at build time, so
  less work happens at startup and per request. It started four to five times faster, used half the memory and
  served about a third more requests with a lower tail latency.

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

`docker compose up --build` waits for the API to be healthy, then k6:

1. seeds 1,000 products with 5 yearly prices each (2020 to 2024),
2. runs 2 minutes: 30 s ramp-up to 200 virtual users, then 90 s steady,
3. mixes 70% price in force, 20% history and 10% new prices (on dates that never overlap),
4. fails if `http_req_failed >= 1%` or `p(95) >= 50 ms`,
5. writes the full report to `perf/results/summary-<run>.json`.

The script is [perf/load-test.js](perf/load-test.js). Load parameters can be overridden, for example:

```bash
docker compose run --rm -e VUS=50 -e STEADY=30s benchmark
```

The base infrastructure is the minimum: app, MongoDB and k6. Redis is an improvement you switch on per run:

```bash
RUN=with-redis PRODUCTS_CACHE_ENABLED=true docker compose --profile cache up --build
```

[scripts/seed.sh](scripts/seed.sh) seeds any running instance without k6:

```bash
scripts/seed.sh http://localhost:8080 1000 5
```

Resource limits in `docker-compose.yml` are the ones provided by the challenge for `app` and `benchmark` and are
left untouched. MongoDB and Redis are auxiliary containers, each capped at 0.5 CPU and 1 GB.

### Results

Every run, what changed and how it was measured: [perf/RESULTS.md](perf/RESULTS.md).

## Known limits

- A MongoDB document is capped at 16 MB, about 150k embedded prices per product. Past that, the bucket pattern
  (one document per product and period) keeps the same queries.
- Prices are immutable; there are no update or delete endpoints yet. "Updating" would mean closing the current
  price and adding a new one.
- MongoDB, capped at half a CPU, is the busiest container once the cache is on: writes always go there. It is the
  next thing to scale (a replica for reads that miss the cache, or a write-behind buffer for prices).
- Absolute numbers in `perf/RESULTS.md` come from a 2 CPU machine where the containers compete for the host; the
  comparisons between runs hold, the k6 thresholds (`p95 < 50 ms`) are not met there.
- No authentication.
- The history is not paginated; a product's prices are returned in one response.
