#!/usr/bin/env bash
# Removes from the shop database what the automated tests create, so that a developer's catalogue keeps only its own
# data: categories named "E2E …" (end-to-end tests), "Smoke …" (scripts/smoke.sh) and "OTel …" (earlier smoke tests),
# their products, and the cart and order lines of those products. Orders left empty go with them.
# Run at the end of scripts/smoke.sh and of the end-to-end tests (also in CI, where it checks the script itself);
# KEEP_TEST_DATA=1 keeps the data, to look at it after a run. By hand: ./scripts/clean-test-data.sh
#
# One transaction, refused as a whole if an order mixes a test product with a real one: deleting the test line would
# leave that order with a wrong total. The product images of the deleted products stay in the media storage (unused
# objects, never served again). The API caches best sellers for up to 5 minutes: they catch up on their own.
set -euo pipefail
cd "$(dirname "$0")/.."

if [ -n "${KEEP_TEST_DATA:-}" ]; then
  echo "--  test data kept (KEEP_TEST_DATA is set)"
  exit 0
fi

# The database container of the Compose stack; its own variables give the user and the database (no secret here).
summary=$(docker compose exec -T postgres sh -c 'psql -v ON_ERROR_STOP=1 -qAt -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<'SQL'
BEGIN;
CREATE TEMP TABLE test_categories ON COMMIT DROP AS
  SELECT id FROM categories WHERE name ~ '^(E2E|Smoke|OTel) ';
CREATE TEMP TABLE test_products ON COMMIT DROP AS
  SELECT id FROM products WHERE category_id IN (SELECT id FROM test_categories) OR name ~ '^(E2E|Smoke|OTel) ';
CREATE TEMP TABLE touched_orders ON COMMIT DROP AS
  SELECT DISTINCT order_id AS id FROM order_items WHERE product_id IN (SELECT id FROM test_products);
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM order_items
             WHERE order_id IN (SELECT id FROM touched_orders) AND product_id NOT IN (SELECT id FROM test_products)) THEN
    RAISE EXCEPTION 'an order mixes test products and real products: nothing deleted';
  END IF;
END $$;
SELECT format('%s categories, %s products, %s orders',
              (SELECT count(*) FROM test_categories), (SELECT count(*) FROM test_products),
              (SELECT count(*) FROM touched_orders));
DELETE FROM cart_items  WHERE product_id IN (SELECT id FROM test_products);
DELETE FROM order_items WHERE product_id IN (SELECT id FROM test_products);
DELETE FROM orders      WHERE id IN (SELECT id FROM touched_orders);
DELETE FROM products    WHERE id IN (SELECT id FROM test_products);
DELETE FROM categories  WHERE id IN (SELECT id FROM test_categories);
COMMIT;
SQL
) || { echo "FAIL: test data not removed (see the error above)" >&2; exit 1; }

echo "OK  test data removed: $summary"
