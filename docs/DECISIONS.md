# Decisions

## Stack

- Quarkus 3.40 (latest stable; 4.0 is still a beta) on its reactive stack: chosen by measurement after Spring Boot 4 on WebFlux, Spring Boot 4 on MVC with virtual threads and Quarkus with virtual threads. Quarkus beat both Spring variants by about a third in throughput, started in 2.5 s instead of 7 to 12 s and used half the memory. The runs are in `perf/RESULTS.md`.
- Reactive (Mutiny on the Vert.x event loop, reactive MongoDB and Redis clients) over virtual threads, both on Quarkus: with Redis it was consistently better across repeats (throughput +11%, p95 -8%, p99 -10%) and left the app more CPU headroom; without cache the two were within noise.
- Reactive WebFlux lost to virtual threads on Spring, yet reactive won on Quarkus, most likely because Mutiny on Vert.x is a thinner layer than Reactor on WebFlux; that is why the model was measured again on the new framework instead of carried over.
- Build stays on the provided Gradle 8.14.3 wrapper (not Maven): keep the scaffold the challenge ships with; `sh gradlew build` is the verify step. Quarkus 4 will need Gradle 9.6, which is a wrapper bump when that upgrade happens.
- `./gradlew` is invoked as `sh gradlew`: the provided wrapper is committed without the executable bit.
- Package root `com.mango.products`: kept from the provided scaffold.

- Every experiment has its own branch (`experiment/01-...` to `experiment/06-...`) frozen at the measured code, so any row of `perf/RESULTS.md` can be run again; `main` carries the chosen design.
- The in-process near cache (branch 06) is on `main` and on by default: it was the best result measured (+69% throughput in branch 06, +26% on `main`) and the challenge runs one instance. At a fixed 450 req/s it is faster at every percentile than Redis alone (p95 39 against 65 ms, p99 92 against 159 ms) and the only experiment branch under p95 50 ms in both rounds; the higher p99 seen in the closed loop came from more writes per second queueing in MongoDB. Its price is consistency across instances (each serves its own copy for up to 5 s after a change made elsewhere); that is documented with pros and cons, and `PRODUCTS_LOCAL_CACHE_ENABLED=false` turns it off for distributed deployments.
- Every configuration was compared again at one fixed arrival rate, in one session: the closed-loop ranking had placed Spring MVC with virtual threads above WebFlux, but at a fixed 450 req/s it saturated (k6 dropped about 2,300 requests it could not send in time, p99 above one second) while WebFlux did not. The local cache won under both models.
- The load test has an open-model mode (`RATE=n`, constant arrival rate) next to the statement's closed loop: comparing configurations at the same load separates a faster component from the extra queueing its speed causes elsewhere.

## Architecture

- Hexagonal layout `domain` / `application` / `adapters.in.web` / `adapters.out.mongo` / `adapters.out.redis`, enforced by ArchUnit: the domain may only depend on `java.*`, adapters never depend on each other.
- `config` is the composition root and the only package allowed to pick adapters; ArchUnit ignores its dependencies on purpose.
- Moving from Spring to Quarkus only rewrote adapters and wiring: the domain did not change, and the use cases only swapped `@Service` for `@ApplicationScoped`. Moving to the reactive model also changed the ports and use cases to return `Uni`; the domain still did not change.
- The outbound ports (`ProductRepository`, `ProductCache`) live in `application.port.out`, next to the use cases that need them, and keep the domain free of persistence concerns.
- Ports and use cases return Mutiny `Uni`, so the application layer depends on Mutiny; the domain stays plain Java. An absent value is a `null` item, Mutiny's idiom, instead of an `Optional` inside a `Uni`.
- Anything that can fail synchronously (building a `Price`, parsing a query parameter) runs inside `Uni.createFrom().item(...)`, so every failure travels down the pipeline and reaches the same exception mappers.
- Use cases are concrete application services, one per use case, with no inbound port interfaces: a single implementation behind an interface adds indirection without a second adapter to justify it.
- `ProductId` is a value object: ids cannot be mixed up with other strings in signatures, and blank ids fail in the domain.

## Domain rules

- `initDate < endDate` strictly (as the statement says): a single-day price is rejected; with closed intervals `[d, d]` would otherwise be valid.
- The open-end sentinel `9999-12-31` lives in `DateRange`: one place maps `null` to the sentinel, so the domain, Mongo filters and the web mapping never special-case open ranges; dates after it are rejected.
- Domain exceptions are a sealed hierarchy without stack traces: expected outcomes (404/409 under load) should not pay for `fillInStackTrace`, and the exception mapper switches over them exhaustively.
- Positivity (`value > 0`) is a `Price` rule, not a `Money` rule: money can be zero or negative in general; a price cannot.
- Currencies without a minor unit (`getDefaultFractionDigits() == -1`, e.g. XAU) are rejected: they have no defined scale for `amountMinor`.
- `Money` checks digits and decimals before rescaling: a body like `{"value": 1e10000000}` used to expand into a ten-million-digit number on the event loop (5.7 s per request, other requests stalled). It is now rejected in about 0.1 s; amounts are limited to 18 digits in minor units.
- The price in force is picked in memory by `Product.priceAt` from the whole aggregate, which is already one document read or one cache hit.

## Persistence

- The no-overlap invariant is guarded by a conditional atomic write exposed by the repository port, not by loading the aggregate: loading, checking and saving would need optimistic locking and retries to be correct under concurrency.
- One `products` collection with prices embedded: every read is by `_id`, so the default `_id` index is the only one needed.
- The plain MongoDB driver with `org.bson.Document` mapping instead of Panache: the adapter needs two queries and one atomic update, and the mapping stays explicit in one class.
- Dates stored as ISO `yyyy-MM-dd` strings: they compare and sort like dates and have no time zone. The alternative, `Date` at midnight UTC, works too but leaks a time component into every query and mapping.
- Ids are ObjectIds generated by the repository (`nextId()`): the aggregate has its identity before it is persisted, and `_id` stays a compact 12-byte key. An id that is not a valid ObjectId is simply an unknown product.
- Overlap check and insert are one `findOneAndUpdate` (`$not $elemMatch` filter + `$push` + `$inc version`) that returns the stored document; an empty result triggers a count only on the rejection path to tell 404 from 409.
- Document size limit (16 MB) caps a product at roughly 150k embedded prices; past that the evolution is the bucket pattern (one document per product and period).
- MongoDB calls have bounded waits (2 s to connect, select a server or read): the driver defaults are 30 s to find a server and no socket timeout.
- MongoDB runs as an auxiliary container capped at 0.5 CPU / 1 GB with a 256 MB WiredTiger cache: fits the auxiliary-container limit with headroom for connections.

## Cache

- Redis cache (added after the first k6 run showed MongoDB pinned at its 0.5 CPU limit while the app sat mostly idle): it takes the 90% of read traffic off MongoDB and, unlike an in-process cache, stays correct with several app instances.
- Redis is on by default (compose starts it and `PRODUCTS_CACHE_ENABLED` defaults to true), so a plain `docker compose up` measures the best configuration; `PRODUCTS_CACHE_ENABLED=false` runs the minimal setup for comparison.
- Cache-aside lives in the application layer behind the `ProductCache` port: reads try the cache, fall back to MongoDB and fill the cache; writes put the product returned by the atomic write.
- Every cache write is a compare-and-set on the aggregate `version` (a small Lua script), so a slow reader can never put back an older copy over a newer one written after a price was added.
- The cache fails open: a Redis error or timeout (500 ms) is treated as a miss, the Redis health check is disabled, and the API keeps serving from MongoDB.
- A circuit breaker (SmallRye Fault Tolerance, programmatic `Guard`) wraps every Redis call: after half of 20 calls fail it opens for 5 s, so a slow or dead Redis costs the timeout only for the first few requests, and the state change is logged once instead of one warning per request.
- No deadline in the breaker itself: a 250 ms one tripped on queueing under load, opened the circuit with Redis healthy and cost throughput; the client timeout plus the breaker are enough.
- A connection to an unreachable Redis gives up after 1 s (`quarkus.redis.tcp.connection-timeout`); the Vert.x default of 60 s made one request hang that long when Redis was stopped.
- If storing the new version after a price is added fails, the entry is deleted: a stale price must never be served for the TTL. If Redis misses the delete too, the TTL of 1 minute bounds the staleness.
- The circuit breaker costs about 4% of throughput and some p99 against the same build without it (runs 9c, 9d and 10); accepted as the price of bounded behaviour when Redis degrades.
- The Redis client pool is 32 connections with 512 waiters: the default (6 and 24) overflowed under 200 concurrent requests and silently sent most reads to MongoDB.
- Redis runs as an auxiliary container (0.5 CPU / 1 GB) without persistence, `maxmemory 256mb` and `allkeys-lru`; entries expire after 1 minute, which bounds staleness in the worst case and costs about 17 extra MongoDB reads per second for 1,000 hot products.
- Products are not cached on creation: the first read fills the entry, so products never read do not take memory.

## API

- Price JSON stays exactly `{value, initDate, endDate}` as in the statement; multi-currency reads take an optional `currency` query parameter (default EUR) instead of adding fields to the mandated responses.
- `GET /products/{id}/price?date=` is the RESTful form (one price in force, a singular resource); `GET /products/{id}/prices?date=` stays because the external tests call it. JAX-RS cannot route on a query parameter, so that one method returns the history without `date` and the price with it.
- Query parameters are parsed in the resource rather than by JAX-RS converters: JAX-RS answers an unconvertible query parameter with 404, and a malformed date must be a 400 that names the field.
- Errors are RFC 9457 problem details (`application/problem+json`); validation failures add an `errors` list of `{field, message}` whether they come from bean validation or from the domain.
- No `Location` on `POST /products/{id}/prices`: a price has no identity of its own, it is addressed through the product and a date.
- Health stays at `/actuator/health` (configured in Quarkus) so the compose healthcheck and existing tooling did not change; nothing else is exposed there.
- The statement's four endpoints are never changed: the recruiter's automated tests call them. They live in their own resource (`ChallengeResource`) at the original paths, with exactly the specified behaviour, and appear first in Swagger as "Challenge endpoints"; the load test drives them.
- Both resources only declare paths, validation and documentation, and delegate to one `ProductEndpoints` class: an earlier version had the challenge resource call the `/v1` resource, which validated every write twice.
- Path versioning for everything else: the same API under `/v1` plus the improvements, so an incompatible change can ship as `/v2` beside it. The challenge resource delegates to the `/v1` one, so both answer the same way and there is no redirect or reroute.
- Swagger is the complete API reference: the overview (rules, auth with test credentials, errors, paging, versioning) and the shared error responses live in `META-INF/openapi.yaml`, endpoint details in annotations, and a test checks both are published.
- Update and delete identify a price by currency and `initDate`, unique because periods never overlap; there is no separate price id to keep in sync. Replacement is one `findOneAndUpdate` with an array filter that re-checks overlaps against the other prices.
- History filtering, sorting and paging run in memory on the aggregate already read whole; the body keeps the statement's shape and paging travels in `X-Total-Count` and RFC 8288 `Link` headers, so default clients see no change.
- Swagger UI is the documentation entry point, with the OAuth client-credentials scheme declared so protected calls can be made from the page; the security scheme Quarkus adds automatically for token authentication is turned off because it duplicated it.

## Validation

- Input is validated at the edge before any use case runs: field constraints plus a class-level `@ValidPrice` for the cross-field rules (ordered period, decimals allowed by the currency). All violations come back in one 400 and nothing reaches the repository.
- `@ValidPrice` calls the domain's own predicates (`DateRange.isValid`, `Money.isRepresentable`), so each rule is written once; the domain constructors keep enforcing the same invariants for any other entry point.
- The validator lives next to the DTOs in `adapters.in.web.dto`: a separate `validation` package would depend on `dto` and vice versa.

## Runtime and container

- Serial GC in the container: the app gets 1 CPU, where G1's concurrent threads only add overhead.
- CDS archive created by a training run at image build (start, then a graceful stop that writes the archive): `AutoCreateSharedArchive` alone would only help from the second start of the same container.
- Native image is not built: it would cut startup further but needs GraalVM in the build and a longer image build; the JVM image already starts in 4 to 5 s.
- Quarkus sizes the event loops from the CPUs it sees (two for the 1 CPU container); no thread pool tuning is needed for request handling.

## Security

- OAuth 2.0 client credentials, the standard service-to-service flow: a separate authorization server (Keycloak in the compose file, realm imported at startup) issues JWTs, and the API only validates them against the server's public keys, with no call per request.
- Keycloak runs in the compose file instead of a hosted free service: it needs no account, works offline and keeps the whole setup reproducible.
- Scope-based authorization: `products:write` is required to change or delete prices; a reader client without it shows the `403` path.
- The four mandatory endpoints stay public by default because the statement's automated tests call them without a token; `PRODUCTS_AUTH_ALL=true` requires a valid token on every `/products` call.
- Authentication is lazy (`quarkus.http.auth.proactive=false`): the Quarkus default ran the security chain on every request, public ones included.
- SmallRye JWT validates the tokens instead of the Quarkus OIDC extension: a resource server only needs signature (JWKS), issuer, expiry and scopes; the OIDC extension, built for applications that also log users in, added work to every request.
- Tests sign their own JWTs with a throwaway key pair in `src/test/resources`, so the real bearer-token path (401, 403, 200) is tested without starting Keycloak or a mock server.

## Observability

- JSON logs in production (plain text in dev and tests), one object per line with the request id, so a log shipper can index them without parsing.
- Business events (product created, price added, replaced, removed, rejected) are logged at INFO; reads are not logged individually, their volume belongs in metrics.
- `X-Request-Id` is set by a Vert.x router handler, taken from the caller when present: cheaper than a JAX-RS filter pair and it covers every route.
- JBoss Logging instead of Log4j 2 as the API: it is what Quarkus uses, and Quarkus bridges Log4j 2, SLF4J and JUL into the same log manager; Log4j 2 as a backend is not supported by Quarkus.
- Prometheus metrics for the JVM, cache reads by result and cache write errors. Per-request HTTP timers are off by default: they cost about 18% CPU per request in the measurements, the largest single cost of the observability. Fault-tolerance metrics are off too, because they are recorded on every Redis call; the circuit breaker logs its state changes instead.

## Tests and load test

- Integration tests use Quarkus Dev Services, which start MongoDB and Redis in containers; they need a Docker daemon and no test-specific container code.
- The event loop must never block: Vert.x logs a warning when it does, and the test suite and every load-test run were checked for those warnings (none). All I/O goes through the reactive MongoDB and Redis clients.
- JUnit default timeout of 60 s: a call that never returns fails the test instead of hanging the build.
- k6 replaces the provided curl benchmark and keeps its service name and resource limits; it seeds through the API in `setup()` with batched requests, so the data goes through the same validation as real traffic.
- Thresholds are scoped to the `mixed` scenario: seeding requests are not part of the measured load.
- Load-test writes use a unique two-day window per test iteration in the future, from k6's global iteration counter: deriving it from the VU number pushed dates past year 9999 when a slow server made k6 start many VUs, and the API rightly rejected them, so a 409 in the results always means a bug, never a test artifact.
