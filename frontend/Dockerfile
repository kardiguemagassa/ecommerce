# syntax=docker/dockerfile:1

# Web image of the shop: the React front-end served by nginx, which is also the single entry point to the API and
# the media (see nginx/templates/default.conf.template). Built natively ($BUILDPLATFORM): the bundles are the same
# for every architecture, only the nginx stage differs.
FROM --platform=$BUILDPLATFORM node:24.22.2-alpine AS build
WORKDIR /workspace
COPY package.json package-lock.json .npmrc ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# Unprivileged nginx: runs as user nginx (uid 101) on port 8080, no root process at all.
FROM nginx/nginx-unprivileged:1.31.8-alpine-slim
ENV API_UPSTREAM=api:8080 \
    MEDIA_UPSTREAM=media:8333 \
    MEDIA_BUCKET=ecommerce-media \
    KEYCLOAK_ORIGIN=http://localhost:8180 \
    NGINX_ENTRYPOINT_LOCAL_RESOLVERS=1
COPY nginx/snippets/ /etc/nginx/snippets/
COPY nginx/templates/ /etc/nginx/templates/
COPY --from=build /workspace/dist/ /usr/share/nginx/html/
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=3 \
    CMD wget -q --spider http://127.0.0.1:8080/healthz || exit 1
