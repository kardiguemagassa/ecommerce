#!/usr/bin/env bash
# Smoke test of the running Compose stack (docker compose --profile app up -d --wait), run by CI and by hand:
# everything through the web entry point (nginx on http://localhost:8080), as a browser would, with real Keycloak
# tokens, then the observability pipeline end to end.
#   1. Keycloak serves the "ecommerce" realm; the API refuses anonymous (401) and under-privileged (403) calls
#   2. the shop administrator creates a product, the customer alice@example.com orders it through /api/me
#   3. the browser login of the BFF: Keycloak login form, back on the front-end, session cookie, CSRF on writes,
#      logout at Keycloak
#   4. images: product image and avatar uploaded, re-encoded as JPEG, served read-only under /media, deleted
#   5. the web entry point: front-end routes, security headers
#   6. an error response carries the id of its trace
#   7. the order trace, read back from Tempo, holds the request, its SQL queries and the async confirmation
#   8. the confirmation log lines of that trace reach Loki
#   9. image uploads are rate limited (429): last, not to slow the other checks down
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

BASE=${BASE_URL:-http://localhost:8080}
API=$BASE/api
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

# --- Customer support (administrators) ---------------------------------------------------------------------------
response=$(call GET "/admin/customers?q=${CUSTOMER%%@*}" "$ADMIN")
expect "$response" 200 "customer search by the administrator"
customer=$(body "$response" | json "['content'][0]['id']")
[ "$(body "$response" | json "['content'][0]['email']")" = "$CUSTOMER" ] || fail "customer search: $CUSTOMER not found"
response=$(call GET "/admin/customers/$customer/orders" "$ADMIN")
expect "$response" 200 "orders of the customer"
[ "$(body "$response" | json "['content'][0]['id']")" = "$order" ] || fail "orders of the customer: latest is not order $order"
expect "$(call GET /admin/customers "$ALICE")" 403 "customer search by a customer"
echo "OK  customer support: $CUSTOMER found by the administrator with order $order on top, 403 for a customer"

# --- Browser login through the API (BFF), as a browser would do it ----------------------------------------------
jar=$(mktemp)
image=$(mktemp)
trap 'rm -f "$jar" "$image"' EXIT
browser() { curl -sS -c "$jar" -b "$jar" "$@"; }
form_action() { python3 -c "import html, re, sys; m = re.search(r'action=\"([^\"]+)\"', sys.stdin.read()); print(html.unescape(m.group(1)) if m else '')"; }

action=$(browser -L "$BASE/oauth2/authorization/keycloak" | form_action)
[ -n "$action" ] || fail "BFF login: no Keycloak login form"
landing=$(browser -L -o /dev/null -w '%{url_effective}' --data-urlencode "username=$CUSTOMER" \
  --data-urlencode "password=$(setting DEMO_USER_PASSWORD)" "$action")
[ "$landing" = "$BASE/" ] || fail "BFF login: landed on $landing instead of the front-end $BASE/"
session=$(browser "$API/me/session")
[ "$(json "['authenticated']" <<< "$session")" = True ] || fail "BFF login: not signed in after the login form: $session"
[ "$(json "['email']" <<< "$session")" = "$CUSTOMER" ] || fail "BFF login: session of another user: $session"
grep -q 'JSESSIONID' "$jar" || fail "BFF login: no session cookie"
xsrf=$(awk '$6 == "XSRF-TOKEN" { print $7 }' "$jar" | tail -1)
[ -n "$xsrf" ] || fail "BFF login: no XSRF-TOKEN cookie"
echo "OK  BFF login of $CUSTOMER: Keycloak form, back on the front-end, session cookie, no token in the browser"

item="{\"productId\":$product,\"quantity\":1}"
code=$(browser -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/json' -d "$item" "$API/me/cart/items")
[ "$code" = 403 ] || fail "BFF session write without CSRF token: HTTP $code instead of 403"
code=$(browser -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $xsrf" \
  -d "$item" "$API/me/cart/items")
[ "$code" = 200 ] || fail "BFF session write with CSRF token: HTTP $code instead of 200"
echo "OK  session writes need the CSRF token (403 without, 200 with)"

quantity=$(browser -X PUT -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $xsrf" -d '{"quantity":3}' \
  "$API/me/cart/items/$product" | json "['totalQuantity']")
[ "$quantity" = 3 ] || fail "cart quantity change: $quantity items instead of 3"
code=$(browser -o /dev/null -w '%{http_code}' -X PUT -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $xsrf" \
  -d '{"quantity":11}' "$API/me/cart/items/$product")
[ "$code" = 409 ] || fail "cart quantity above stock: HTTP $code instead of 409"
code=$(browser -o /dev/null -w '%{http_code}' -X DELETE -H "X-XSRF-TOKEN: $xsrf" "$API/me/cart")
[ "$code" = 204 ] || fail "cart cleared: HTTP $code instead of 204"
case "$(browser "$API/me/session" | json "['accountUrl']")" in
  "$KEYCLOAK"/realms/ecommerce/account) ;;
  *) fail "session: no Keycloak account console link" ;;
esac
echo "OK  cart quantity set (200, 409 above stock), cart cleared, account console link in the session"

logout=$(browser -X POST -H "X-XSRF-TOKEN: $xsrf" "$BASE/logout")
case "$(json "['logoutUrl']" <<< "$logout")" in
  "$KEYCLOAK"/realms/ecommerce/protocol/openid-connect/logout\?*id_token_hint=*) ;;
  *) fail "BFF logout: unexpected answer $logout" ;;
esac
[ "$(browser "$API/me/session" | json "['authenticated']")" = False ] || fail "BFF logout: session still open"
echo "OK  logout closes the session and points to the Keycloak end-session endpoint"

# --- Images: uploaded through the API, read from the media storage ------------------------------------------------
# upload PATH TOKEN FILE: multipart PUT, prints the body, then the HTTP status on the last line
upload() { curl -sS -X PUT -w '\n%{http_code}' -H "Authorization: Bearer $2" -F "file=@$3" "$API$1"; }
# A 800 x 400 PNG, written with the Python standard library.
python3 -c "
import struct, sys, zlib
w, h = 800, 400
rows = b''.join(b'\\x00' + bytes([200, 80, 40]) * w for _ in range(h))
chunk = lambda kind, data: struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))
sys.stdout.buffer.write(b'\\x89PNG\\r\\n\\x1a\\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0))
                       + chunk(b'IDAT', zlib.compress(rows)) + chunk(b'IEND', b''))" > "$image"

response=$(upload "/products/$product/image" "$ALICE" "$image")
expect "$response" 403 "product image uploaded by a customer"
response=$(upload "/products/$product/image" "$ADMIN" "$image")
expect "$response" 200 "product image uploaded by the administrator"
image_url=$(body "$response" | json "['imageUrl']")
[ "$(curl -fsS "$API/products/$product" | json "['imageUrl']")" = "$image_url" ] || fail "product read without its new image"
case "$image_url" in /media/products/*.jpg) ;; *) fail "product image URL $image_url is not under /media" ;; esac
content_type=$(curl -fsS -o /dev/null -w '%{content_type}' "$BASE$image_url") || fail "product image not served at $image_url"
[ "$content_type" = image/jpeg ] || fail "product image served as $content_type instead of image/jpeg"
code=$(curl -sS -o /dev/null -w '%{http_code}' -X PUT --data 'overwritten' "$BASE$image_url")
case "$code" in 2*) fail "media writable without credentials: PUT $image_url answered $code" ;; esac
echo "OK  product image stored and re-encoded as JPEG, served read-only at $image_url"

echo 'not an image' > "$image.txt"
response=$(upload /me/avatar "$ALICE" "$image.txt"); rm -f "$image.txt"
expect "$response" 415 "text file sent as avatar"
response=$(upload /me/avatar "$ALICE" "$image")
expect "$response" 200 "avatar uploaded"
avatar_url=$(body "$response" | json "['avatarUrl']")
curl -fsS -o /dev/null "$BASE$avatar_url" || fail "avatar not served at $avatar_url"
expect "$(call DELETE /me/avatar "$ALICE")" 204 "avatar deleted"
code=$(curl -sS -o /dev/null -w '%{http_code}' "$BASE$avatar_url")
[ "$code" != 200 ] || fail "deleted avatar still served at $avatar_url"
[ "$(body "$(call GET /me "$ALICE")" | json "['avatarUrl']")" = None ] || fail "profile still has an avatar"
echo "OK  avatar: other files refused (415), stored, then erased from the storage"

# --- Web entry point: front-end routes and security headers -------------------------------------------------------
headers=$(curl -fsS -D - -o /dev/null "$BASE/admin") || fail "front-end route /admin not served"
grep -qi '^content-type: text/html' <<< "$headers" || fail "/admin is not the front-end page"
grep -qi "^content-security-policy: default-src 'self'; script-src 'self'" <<< "$headers" || fail "front-end without its CSP"
grep -qi '^x-content-type-options: nosniff' <<< "$headers" || fail "front-end without X-Content-Type-Options"
grep -qi '^server: nginx/' <<< "$headers" && fail "nginx version disclosed"
echo "OK  front-end routes served by nginx with CSP and security headers"

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

# --- Rate limit of image uploads (last: the window then stays full for a minute) --------------------------------
echo 'not an image' > "$image"
limited=""
for _ in $(seq 1 15); do
  response=$(upload /me/avatar "$ALICE" "$image")
  if [ "$(status "$response")" = 429 ]; then limited=$(body "$response"); break; fi
done
[ -n "$limited" ] || fail "15 avatar uploads in a row were not rate limited"
[ "$(json "['code']" <<< "$limited")" = RATE_LIMITED ] || fail "rate limit answer is not a RATE_LIMITED problem: $limited"
echo "OK  image uploads rate limited by nginx (429 RATE_LIMITED)"
