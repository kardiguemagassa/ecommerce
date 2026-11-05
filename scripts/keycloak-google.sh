#!/usr/bin/env bash
# Adds "Sign in with Google" to the local "ecommerce" realm, or updates it (idempotent):
#   ./scripts/keycloak-google.sh
# Needs GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET (environment or .env): an OAuth client of type "Web application"
# in the Google Cloud console, with the authorized redirect URI
#   http://localhost:8180/realms/ecommerce/broker/google/endpoint
# Applied through the Keycloak admin API rather than versioned in the realm file: without Google credentials (CI,
# another developer) there is no broken button, and the secret never reaches Git.
set -euo pipefail

setting() {
  local value="${!1:-}"
  if [ -z "$value" ] && [ -f .env ]; then value=$(grep -E "^$1=" .env | tail -1 | cut -d= -f2-); fi
  [ -n "$value" ] || { echo "$1 is not set (environment or .env)" >&2; exit 1; }
  printf '%s' "$value"
}

KEYCLOAK=${KEYCLOAK_URL:-http://localhost:${KEYCLOAK_PORT:-8180}}
ADMIN_API="$KEYCLOAK/admin/realms/ecommerce/identity-provider/instances"

json() { python3 -c "import json, sys; print(json.load(sys.stdin)$1)"; }

admin_token=$(curl -fsS --data-urlencode grant_type=password --data-urlencode client_id=admin-cli \
  --data-urlencode "username=$(setting KEYCLOAK_ADMIN_USERNAME)" \
  --data-urlencode "password=$(setting KEYCLOAK_ADMIN_PASSWORD)" \
  "$KEYCLOAK/realms/master/protocol/openid-connect/token" | json "['access_token']")

# Built in Python from the environment and sent on stdin: the secret never appears on a command line (visible in
# the process list).
provider=$(GOOGLE_CLIENT_ID=$(setting GOOGLE_CLIENT_ID) GOOGLE_CLIENT_SECRET=$(setting GOOGLE_CLIENT_SECRET) python3 -c '
import json, os
print(json.dumps({
    "alias": "google",
    "providerId": "google",
    "displayName": "Google",
    "enabled": True,
    # Google verifies the e-mail: the account is created verified; an existing account with the same e-mail is
    # linked only after its owner signs in to it (default "first broker login" flow).
    "trustEmail": True,
    "storeToken": False,
    "firstBrokerLoginFlowAlias": "first broker login",
    "config": {
        "clientId": os.environ["GOOGLE_CLIENT_ID"],
        "clientSecret": os.environ["GOOGLE_CLIENT_SECRET"],
        "defaultScope": "openid profile email",
        "syncMode": "IMPORT",
    },
}))')

status=$(curl -sS -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $admin_token" "$ADMIN_API/google")
if [ "$status" = 200 ]; then
  printf '%s' "$provider" | curl -fsS -X PUT -H "Authorization: Bearer $admin_token" \
    -H 'Content-Type: application/json' --data-binary @- "$ADMIN_API/google" > /dev/null
  echo "Google sign-in updated in the realm ecommerce"
else
  printf '%s' "$provider" | curl -fsS -X POST -H "Authorization: Bearer $admin_token" \
    -H 'Content-Type: application/json' --data-binary @- "$ADMIN_API" > /dev/null
  echo "Google sign-in added to the realm ecommerce"
fi
echo "Login page: $KEYCLOAK/realms/ecommerce/account (button \"Google\")"
