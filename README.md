# E-commerce Backend — Spring Boot 4

[![CI](https://github.com/kardiguemagassa/ecommerce/actions/workflows/ci.yml/badge.svg?branch=dev)](https://github.com/kardiguemagassa/ecommerce/actions/workflows/ci.yml)
[![Release](https://github.com/kardiguemagassa/ecommerce/actions/workflows/release.yml/badge.svg)](https://github.com/kardiguemagassa/ecommerce/actions/workflows/release.yml)

A simplified e-commerce REST API: product catalog with search and caching, shopping cart, and orders
confirmed asynchronously. Built as a production-minded showcase: query optimization, transactional
consistency, caching, observability and an automated test suite.

## Features

- **Products** — CRUD, paginated search filtered by category and name, bean validation
- **Categories** — CRUD with case-insensitive unique names; deletion blocked while in use
- **Cart** — one cart per customer: add (merges quantities), remove, clear, live total
- **Orders** — placed atomically from the cart (price snapshot, stock decrement, cart emptied),
  then confirmed **asynchronously** after commit (email + status `PENDING` → `CONFIRMED`)
- **Best sellers** — `GET /api/products/top-selling`, cached
- **Images** — product images (administrators) and optional profile photos (customers): every upload checked and
  re-encoded as JPEG without its metadata, stored in S3 object storage
- **Errors** — uniform [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) `application/problem+json`
- **Observability** — OpenTelemetry traces (HTTP, SQL, async confirmation), metrics and logs, Grafana dashboard
- **API documentation** — OpenAPI 3 / Swagger UI, switchable off for production

## Tech stack

| Area | Choice |
|---|---|
| Language / runtime | Java 21 |
| Framework | Spring Boot 4.1 (Spring 7, Hibernate 7, Jackson 3) |
| Database | PostgreSQL 18, schema versioned with Flyway |
| Identity | Keycloak 26 (OpenID Connect), realm `ecommerce` |
| Media storage | S3 API (AWS SDK v2): SeaweedFS in development, any S3 provider in production; TwelveMonkeys ImageIO (WebP) |
| Cache | Caffeine (Spring Cache abstraction) |
| Observability | Micrometer, OpenTelemetry (OTLP), Grafana LGTM: Tempo, Prometheus, Loki |
| API docs | springdoc-openapi 3 |
| Tests | JUnit 5, Mockito, AssertJ, Awaitility, Testcontainers (PostgreSQL, SeaweedFS), JaCoCo |
| Build | Maven (Surefire for unit tests, Failsafe for integration tests) |
| Packaging | Docker multi-stage image (layered, non-root), Docker Compose |
| Front-end | React 19, TypeScript, Vite, MUI, React Router, TanStack Query; API client generated from OpenAPI (openapi-typescript, openapi-fetch); Vitest, Testing Library, MSW |

## Getting started

### Prerequisites

- Docker: database, Keycloak, media storage, observability backend, tests (Testcontainers), and optionally the API itself
- Java 21, to build and run the application outside a container
- Node.js 24 (version in [`frontend/.nvmrc`](frontend/.nvmrc): `nvm use`), for the React front-end

### Configuration

Copy the template and fill in your local values. `.env` is git-ignored and loaded automatically
by Spring (`spring.config.import`) and by Docker Compose.

```bash
cp .env.example .env
```

| Variable | Purpose | Default |
|---|---|---|
| `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB` | Database location | — |
| `POSTGRES_USERNAME`, `POSTGRES_PASSWORD` | Database credentials | — |
| `MANAGEMENT_PORT` | Actuator port | `8081` |
| `WEB_PORT` | Port of the web entry point (nginx: front-end, `/api`, `/media`) published by Compose | `8080` |
| `KEYCLOAK_PORT`, `KEYCLOAK_ADMIN_USERNAME`, `KEYCLOAK_ADMIN_PASSWORD`, `KEYCLOAK_DB_PASSWORD` | Keycloak and its admin console | `8180` |
| `BFF_CLIENT_SECRET` | Secret of the confidential client `ecommerce-bff` | — |
| `DEMO_USER_PASSWORD` | Password of the demo users (12 characters minimum) | — |
| `SHOP_ADMIN_EMAIL` | E-mail (and username) of the shop administrator, kept out of Git | — |
| `MEDIA_ACCESS_KEY`, `MEDIA_SECRET_KEY` | Credentials of the API on the media storage (its only account in development) | — |
| `MEDIA_PORT` | Media storage port published by Compose (S3 API, images read by browsers) | `8333` |
| `MEDIA_ENDPOINT`, `MEDIA_PUBLIC_URL`, `MEDIA_BUCKET`, `MEDIA_PROVISION` | S3 endpoint, public address of the objects, bucket, bucket creation at startup (development only) | SeaweedFS on `localhost:8333`, `true` |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Optional Google sign-in (`scripts/keycloak-google.sh`) | — |
| `TRACING_SAMPLING_PROBABILITY` | Share of requests traced | `0.1` (`1.0` in `.env.example` and Compose) |
| `API_DOCS_ENABLED` | Expose OpenAPI + Swagger UI | `true` (set `false` in production) |

### Run

PostgreSQL, Keycloak, the media storage and the observability backend always run in Docker (`compose.yml`; database
data kept in the `postgres-data` volume, images in `media-data`). The API
runs either from the IDE / Maven, or in a container too:

```bash
# Database in Docker, API on the host (hot reload with DevTools)
docker compose up -d --wait
./mvnw spring-boot:run

# Everything in Docker: http://localhost:8080 (nginx: front-end, API, media)
docker compose --profile app up -d --build --wait

# Stop (data kept): with the profile, or the API and web containers keep running; -v deletes the volumes
docker compose --profile app down

# Demo catalogue (5 categories, 20 products with generated images, a few orders): idempotent, through the API
./scripts/demo-data.py
```

Every port is published on `127.0.0.1` only. In Docker, **nginx is the single entry point** (`web` service): the
React front-end, `/api`, the browser login (`/oauth2`, `/login/oauth2`, `/logout`) and `/media`, on one origin; the
API port itself is not published. Both containers run as non-root users; the API has a 512 MB memory limit from which
the JVM sizes its heap, nginx runs on a read-only file system.

| URL | Content |
|---|---|
| http://localhost:8080 | The shop (React front-end) |
| http://localhost:8080/api/... | REST API |
| http://localhost:8080/swagger-ui.html | Swagger UI |
| http://localhost:8080/v3/api-docs | OpenAPI document (JSON) |
| http://localhost:8081/actuator/health | Health, liveness and readiness probes |
| http://localhost:8081/actuator/metrics | Metrics (also pushed over OTLP) |
| http://localhost:3000 | Grafana: dashboard, traces, logs (admin / admin) |
| http://localhost:8180 | Keycloak: admin console (`KEYCLOAK_ADMIN_*`), realm `ecommerce` |
| http://localhost:8080/media/... | Product images and profile photos (read-only; S3 API of the storage on 8333) |

Ready-to-run requests for the IntelliJ HTTP client are in [`http/`](http/) (environment `dev`). Each file starts by
signing in; put the demo password (and your `SHOP_ADMIN_EMAIL` as `adminEmail`) in
`http/http-client.private.env.json`, git-ignored, and run `./scripts/dev-token.sh alice@example.com` once to create the
development client.

### Front-end (React)

The shop's web interface lives in [`frontend/`](frontend/). In development, Vite serves it on
http://localhost:5173 and forwards `/api`, `/oauth2`, `/login/oauth2` and `/logout` to the API: one origin for the
browser, so the session cookie and the CSRF token work without CORS.

```bash
# API and its services running (see above: from the IDE, or in Docker)
cd frontend
nvm use            # Node.js version of .nvmrc
npm ci
npm run dev        # http://localhost:5173
```

- **Sign-in through the API** (Backend for Frontend): the browser holds an HttpOnly session cookie, never a token.
  The front-end reads who is signed in from `GET /api/me/session` and sends the `XSRF-TOKEN` cookie back as
  `X-XSRF-TOKEN` with every write.
- **Typed client**: [`frontend/openapi.json`](frontend/openapi.json) is the API contract, from which
  `src/api/schema.d.ts` is generated (`npm run api:types`, run before `dev`, `build`, `lint`, `typecheck` and `test`).
  `./mvnw verify` exports the live contract to `target/openapi.json`; CI fails when the copy in `frontend/` differs.
  After an API change: `./mvnw verify`, then `npm run api:sync` in `frontend/`, and commit `openapi.json`.
- **Catalogue** (`/`): best sellers, products 12 per page with their image, category, price and availability; the
  search, category, order and page live in the URL (`/?q=clavier&categorie=1&tri=prix-croissant&page=2`), so a
  search can be bookmarked or shared and the back button works. **Product page** (`/produits/:id`): quantity limited
  to the stock, a removed product is a 404 inside the page. **Add to cart**: the cart returned by the API replaces the
  cached one (badge updated without extra request), with a confirmation toast; anonymous visitors get a toast with a
  "Se connecter" button.
- **Data fetched with the page**: route loaders fill the TanStack Query cache while the page module loads; query keys
  from one factory (`productKeys`), next page of results prefetched.
- **Pages loaded on demand**: each route is its own chunk (React Router `lazy`), with a progress bar while it
  arrives, the browser tab named after the page, focus moved to the new content after a navigation and a skip link.
  A page that fails keeps the header usable; after a deployment, a tab on the previous release offers to reload.
- **Toasts** (`notify.success/info/warning/error`): one at a time in order of arrival; errors in French with the
  `traceId` of the request to quote to the support, kept until closed; others vanish after 4 to 6 s.

| Command (in `frontend/`) | Purpose |
|---|---|
| `npm run dev` | Development server with hot reload |
| `npm run lint` / `npm run typecheck` | ESLint / TypeScript (strict) |
| `npm test` | Unit and component tests (Vitest, Testing Library, API mocked with MSW) |
| `npm run build` | Production build in `frontend/dist` |

### Web entry point (nginx)

The `web` image ([`frontend/Dockerfile`](frontend/Dockerfile)) builds the front-end with Node.js and serves it with
an unprivileged nginx, which also proxies the API and the media
([configuration](frontend/nginx/templates/default.conf.template)):

| Path | Served by | Notes |
|---|---|---|
| `/`, `/produits/…`, `/admin`… | front-end | any other path gets `index.html` (revalidated on every visit) |
| `/assets/*` | front-end | hashed bundles, cached a year (`immutable`); a missing one is a 404, never `index.html` |
| `/api/`, `/oauth2/`, `/login/oauth2/`, `/logout` | API | `X-Forwarded-*` headers: Spring builds its redirects on the browser's address |
| `/media/*` | media storage | `GET` / `HEAD` only |

- **Security headers** on every response: Content Security Policy (`script-src 'self'`: no inline or third-party
  script; `style-src 'unsafe-inline'` for MUI's injected styles only), `nosniff`, `Referrer-Policy`,
  `Permissions-Policy`, `frame-ancestors 'none'`, no version in `Server`. HSTS is ready, to enable behind HTTPS.
- **Uploads**: 3 MB per request at most (413), avatar and product image uploads limited to 10 a minute per client,
  bursts of 5 (429); both answer an RFC 9457 problem with its `code`, like the API.
- Upstreams resolved at request time (a restarted API is found again); `API_UPSTREAM`, `MEDIA_UPSTREAM`,
  `MEDIA_BUCKET` and `KEYCLOAK_ORIGIN` configure the image.

### Identity provider (Keycloak)

The realm `ecommerce` is imported from [`keycloak/realm-ecommerce.json`](keycloak/realm-ecommerce.json) on the first
start of Keycloak (an existing realm is never overwritten: `docker compose down` then remove the `keycloak-db-data`
volume to re-import it). Secrets and passwords are placeholders resolved from `.env` at import: none is in Git.

| Element | Content |
|---|---|
| Realm roles | `CUSTOMER` (given to every new user: own cart and orders), `ADMIN` (catalog, customer support) |
| `ecommerce-bff` | Confidential client of the API: browser login (authorization code + PKCE), tokens kept server-side |
| `ecommerce-swagger` | Public client of Swagger UI (authorization code + PKCE) |
| Demo users | `alice@example.com` (`CUSTOMER`) and the shop administrator `SHOP_ADMIN_EMAIL` (`CUSTOMER`, `ADMIN`; `admin@example.com` in `.env.example`), password `DEMO_USER_PASSWORD` |
| Policies | French login pages, self-registration with the e-mail as username, passwords of 12 characters minimum, brute-force protection, access tokens valid 5 minutes |

Access tokens carry the audience `ecommerce-api` and the realm roles in a `roles` claim.

#### Sign in with Google (optional)

Keycloak brokers the Google login: the application only ever sees Keycloak tokens and sessions, so nothing changes
in the API. Not part of the versioned realm (no broken button without credentials, no secret in Git), it is added to
a running Keycloak by a script:

1. Google Cloud console, *Google Auth Platform*: consent screen (external, your account as test user), then an OAuth
   client of type *Web application* with the authorized redirect URI
   `http://localhost:8180/realms/ecommerce/broker/google/endpoint`.
2. `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` in `.env`.
3. `./scripts/keycloak-google.sh` (idempotent: adds or updates the provider).

The login page then shows a **Google** button. A first Google login creates the user with the `CUSTOMER` role and a
verified e-mail; if an account with that e-mail already exists, Keycloak asks its owner to sign in to it once before
linking the two, so the account keeps its roles.

### Database migrations

The schema is owned by [Flyway](https://documentation.red-gate.com/flyway) scripts in
[`src/main/resources/db/migration`](src/main/resources/db/migration), applied on startup.
Hibernate only validates it (`ddl-auto: validate`): the application refuses to start if the
entities and the schema disagree.

| Version | Change |
|---|---|
| `V1` | Initial schema (tables, sequences, constraints, indexes of v1.0.0) |
| `V2` | Unique index on `lower(name)` for categories |
| `V3` | `customers.external_id`: the Keycloak user (`sub`) of each customer |
| `V4` | `products.image_key`, `customers.avatar_key`: object keys of the images in the media storage |

A database created by v1.0.0 (schema generated by Hibernate, no Flyway history) must be
**baselined once** at V1, so that Flyway only applies the later scripts:

```bash
SPRING_FLYWAY_BASELINE_ON_MIGRATE=true ./mvnw spring-boot:run
```

Baselining is not enabled permanently: on an unexpected non-empty database, failing is safer than
silently skipping V1.

## API overview

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/categories` | List categories |
| `GET` `POST` `PUT` `DELETE` | `/api/categories[/{id}]` | Category CRUD |
| `GET` | `/api/products?categoryId=&name=&page=&size=&sort=` | Search products (sort: id, name, price, stock) |
| `GET` | `/api/products/{id}` | Product details (cached) |
| `GET` | `/api/products/top-selling?limit=10` | Best sellers (1–50, cached) |
| `POST` `PUT` `DELETE` | `/api/products[/{id}]` | Product management |
| `PUT` `DELETE` | `/api/products/{id}/image` | Replace (multipart `file`) / remove the product image |
| `GET` | `/api/me` | My customer profile (created on the first call) |
| `PUT` `DELETE` | `/api/me/avatar` | Replace (multipart `file`) / delete my profile photo |
| `GET` `DELETE` | `/api/me/cart` | Get / empty my cart |
| `POST` | `/api/me/cart/items` | Add a product (adds to the quantity already in the cart) |
| `PUT` | `/api/me/cart/items/{productId}` | Set the quantity of a line (stock checked, `409` above it) |
| `DELETE` | `/api/me/cart/items/{productId}` | Remove a product |
| `POST` | `/api/me/orders` | Place an order from my cart |
| `GET` | `/api/me/orders` | My order history (paginated, newest first) |
| `GET` | `/api/me/orders/{orderId}` | One of my orders with its lines |
| `GET` | `/api/admin/customers/{id}[/orders[/{orderId}]]` | Customer support: a customer, their orders |

### Security

Two ways in, one set of rules:

| Client | Authentication | Where the tokens are |
|---|---|---|
| Browser (React front-end, v2.0.0) | **Backend for Frontend**: `/oauth2/authorization/keycloak` signs in with Keycloak (authorization code + PKCE, confidential client `ecommerce-bff`), then an `HttpOnly`, `SameSite=Lax` session cookie | On the server only: JavaScript never sees a token |
| Swagger UI, curl, other services | OAuth2 **resource server**: `Authorization: Bearer <access token>`, checked for signature (realm keys), issuer, audience `ecommerce-api` and expiry | With the client |

Browser sessions:

- `GET /api/me/session` (public) tells the front-end whether it is signed in, with the name, e-mail, roles, photo and
  the link of the Keycloak account console (`accountUrl`: name, e-mail and password are changed there), never a
  token; it also sets the `XSRF-TOKEN` cookie.
- Writes made with the session cookie need that token back in the `X-XSRF-TOKEN` header (CSRF); bearer-token calls
  are not concerned (a browser never sends a bearer token on its own).
- `POST /logout` closes the session and answers `{"logoutUrl": ...}`: the Keycloak end-session URL the front-end
  navigates to, so that single sign-on does not log the user back in.
- After login and logout the browser lands on the front-end: `BFF_FRONT_URL`, `/` by default, resolved against the
  address the browser used (nginx on 8080, Vite on 5173; forwarded headers trusted from private networks only).

Swagger UI: **Authorize** signs in with Keycloak (public client `ecommerce-swagger`, PKCE), then sends the token
with every call.

| Routes | Access |
|---|---|
| `GET` on products and categories | Public |
| Writes on products and categories, `/api/admin/**` | Realm role `ADMIN` |
| `/api/me/**` | Realm role `CUSTOMER` (every user has it) |
| Anything else | Refused (a new endpoint is closed until a rule opens it) |

There is no sign-up endpoint and no customer id in the URLs: the customer is always the one of the token. Their
record is created on their first call from the token claims (`sub`, `email`, `name`), and their e-mail and name follow
Keycloak afterwards. A customer created before authentication is attached to the Keycloak user with the same e-mail,
only if Keycloak verified that e-mail.

A token of a demo user for curl or scripts (development only, password grant on a local client created on first use):

```bash
curl -H "Authorization: Bearer $(./scripts/dev-token.sh alice@example.com)" http://localhost:8080/api/me
```

The full contract, with request/response schemas and error codes, is in Swagger UI.

### Error format

All errors share the RFC 9457 structure; validation errors add a field-by-field `errors` map:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Invalid request content.",
  "instance": "/api/products",
  "errors": { "price": "must be greater than 0" }
}
```

| Status | When |
|---|---|
| `400` | Validation error, malformed body, unsupported sort property, page size above 100 |
| `401` | Missing, invalid or expired token (`code`: `UNAUTHENTICATED`, `WWW-Authenticate: Bearer`) |
| `403` | Valid token without the required role (`code`: `ACCESS_DENIED`) |
| `404` | Unknown resource, or an order belonging to another customer |
| `409` | Duplicate, resource in use, insufficient stock, empty cart, concurrent modification |
| `413` | Image above its limit: 2 MB for a product, 1 MB for a photo (`code`: `IMAGE_TOO_LARGE`) |
| `415` | File that is not a JPEG, PNG or WebP image, whatever its name (`code`: `UNSUPPORTED_IMAGE`) |
| `422` | Damaged image, or more than 40 megapixels (`code`: `INVALID_IMAGE`) |
| `503` | Media storage unreachable, nothing changed (`code`: `STORAGE_UNAVAILABLE`) |

### Images

Product images (administrators) and profile photos (each customer, optional) are uploaded to the API as
`multipart/form-data`, then stored in an S3 bucket; the database keeps the object key, the responses carry
`imageUrl` / `avatarUrl` (also in `GET /api/me/session`, for the navigation bar).

- **Untrusted input**: the format comes from the content (magic bytes), never from the file name or the declared type;
  the dimensions are read before decoding (decompression bombs refused above 40 megapixels).
- **Re-encoded**: turned upright (EXIF orientation), centre-cropped (4:3 for products, square for photos), reduced to
  1200 x 900 or 256 x 256 at most, flattened on white, written as a new JPEG: no EXIF (GPS position, device), no
  payload appended to the original survives.
- **Random keys** (`products/<uuid>.jpg`): names chosen by users never reach the storage, URLs cannot be guessed,
  objects never change (served with `Cache-Control: immutable`).
- **Consistent with the database**: a new image is written first and deleted if the transaction rolls back; the
  replaced or removed one is deleted only after the commit.
- **Read directly by browsers**: the bucket policy allows `s3:GetObject` only (no listing, no anonymous writing);
  the API never streams images.

The storage is any S3 implementation: [SeaweedFS](https://github.com/seaweedfs/seaweedfs) (Apache 2.0) in Compose,
tests and CI; AWS S3, Scaleway, OVHcloud or Cloudflare R2 in production by changing `MEDIA_ENDPOINT` and the keys.
MinIO, the usual choice, is no longer distributed as an image since 2025.

## Architecture

```
src/main/java/com/kmgtechnologies/ecommerce
├── controller     REST layer: validation, HTTP mapping, sort allow-list
├── service        Use cases and transaction boundaries
├── repository     Spring Data JPA, DTO projections, fetch plans
├── entity         Aggregates: Cart → CartItem, Order → OrderItem
├── dto            Request/response records (entities never leave the service layer)
├── event          OrderPlacedEvent
├── notification   Asynchronous order confirmation
├── cache          Cache eviction after commit
├── metrics        Business metrics
├── media          Image checks and re-encoding, S3 storage, public URLs
├── exception      Domain exceptions and RFC 9457 handler
└── config         Cache, async executor, clock, OpenAPI
```

### Order placement flow

```mermaid
sequenceDiagram
    participant C as Client
    participant API as OrderService
    participant DB as PostgreSQL
    participant L as After-commit listeners
    participant A as async-* thread

    C->>API: POST /customers/{id}/orders
    API->>DB: load cart (JOIN FETCH), check stock
    API->>DB: insert order + lines, decrement stock (@Version), empty cart
    API-->>C: 201 Created (PENDING)
    DB-->>L: COMMIT
    L->>L: evict ordered products from cache, record metrics
    L->>A: OrderPlacedEvent (@Async)
    A->>A: send confirmation email
    A->>DB: status CONFIRMED
```

Every side effect runs **after commit**: a rolled-back order (e.g. insufficient stock) sends no email,
evicts nothing and is not counted.

## Technical choices

| Concern | Decision | Why |
|---|---|---|
| N+1 queries | DTO projections (`select new ...`) for lists, `JOIN FETCH` for the cart, `@EntityGraph` for order details; `open-in-view: false` | One query per use case; lazy loading cannot leak into the web layer |
| Schema | Flyway migrations, Hibernate `validate` | Reviewed, versioned DDL; drift between entities and schema fails at startup |
| Identifiers | `SEQUENCE` with `allocationSize = 50` | Enables JDBC batching; avoids one round-trip per insert, unlike `IDENTITY` |
| Concurrency | Optimistic locking (`@Version`) on products and orders → `409` | Two orders cannot oversell the last unit; no long-held locks |
| Order history | Product name and unit price snapshotted in `OrderItem` | Past orders stay correct after a price change |
| Pagination | Max page size 100, sort properties allow-listed | Prevents unbounded queries and sorting on non-indexed or internal fields |
| Caching | Caffeine: product details (10 min, 1000 entries), best sellers (5 min); transaction-aware cache manager; eviction on update/delete and after each order | Hot reads skip the database; a rolled-back transaction never corrupts the cache |
| Async work | `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` on a bounded pool | The HTTP response does not wait for the email |
| Time | Injected `Clock` | Deterministic tests on timestamps |
| Secrets | Environment variables / git-ignored `.env`, no credential in the repository | Twelve-factor configuration |
| Container image | Multi-stage build, layered jar, non-root user, G1 with heap sized from the memory limit | A code change ships one small layer; least privilege; predictable memory |

## Observability

Traces, metrics and logs are exported over **OTLP** (OpenTelemetry) to `grafana/otel-lgtm` in development: one
container with the OpenTelemetry collector, Tempo (traces), Prometheus (metrics), Loki (logs) and Grafana, started by
`docker compose up -d`. Changing backend (Grafana Cloud, Datadog, Elastic...) means changing the OTLP endpoints, not
the code.

| Signal | What you get |
|---|---|
| **Traces** | One trace per request: HTTP, every SQL query, and for an order the **asynchronous confirmation** in the same trace (context propagated to the `@Async` thread). Sampling 100 % in development, 10 % by default |
| **Logs** | Console unchanged, and every log line sent to Loki with its trace and span ids |
| **Metrics** | Pushed every 10 s in Compose (1 min by default), shown by the *E-commerce — Vue d'ensemble* dashboard |
| **Errors** | Every RFC 9457 response carries a `traceId`: a reported error leads straight to its trace |

Health probes and SQL run outside of any request (Flyway, connection pool) create no trace. SQL spans never contain
parameter values.

| Metric | Source |
|---|---|
| `ecommerce_orders_placed_total` | Orders committed (counted after commit) |
| `ecommerce_orders_amount` | Order amounts: count, sum, max (revenue, average basket) |
| `ecommerce_orders_confirmation_milliseconds{outcome}` | Duration of the async confirmation, success/failure |
| `http_server_requests_milliseconds` | Per-endpoint traffic, status and latency histogram (p95) |
| `cache_gets_total{result}` | Cache hits and misses |
| `hikaricp_*`, `executor_*`, `jvm_*` | Connection pool, async pool, JVM |

Actuator runs on a **separate management port** (`8081`), so the public API port never exposes `/actuator`. Only
`health`, `info` and `metrics` are exposed; health details are hidden.

### Following an order in Grafana

http://localhost:3000 (admin / admin):

- **Dashboards → E-commerce → Vue d'ensemble**: orders per minute, revenue, average basket, cache hit ratio, p95
  latency per endpoint, confirmation duration (provisioned from `observability/grafana`, read-only)
- **Explore → Tempo**, query `{ resource.service.name = "ecommerce" && span.http.route = "/api/me/orders" }`:
  the order trace, with its SQL queries and the `order confirmation` span; **Logs for this span** opens the matching
  lines in Loki
- **Explore → Loki**: `{service_name="ecommerce"} | trace_id="<traceId>"` for the logs of one request

`./scripts/smoke.sh` does all of this automatically against the running stack (also run by CI): it places an order,
changes a cart quantity through the browser session (and is refused above the stock), uploads a product image and a profile photo (read back from the media storage, then deleted), then checks the
`traceId` of an error, the order trace in Tempo and its confirmation logs in Loki.

## Tests

```bash
./mvnw test      # unit and slice tests (Surefire)
./mvnw verify    # + integration tests (Failsafe) + JaCoCo report
```

Tests run against a **real PostgreSQL 18** started by Testcontainers (Docker must be running): the
same Flyway migrations as in production, then Hibernate schema validation. One container per test JVM,
shared by all Spring contexts.

| Level | Tools | Scope |
|---|---|---|
| Entities | JUnit | Aggregate invariants (cart merging, stock, order status) |
| Services | Mockito | Business rules, events published or not |
| Repositories | `@DataJpaTest` on PostgreSQL | Migrations, queries, projections, constraints |
| Controllers | `@WebMvcTest` | Validation, status codes, RFC 9457 payloads |
| Observability | JUnit, OpenTelemetry SDK (in-memory) | Noise filter, logs exported with their trace id, `traceId` in errors |
| Security | `@WebMvcTest` with `@WithMockUser` / `oidcLogin()`, JUnit | 401 / 403 per route, CSRF on session writes, session endpoint, logout URL, roles of the ID token, first-call provisioning |
| Images | JUnit, Mockito | Format detection, decompression bombs, EXIF orientation and removal, crop and size, storage kept in step with commits and rollbacks |
| Integration (`*IT`) | `@SpringBootTest`, Testcontainers SeaweedFS | Async confirmation, caching, Actuator exposure, OpenAPI contract and its production switch, images served publicly and deleted |
| Front-end | Vitest, Testing Library, MSW | CSRF header on writes, French messages and `traceId` of API errors (429, 413 included), header signed in or not, protected pages, session expiry, toasts (queue, durations, roles, actions), lazy routes (titles, focus, stale chunk after a deployment), catalogue (URL state sent to the API, filters, pagination links, empty results, best sellers), product page (quantity bounds, 404), add to cart (badge, toasts, anonymous visitor, click before the session is known, insufficient stock), cart (optimistic quantity rolled back on a 409, remove and undo, clear after confirmation, empty cart, anonymous visitor without call), order (placed then tracked, polling stopped once confirmed, 404 in the page), order history (page from the address), profile photo (checked before upload, preview, multipart upload, header updated, deletion confirmed), account console link |

284 tests (255 unit/slice, 29 integration), plus 74 front-end tests. Coverage report: `target/site/jacoco/index.html`.

## Known limitations and next steps

| Limitation | Next step |
|---|---|
| In-memory sessions: one API instance, sessions lost on restart | Spring Session with Redis or JDBC when running several instances |
| Confirmation is lost if the app stops between commit and email (order stays `PENDING`) | Transactional outbox + retry, or a message broker |
| Caffeine is local to one instance | Redis when running several instances |
| `name LIKE '%x%'` cannot use a B-tree index | PostgreSQL `pg_trgm` GIN index or full-text search |
| Async pool grows beyond 2 threads only when its queue (100) is full | Size `core-size` from `executor_queued_tasks` and confirmation latency |

## CI/CD

| Workflow | When | What |
|---|---|---|
| [CI](.github/workflows/ci.yml) | Every pull request to `dev`/`main`, every push to `dev` | `build`: `./mvnw verify` (tests on PostgreSQL 18, coverage summary), OpenAPI contract of the front-end up to date. `frontend`: lint, types, tests, build. `smoke`: the Compose stack built and started, then `scripts/smoke.sh` |
| [Release](.github/workflows/release.yml) | Push to `main`, tag `vX.Y.Z` | Tests again, image scanned by Trivy (no fixable CRITICAL vulnerability), then pushed to Docker Hub for amd64 and arm64 with SBOM and provenance |
| [Dependabot](.github/dependabot.yml) | Weekly | Update pull requests to `dev`: GitHub Actions, Maven, Dockerfile and Compose images |

Images: [`kardigue/ecommerce-api`](https://hub.docker.com/r/kardigue/ecommerce-api) and
[`kardigue/ecommerce-web`](https://hub.docker.com/r/kardigue/ecommerce-web) (nginx and the front-end), tags `X.Y.Z`, `X.Y`, `latest` (releases),
`main` and `sha-<commit>`. Actions are pinned by commit SHA; workflows only read the repository.

To run a published release instead of building locally:

```bash
IMAGE_NAMESPACE=kardigue IMAGE_TAG=1.1.0 docker compose --profile app up -d --no-build --wait
```

## Workflow

- Feature branches merged into `dev` through pull requests; `main` and `dev` are protected, CI `build`, `frontend` and `smoke` required
- [Conventional Commits](https://www.conventionalcommits.org/) (`feat`, `fix`, `test`, `build`, `docs`, `chore`)
