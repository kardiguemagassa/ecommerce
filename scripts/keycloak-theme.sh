#!/usr/bin/env bash
# Applies the look of the shop to the login pages of an existing "ecommerce" realm (idempotent):
#   ./scripts/keycloak-theme.sh
# A new realm gets it from keycloak/realm-ecommerce.json; an existing one is never overwritten by the import, so this
# sets the same values through the admin API: theme "comptoir", name "Comptoir", "Rester connecté", and the shop's
# address for the "Retour à la boutique" link. Nothing is deleted, no volume is touched.
set -euo pipefail

setting() {
  local value="${!1:-}"
  if [ -z "$value" ] && [ -f .env ]; then value=$(grep -E "^$1=" .env | tail -1 | cut -d= -f2-); fi
  [ -n "$value" ] || { echo "$1 is not set (environment or .env)" >&2; exit 1; }
  printf '%s' "$value"
}

KEYCLOAK=${KEYCLOAK_URL:-http://localhost:${KEYCLOAK_PORT:-8180}}
REALM_API="$KEYCLOAK/admin/realms/ecommerce"
SHOP_URL=${SHOP_URL:-http://localhost:${WEB_PORT:-8080}/}

json() { python3 -c "import json, sys; print(json.load(sys.stdin)$1)"; }

admin_token=$(curl -fsS --data-urlencode grant_type=password --data-urlencode client_id=admin-cli \
  --data-urlencode "username=$(setting KEYCLOAK_ADMIN_USERNAME)" \
  --data-urlencode "password=$(setting KEYCLOAK_ADMIN_PASSWORD)" \
  "$KEYCLOAK/realms/master/protocol/openid-connect/token" | json "['access_token']")
auth=(-H "Authorization: Bearer $admin_token" -H 'Content-Type: application/json')

# Only these fields: the rest of the realm is left as it is.
curl -fsS -X PUT "${auth[@]}" "$REALM_API" \
  -d '{"displayName":"Comptoir","displayNameHtml":"Comptoir","loginTheme":"comptoir","rememberMe":true}' > /dev/null

client=$(curl -fsS "${auth[@]}" "$REALM_API/clients?clientId=ecommerce-bff" | json "[0]['id']")
curl -fsS "${auth[@]}" "$REALM_API/clients/$client" \
  | SHOP_URL="$SHOP_URL" python3 -c 'import json, os, sys; c = json.load(sys.stdin); c["baseUrl"] = os.environ["SHOP_URL"]; print(json.dumps(c))' \
  | curl -fsS -X PUT "${auth[@]}" --data-binary @- "$REALM_API/clients/$client" > /dev/null

echo "Theme \"comptoir\" applied to the realm ecommerce: sign in from $SHOP_URL to see it"
