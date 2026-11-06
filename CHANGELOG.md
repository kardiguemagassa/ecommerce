# Changelog

Notable changes of each release. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the
versions [Semantic Versioning](https://semver.org/). Each release publishes two images on Docker Hub:
`kardigue/ecommerce-api` and `kardigue/ecommerce-web`.

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

[2.0.0]: https://github.com/kardiguemagassa/ecommerce/compare/v1.2.0...v2.0.0
[1.2.0]: https://github.com/kardiguemagassa/ecommerce/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/kardiguemagassa/ecommerce/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/kardiguemagassa/ecommerce/releases/tag/v1.0.0
