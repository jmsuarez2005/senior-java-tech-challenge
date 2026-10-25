# Decisions

One line per decision. Format: decision — reason.

- Build stays on the provided Gradle 8.14.3 wrapper (not Maven) — keep the scaffold the challenge ships with; `./gradlew build` is the verify step.
- Spring Boot 4.1.2 (latest stable 4.x at start) — all chosen libraries (springdoc 3.2.0, Testcontainers 2.0.5) support it, no fallback to 3.5.x needed.
- Package root `com.mango.products` — kept from the provided scaffold.
- `docker-compose.yml` keeps the provided 1 CPU / 1 GB limits on `app` and `benchmark` — the statement forbids changing them; only auxiliary containers are added.
- MongoDB runs as an auxiliary container capped at 0.5 CPU / 1 GB with a 256 MB WiredTiger cache — fits the auxiliary-container limit with headroom for connections.
- Serial GC in the container — the app gets 1 CPU, where G1's concurrent threads only add overhead.
- CDS archive created by a training run at image build (`-Dspring.context.exit=onRefresh`) — `AutoCreateSharedArchive` alone would only help from the second start of the same container.
- Boot jar extracted with `-Djarmode=tools` in the image — CDS cannot archive classes from nested jars.
- Lazy initialization on in `default`, off in `test` — fast startup in production, fail-fast wiring in tests.
- Actuator exposes only `health`, which includes the Mongo indicator — the compose healthcheck then means "app and database ready".
- Native image is opt-in via `./gradlew bootBuildImage -Pnative` — Gradle equivalent of a Maven `native` profile; not required to build locally.
- `./gradlew` is invoked as `sh gradlew` in the Dockerfile — the provided wrapper is committed without the executable bit.
