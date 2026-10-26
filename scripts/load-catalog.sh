#!/usr/bin/env bash
# Restores the dummy catalogue in seed/catalog.json on the running stack. docker compose up loads it by itself;
# run this after changing seeded products by hand, or after editing the catalogue.
set -euo pipefail
cd "$(dirname "$0")/.."

docker compose run --rm --no-deps catalog
# Cached copies could be newer than the restored ones; the local caches expire within 5 s.
docker compose exec -T redis redis-cli FLUSHALL > /dev/null
echo "Catalogue restored and Redis flushed"
