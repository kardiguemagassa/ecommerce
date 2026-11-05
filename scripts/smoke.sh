#!/usr/bin/env bash
# Smoke test of the running Compose stack (docker compose --profile app up -d --wait), run by CI and by hand:
# real Keycloak tokens against the API, then the observability pipeline end to end.
#   1. Keycloak serves the "ecommerce" realm; the API refuses anonymous (401) and under-privileged (403) calls
#   2. the shop administrator creates a product, the customer alice@example.com orders it through /api/me
#   3. an error response carries the id of its trace
#   4. the order trace, read back from Tempo, holds the request, its SQL queries and the async confirmation
#   5. the confirmation log lines of that trace reach Loki
# Tokens of the demo users come from scripts/dev-token.sh (password grant on a development-only client).
# Grafana's data source proxy is used for Tempo and Loki (admin/admin, development backend only).
set -euo pipefail

# Settings from the environment, else from .env (CI writes one, a developer has one).
setting() {
  local value="${!1:-}"
  if [ -z "$value" ] && [ -f .env ]; then value=$(grep -E "^$1=" .env | tail -1 | cut -d= -f2-); fi
  [ -n "$value" ] || { echo "FAIL: $1 is not set (environment or .env)" >&2; exit 1; }
  printf '%s' "$value"
}

API=${API_URL:-http://localhost:8080/api}
GRAFANA=${GRAFANA_URL:-http://admin:admin@localhost:3000}/api/datasources/proxy/uid
KEYCLOAK=${KEYCLOAK_URL:-http://localhost:${KEYCLOAK_PORT:-8180}}
ATTEMPTS=${ATTEMPTS:-30}
SHOP_ADMIN=$(setting SHOP_ADMIN_EMAIL)
CUSTOMER=alice@example.com

json() { python3 -c "import json, sys; print(json.load(sys.stdin)$1)"; }
fail() { echo "FAIL: $*" >&2; exit 1; }

# call METHOD PATH TOKEN [JSON]: prints the body, then the HTTP status on the last line
call() {
  local args=(-sS -X "$1" -w '\n%{http_code}' -H 'Content-Type: application/json')
  [ "$3" = none ] || args+=(-H "Authorization: Bearer $3")
  [ $# -lt 4 ] || args+=(-d "$4")
  curl "${args[@]}" "$API$2"
}
status() { tail -1 <<< "$1"; }
body() { sed '$d' <<< "$1"; }
expect() { [ "$(status "$1")" = "$2" ] || fail "$3: HTTP $(status "$1") instead of $2: $(body "$1")"; }

# --- Keycloak: realm and tokens ------------------------------------------------------------------
issuer=$(curl -fsS "$KEYCLOAK/realms/ecommerce/.well-known/openid-configuration" | json "['issuer']")
[ "$issuer" = "$KEYCLOAK/realms/ecommerce" ] || fail "Keycloak realm ecommerce: unexpected issuer $issuer"
echo "OK  Keycloak realm ecommerce, issuer $issuer"

user_token() { ./scripts/dev-token.sh "$1"; }
ADMIN=$(user_token "$SHOP_ADMIN") || fail "no token for the shop administrator"
ALICE=$(user_token "$CUSTOMER") || fail "no token for $CUSTOMER"
echo "OK  tokens for the shop administrator and $CUSTOMER"

# --- Access rules --------------------------------------------------------------------------------------------------
expect "$(call GET /me/cart none)" 401 "anonymous cart"
expect "$(call GET /me/cart invalid.token.value)" 401 "cart with an invalid token"
expect "$(call POST /categories "$ALICE" '{"name":"Not allowed"}')" 403 "category created by a customer"
expect "$(call GET /products none)" 200 "anonymous catalog"
echo "OK  401 without a valid token, 403 for a customer on the catalog, public catalog reads"

# --- An order, through the API as real users ----------------------------------------------------------------------
suffix=$(date +%s%N)
response=$(call POST /categories "$ADMIN" "{\"name\":\"Smoke $suffix\"}")
expect "$response" 201 "category created by the administrator"
category=$(body "$response" | json "['id']")
response=$(call POST /products "$ADMIN" "{\"name\":\"Keyboard\",\"price\":49.90,\"stock\":10,\"categoryId\":$category}")
expect "$response" 201 "product created by the administrator"
product=$(body "$response" | json "['id']")

response=$(call GET /me "$ALICE")
expect "$response" 200 "customer profile"
[ "$(body "$response" | json "['email']")" = "$CUSTOMER" ] || fail "profile e-mail is not $CUSTOMER"
# JSON built first: macOS bash 3.2 mangles escaped quotes inside a quoted command substitution.
item="{\"productId\":$product,\"quantity\":2}"
response=$(call POST /me/cart/items "$ALICE" "$item")
expect "$response" 200 "item added to the cart"
response=$(call POST /me/orders "$ALICE")
expect "$response" 201 "order placed"
order=$(body "$response" | json "['id']")
echo "OK  customer profile from the token, order $order placed through /api/me"

# --- Observability -------------------------------------------------------------------------------------------------
error_trace=$(body "$(call GET /categories/999999999 none)" | json ".get('traceId', '')")
[ -n "$error_trace" ] || fail "404 response without traceId"
echo "OK  error response carries traceId $error_trace"

trace=""
for _ in $(seq 1 "$ATTEMPTS"); do
  trace=$(curl -fsS -G "$GRAFANA/tempo/api/search" \
            --data-urlencode "q={ name = \"order confirmation\" && span.order.id = \"$order\" }" 2>/dev/null \
          | json ".get('traces', [{}])[0].get('traceID', '')" 2>/dev/null) || trace=""
  # Tempo drops the leading zeros of trace ids in search results; logs carry the full 32 hex digits.
  [ -n "$trace" ] && trace=$(printf '%32s' "$trace" | tr ' ' 0)
  [ -n "$trace" ] && break
  sleep 3
done
[ -n "$trace" ] || fail "no trace with the confirmation of order $order in Tempo"

spans=$(curl -fsS "$GRAFANA/tempo/api/traces/$trace" | python3 -c "
import json, sys
names = [s['name'] for b in json.load(sys.stdin)['batches'] for ss in b.get('scopeSpans', []) for s in ss['spans']]
print('\n'.join(names))")
echo "$spans" | grep -q '^http post /api/me/orders$' || fail "trace $trace: no request span"
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
