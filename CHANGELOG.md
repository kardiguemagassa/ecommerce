# Changelog

Notable changes of each release. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the
versions [Semantic Versioning](https://semver.org/). Each release publishes two images on Docker Hub:
`kardigue/ecommerce-api` and `kardigue/ecommerce-web`.

## [2.1.0] - 2026-11-07

### Added

- **Up to four views per product.** The product page shows them with thumbnails (keyboard arrows, swiped on a phone);
  the catalogue, the cart and the best sellers keep the main image, the first view. In the admin space, views are
  added (dropped or chosen, several at once), moved before or after each other, removed after a confirmation.
- **Full-screen views.** A click on the picture of a product page, or its magnifier button, shows the views full
  screen: arrows (buttons, keyboard, a swipe), zoom ×2 (double click or tap, "+" and "−", the button) following the
  pointer, Escape to close and back on the view last seen.
- API: `POST /api/products/{id}/images` (409 `IMAGE_LIMIT` beyond four), `DELETE /api/products/{id}/images/{imageId}`,
  `PUT /api/products/{id}/images/order`; `images` in the product details. Migration `V5`: each existing image
  becomes the main view, nothing to upload again.

### Deprecated

- `PUT` and `DELETE /api/products/{id}/image`: they still act on the main image; use the views endpoints.

## [2.0.2] - 2026-11-07

### Changed

- Keycloak 26.8.2 and minor updates of the front-end dependencies (TanStack Query, React Router, Vite plugin,
  ESLint, Vitest). Dependabot no longer offers Node.js and TypeScript majors nor the nginx mainline: they are taken
  by hand.

### Fixed

- Keycloak runs under a small init (`init: true`): stop signals are passed on and leftover processes reaped, so
  `docker compose down` no longer hangs on it.
- `scripts/clean-test-data.sh` also removes the images of the test products it deletes; new
  `scripts/clean-orphan-media.py` lists (and with `--delete` removes) images no row uses any more.
- Front-end tests no longer print "Not implemented: window.scrollTo" (mocked in the test setup).

## [2.0.1] - 2026-11-06

### Fixed

- Product images are no longer cropped: the API keeps the whole picture (reduced within 1200 x 900, never enlarged) and
  the shop fits it in its 4:3 frame on white, so a portrait product (a kettle, a coffee maker) is shown entire. Images
  uploaded before 2.0.1 were cropped when stored: upload them again. Profile photos stay square, shown in a circle.
- Catalogue: the category filter stays on one line that scrolls sideways, whatever the number of categories (faded
  edges, arrows for the mouse on wider screens, selected and focused category kept in view), instead of pushing the
  products down.
- Test data no longer piles up in a development database: `scripts/clean-test-data.sh` removes what the end-to-end
  tests and the smoke test created, at the end of each run (`KEEP_TEST_DATA=1` keeps it). The smoke test empties the
  demo customer's cart before ordering, so its order holds the test product only.

## [2.0.0] - 2026-11-06

The shop gets its front-end: **Comptoir**, a React application served by nginx, in front of the API. 2.0.0 because
the stack changes shape: the API is no longer published on its own port, everything goes through the `web` image.

### Added

- **Front-end** (React 19, TypeScript, MUI, React Router, TanStack Query), in French, served by nginx:
  - catalogue with search, categories, sorting and pages in the address, best sellers, product pages;
  - cart with optimistic quantity changes, checkout, order tracking that follows the confirmation live, order history;
  - profile with a photo shown in the header, link to the Keycloak account console;
  - admin space: products (form, image by drag and drop), categories, best sellers, customer support;
  - toasts for every message, routes loaded on demand, typed API client generated from the OpenAPI contract.
- **Images** in S3-compatible object storage (SeaweedFS): product images and profile photos, checked, re-encoded as
  JPEG and stripped of their metadata by the API, served read-only by nginx.
- **Web entry point** (nginx): the front-end, `/api`, the BFF login routes and `/media` on one origin, with a strict
  Content Security Policy, security headers and an upload rate limit.
- **Login and sign-up pages in the look of the shop**: Keycloak theme `comptoir` (keycloak.v2 restyled), French
  wording, "Rester connecté", link back to the shop.
- API: `PUT /api/me/cart/items/{productId}` (quantity of a line), `GET /api/admin/customers` (customer search),
  `accountUrl` and `avatarUrl` in the session, `PUT`/`DELETE` of product images and profile photos.
- **End-to-end tests** (Playwright, Chromium desktop and phone) on the whole stack, with an accessibility check (axe,
  WCAG 2.1 AA) of every page; `e2e` job in CI.
- `scripts/demo-data.py` (demo catalogue with images), `scripts/keycloak-theme.sh` (theme of an existing realm).

### Changed

- The API port is no longer published: the browser reaches everything through `http://localhost:8080` (nginx).
- The release builds and publishes the `web` image next to the `api` image.

### Fixed

- Accessibility: names of the account button and of the cart link (WCAG 2.5.3), file choosers without nested
  controls, decorative images next to the product name.
- "Ajouter au panier" clicked before the session is known no longer asks a signed-in customer to sign in.

## [1.2.0] - 2026-11-05

### Added

- Keycloak identity provider with the `ecommerce` realm (demo accounts, roles `CUSTOMER` and `ADMIN`).
- API secured as an OAuth2 resource server; cart and orders of the signed-in customer under `/api/me`.
- Backend for Frontend: browser login with the tokens kept server-side, session cookie and CSRF protection.
- Optional sign-in with Google, brokered by Keycloak (`scripts/keycloak-google.sh`).

### Fixed

- Account console roles for every user of the realm.

## [1.1.0] - 2026-11-05

### Added

- Schema versioned with Flyway, tests on a real PostgreSQL (Testcontainers).
- API image and Compose stack on PostgreSQL 18.
- OpenTelemetry traces, metrics and logs, with grafana/otel-lgtm.
- GitHub Actions CI and release, Dependabot.

## [1.0.0] - 2026-10-22

### Added

- Catalogue (categories, products with search and pages), customers, carts and orders with stock checks and
  optimistic locking; asynchronous order confirmation after commit.
- RFC 9457 error responses, OpenAPI documentation, caching of product details and best sellers.
- Actuator and business metrics on a dedicated management port, Prometheus and Grafana.
- Unit, slice and integration tests, JaCoCo coverage.

[2.1.0]: https://github.com/kardiguemagassa/ecommerce/compare/v2.0.2...v2.1.0
[2.0.2]: https://github.com/kardiguemagassa/ecommerce/compare/v2.0.1...v2.0.2
[2.0.1]: https://github.com/kardiguemagassa/ecommerce/compare/v2.0.0...v2.0.1
[2.0.0]: https://github.com/kardiguemagassa/ecommerce/compare/v1.2.0...v2.0.0
[1.2.0]: https://github.com/kardiguemagassa/ecommerce/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/kardiguemagassa/ecommerce/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/kardiguemagassa/ecommerce/releases/tag/v1.0.0
