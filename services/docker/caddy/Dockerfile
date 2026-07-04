FROM caddy:2.10.2-builder-alpine AS builder

RUN xcaddy build \
    --with github.com/ggicci/caddy-jwt@v1.1.2

FROM caddy:2.10.2-alpine

COPY --from=builder /usr/bin/caddy /usr/bin/caddy
