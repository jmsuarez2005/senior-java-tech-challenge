#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

docker compose run --rm --no-deps catalog
docker compose exec -T redis redis-cli FLUSHALL > /dev/null
echo "Catalogue restored and Redis flushed"
