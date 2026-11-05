#!/usr/bin/env bash
# Prints an access token of a user of the local "ecommerce" realm, for curl, the IntelliJ HTTP client or scripts:
#   ./scripts/dev-token.sh alice@example.com
#   curl -H "Authorization: Bearer $(./scripts/dev-token.sh alice@example.com)" localhost:8080/api/me
# Password: DEMO_USER_PASSWORD (environment or .env). Development only: on first use, the public client
# "ecommerce-dev-cli" allowing the password grant is created in the local Keycloak through the admin API; the
# versioned realm never contains such a client.
set -euo pipefail

setting() {
  local value="${!1:-}"
  if [ -z "$value" ] && [ -f .env ]; then value=$(grep -E "^$1=" .env | tail -1 | cut -d= -f2-); fi
  [ -n "$value" ] || { echo "$1 is not set (environment or .env)" >&2; exit 1; }
  printf '%s' "$value"
}

[ $# -eq 1 ] || { echo "usage: $0 <e-mail of a user of the ecommerce realm>" >&2; exit 2; }
KEYCLOAK=${KEYCLOAK_URL:-http://localhost:${KEYCLOAK_PORT:-8180}}
CLIENT=ecommerce-dev-cli

json() { python3 -c "import json, sys; print(json.load(sys.stdin)$1)"; }

admin_token=$(curl -fsS --data-urlencode grant_type=password --data-urlencode client_id=admin-cli \
  --data-urlencode "username=$(setting KEYCLOAK_ADMIN_USERNAME)" \
  --data-urlencode "password=$(setting KEYCLOAK_ADMIN_PASSWORD)" \
  "$KEYCLOAK/realms/master/protocol/openid-connect/token" | json "['access_token']")
clients=$(curl -fsS -H "Authorization: Bearer $admin_token" "$KEYCLOAK/admin/realms/ecommerce/clients?clientId=$CLIENT")
if [ "$(json ".__len__()" <<< "$clients")" = 0 ]; then
  curl -fsS -X POST -H "Authorization: Bearer $admin_token" -H 'Content-Type: application/json' \
    "$KEYCLOAK/admin/realms/ecommerce/clients" -d "{\"clientId\":\"$CLIENT\",
    \"name\":\"Development tokens (password grant, local only)\",\"publicClient\":true,
    \"directAccessGrantsEnabled\":true,\"standardFlowEnabled\":false,
    \"protocolMappers\":[{\"name\":\"ecommerce-api audience\",\"protocol\":\"openid-connect\",
    \"protocolMapper\":\"oidc-audience-mapper\",\"config\":{\"included.custom.audience\":\"ecommerce-api\",
    \"access.token.claim\":\"true\",\"id.token.claim\":\"false\"}}]}" > /dev/null
fi

curl -fsS --data-urlencode grant_type=password --data-urlencode "client_id=$CLIENT" \
  --data-urlencode "username=$1" --data-urlencode "password=$(setting DEMO_USER_PASSWORD)" \
  --data-urlencode scope=openid "$KEYCLOAK/realms/ecommerce/protocol/openid-connect/token" | json "['access_token']"
