#!/usr/bin/env bash
# Smoke test of the running Compose stack (docker compose --profile app up -d --wait), run by CI and by hand:
# places an order through the API, then checks that the observability pipeline shows it end to end.
#   1. an error response carries the id of its trace
#   2. the order trace, read back from Tempo, holds the request, its SQL queries and the async confirmation
#   3. the confirmation log lines of that trace reach Loki
# Grafana's data source proxy is used for Tempo and Loki (admin/admin, development backend only).
set -euo pipefail

API=${API_URL:-http://localhost:8080/api}
GRAFANA=${GRAFANA_URL:-http://admin:admin@localhost:3000}/api/datasources/proxy/uid
ATTEMPTS=${ATTEMPTS:-30}

json() { python3 -c "import json, sys; print(json.load(sys.stdin)$1)"; }
post() { curl -fsS -H 'Content-Type: application/json' -d "$2" "$API$1"; }
fail() { echo "FAIL: $*" >&2; exit 1; }

suffix=$(date +%s%N)
category=$(post /categories "{\"name\":\"Smoke $suffix\"}" | json "['id']")
product=$(post /products "{\"name\":\"Keyboard\",\"price\":49.90,\"stock\":10,\"categoryId\":$category}" | json "['id']")
customer=$(post /customers "{\"email\":\"smoke-$suffix@example.com\",\"fullName\":\"Smoke Test\"}" | json "['id']")
post "/customers/$customer/cart/items" "{\"productId\":$product,\"quantity\":2}" > /dev/null
order=$(post "/customers/$customer/orders" '{}' | json "['id']")
echo "order $order placed"

error_trace=$(curl -sS "$API/categories/999999999" | json ".get('traceId', '')")
[ -n "$error_trace" ] || fail "404 response without traceId"
echo "OK  error response carries traceId $error_trace"

trace=""
for _ in $(seq 1 "$ATTEMPTS"); do
  trace=$(curl -fsS -G "$GRAFANA/tempo/api/search" \
            --data-urlencode "q={ name = \"order confirmation\" && span.order.id = \"$order\" }" 2>/dev/null \
          | json ".get('traces', [{}])[0].get('traceID', '')" 2>/dev/null) || trace=""
  [ -n "$trace" ] && break
  sleep 3
done
[ -n "$trace" ] || fail "no trace with the confirmation of order $order in Tempo"

spans=$(curl -fsS "$GRAFANA/tempo/api/traces/$trace" | python3 -c "
import json, sys
names = [s['name'] for b in json.load(sys.stdin)['batches'] for ss in b.get('scopeSpans', []) for s in ss['spans']]
print('\n'.join(names))")
echo "$spans" | grep -q '^http post /api/customers/{customerId}/orders$' || fail "trace $trace: no request span"
echo "$spans" | grep -q '^order confirmation$' || fail "trace $trace: no confirmation span"
queries=$(echo "$spans" | grep -c '^query$' || true)
[ "$queries" -gt 0 ] || fail "trace $trace: no SQL span"
echo "OK  trace $trace: request, $queries SQL queries and the async confirmation"

logs=""
for _ in $(seq 1 "$ATTEMPTS"); do
  logs=$(curl -fsS -G "$GRAFANA/loki/loki/api/v1/query_range" \
           --data-urlencode "query={service_name=\"ecommerce\"} | trace_id=\"$trace\"" --data-urlencode "limit=20" 2>/dev/null \
         | python3 -c "
import json, sys
for stream in json.load(sys.stdin)['data']['result']:
    for _, line in stream['values']: print(line)" 2>/dev/null) || logs=""
  echo "$logs" | grep -q "Confirmation of order $order sent" && break
  sleep 3
done
echo "$logs" | grep -q "Confirmation of order $order sent" || fail "no confirmation log of order $order in Loki"
echo "OK  confirmation logs of order $order in Loki, same trace id"
