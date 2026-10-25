# Products API

Products with historical prices. A product can have many prices over time, but only one price in force per day
and currency. Built with Spring Boot 4 on WebFlux, reactive MongoDB and a Redis cache.

The original statement is in [SPEC.md](SPEC.md). Every design decision, one line each, is in
[docs/DECISIONS.md](docs/DECISIONS.md).

## Running it

Everything (API, MongoDB, Redis and the k6 load test) with one command:

```bash
docker compose up --build
```

Only the API with MongoDB and Redis:

```bash
docker compose up --build app
```

The API listens on `http://localhost:8080`, Swagger UI is at `/swagger-ui.html` and health at `/actuator/health`.

For local development, start MongoDB and Redis in Docker and run the app with the Gradle wrapper (Java 21):

```bash
docker compose up -d mongo redis
sh gradlew bootRun
```

Tests need a running Docker daemon (Testcontainers starts MongoDB and Redis):

```bash
sh gradlew build
```

The wrapper shipped with the challenge has no executable bit, hence `sh gradlew`.

## API

| Method | Path | Result |
|---|---|---|
| `POST` | `/products` | `201`, `Location: /products/{id}`, body `{id, name, description}` |
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
adapters/in/web      controllers, request/response records, input validation, problem details
application          one class per use case, plus the outbound ports ProductRepository and ProductCache
domain               Product, Price, Money, DateRange, ProductId and their invariants (plain Java)
adapters/out/mongo   MongoDB documents, mapping and the ProductRepository implementation
adapters/out/redis   the ProductCache implementation
```

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

Adding a price is a single `findAndModify` that returns the stored document:

```
filter: { _id: id, prices: { $not: { $elemMatch: { currency: c, initDate: { $lte: newEnd }, endDate: { $gte: newInit } } } } }
update: { $push: { prices: newPrice }, $inc: { version: 1 } }
```

MongoDB applies the filter and the update atomically on one document, so two concurrent requests cannot both
pass the check. There are no locks in Java, no transactions and no read-check-write. If nothing matched, a
second query (`exists`) tells a 404 from a 409; that query only runs when the insert is rejected. An integration
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

### WebFlux instead of virtual threads

The statement weights performance and resource usage heavily. With 1 CPU and 1 GB for the app, an event loop with
a fully reactive driver keeps a handful of threads and very little memory per in-flight request. Virtual threads
would also work well with a blocking driver and simpler code. The deciding factors were the reactive MongoDB
driver, which avoids blocking anywhere in the request path, and BlockHound, which proves it in the tests.

## Performance

Startup and footprint:

- Lazy bean initialization and only the `health` actuator endpoint.
- The Boot jar is extracted in the image and a CDS archive is created at build time with a training run.
  Under the container's 1 CPU this cut startup from about 9.4 s to 6.4 s on my machine.
- Serial GC (the app has a single CPU) and `MaxRAMPercentage=75`.
- WARN logging, no per-request log.
- Optional native image: `sh gradlew bootBuildImage -Pnative` (needs Docker; not required for the challenge).

### Load test

`docker compose up --build` waits for the API to be healthy, then k6:

1. seeds 1,000 products with 5 yearly prices each (2020 to 2024),
2. runs 2 minutes: 30 s ramp-up to 200 virtual users, then 90 s steady,
3. mixes 70% price in force, 20% history and 10% new prices (on dates that never overlap),
4. fails if `http_req_failed >= 1%` or `p(95) >= 50 ms`,
5. writes the full report to `perf/results/summary.json`.

The script is [perf/load-test.js](perf/load-test.js). Load parameters can be overridden, for example:

```bash
docker compose run --rm -e VUS=50 -e STEADY=30s benchmark
```

[scripts/seed.sh](scripts/seed.sh) seeds any running instance without k6:

```bash
scripts/seed.sh http://localhost:8080 1000 5
```

Resource limits in `docker-compose.yml` are the ones provided by the challenge for `app` and `benchmark` and are
left untouched. MongoDB and Redis are auxiliary containers, each capped at 0.5 CPU and 1 GB.

### Results

| Metric | Value |
|---|---|
| Startup (`Started ... in`) | _to fill_ |
| Requests per second | _to fill_ |
| p95 latency | _to fill_ |
| p99 latency | _to fill_ |
| Failed requests | _to fill_ |
| App memory under load | _to fill_ |

## Known limits

- A MongoDB document is capped at 16 MB, about 150k embedded prices per product. Past that, the bucket pattern
  (one document per product and period) keeps the same queries.
- Prices are immutable; there are no update or delete endpoints yet. "Updating" would mean closing the current
  price and adding a new one.
- Writes always go to MongoDB, capped at half a CPU; a write-heavy load would saturate it before the app.
- No authentication.
- The history is not paginated; a product's prices are returned in one response.
