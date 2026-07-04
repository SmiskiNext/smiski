# Docker dev environment

Run infra first, then run Spring Boot services natively:

```sh
pnpm smiski infra up
pnpm smiski svc all
```

Gateway: Caddy listens on `localhost:30000` and proxies to host services.

| Component          | Host port                        |
| ------------------ | -------------------------------- |
| user-management    | 8181 HTTP, 9090 gRPC             |
| meeting-management | 8182                             |
| chat-management    | 8183                             |
| notification       | 8184                             |
| user-postgres      | 8281                             |
| meeting-postgres   | 8282                             |
| chat-mongo         | 8283                             |
| valkey             | 8284                             |
| kafka              | 9094                             |
| rustfs             | 9000 API, 30001 console          |
| livekit            | 7880 HTTP/WS, 7881 TCP, 7882 UDP |

Run one service with
`SPRING_PROFILES_ACTIVE=dev ./services/gradlew -p services/<service> bootRun`
plus required secrets from env.

Compose with app images belongs in a future production compose flow.

Stop services with Ctrl+C. Stop infra with `pnpm smiski infra down`.
