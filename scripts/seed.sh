#!/usr/bin/env bash
# Seeds products with one price per year, starting in 2020.
# Usage: scripts/seed.sh [base-url] [products] [prices-per-product]
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
PRODUCTS="${2:-1000}"
PRICES="${3:-5}"

post() {
  curl -fsS -X POST "$BASE_URL$1" -H 'Content-Type: application/json' -d "$2"
}

for ((n = 1; n <= PRODUCTS; n++)); do
  id=$(post /v1/products "{\"name\":\"Product $n\",\"description\":\"Seed data\"}" | sed -E 's/.*"id":"([^"]+)".*/\1/')
  for ((year = 2020; year < 2020 + PRICES; year++)); do
    post "/v1/products/$id/prices" "{\"value\":$((10 + n % 90)).99,\"initDate\":\"$year-01-01\",\"endDate\":\"$year-12-31\"}" > /dev/null
  done
  echo "$id"
done
