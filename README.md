# Zero Meeting System

A production-grade meeting platform with video conferencing, real-time
in-meeting chat, scheduled meetings, and multi-channel notifications. Built as a
monorepo with Java/Spring microservices, a Next.js web client, and a native
Android app.

**License:** Apache-2.0

---

## What ZMS Does

- **Video meetings** — instant or scheduled rooms powered by LiveKit (WebRTC)
- **Real-time chat** — in-meeting text chat persisted to MongoDB
- **Join requests & admission** — host-controlled access with request/approve
  flows
- **Meeting recordings** — stored to S3-compatible storage (RustFS)
- **Notifications** — email via Resend (password reset, invitations, reminders)
- **User management** — registration, authentication (JWT), profile management
- **Multi-tenant-ready** — admission policies, guest access, meeting passwords

---

## Architecture

```
 Clients (Web / Android)
         │
         ▼
   Kong Gateway (API Router)
         │
         ├── user-management (Spring Boot · Postgres · gRPC server)
         ├── meeting-management (Spring Boot · Postgres · Kafka consumer)
         │         ├── gRPC client ──► user-management
         │         ├── SSE ──► Valkey (pub/sub, join state)
         │         └── LiveKit ──► RustFS (recordings)
         ├── chat-management (Spring Boot · MongoDB · Kafka consumer)
         │         └── LiveKit (room token signalling)
         └── notification (Spring Boot · Kafka consumer)
                   └── Resend (email)

Kafka (CloudEvents) ← user-management publishes
                   ← meeting-management publishes
                   → chat-management consumes
                   → notification consumes
```

**Tech stack summary:**

| Layer                  | Technology                                                 |
| ---------------------- | ---------------------------------------------------------- |
| Backend services       | Spring Boot 4 / Java 25, hexagonal architecture            |
| Service communication  | Kong HTTP gateway, gRPC, Kafka + CloudEvents, SSE + Valkey |
| Persistence            | Postgres (Flyway), MongoDB, Valkey                         |
| Real-time media        | LiveKit (WebRTC), RustFS (S3)                              |
| External notifications | Resend (email), Firebase (auth/storage)                    |
| Web client             | Next.js 16, React 19, Tailwind CSS 4, Radix UI             |
| Mobile client          | Native Android (Hilt, Retrofit, MVVM)                      |
| Infrastructure         | Kubernetes / k3s, Kustomize, Helm                          |
| API-first              | OpenAPI 3.0 (generated from tests), unified spec           |

---

## Quick Start

### Prerequisites

- Java 25
- Node.js 22+
- pnpm 10+
- Docker (for local Postgres, MongoDB, Kafka, Valkey — or use the provided
  docker-compose)
- Android SDK (for the mobile app)

### 1. Start backing services

```bash
docker compose up -d
```

### 2. Build all backend services

```bash
./services/gradlew build
```

### 3. Start the web app

```bash
pnpm install
pnpm --dir frontends/web dev
```

### 4. (Optional) Build the Android app

```bash
./frontends/android-app/gradlew -p frontends/android-app :app:assembleDebug
```

---

## Project Structure

```
zero-meeting-system/
├── services/
│   ├── user-management/        # User registration, auth, profiles
│   ├── meeting-management/    # Meeting lifecycle, scheduling, LiveKit
│   ├── chat-management/       # In-meeting chat, message history
│   ├── notification/         # Email notifications via Kafka consumer
│   ├── shared/               # Shared domain types, Result type, JSend
│   └── proto/                # gRPC proto definitions
├── frontends/
│   ├── web/                  # Next.js 16 web client
│   └── android-app/          # Native Android app
├── services/k8s/             # Kubernetes manifests (Kustomize overlays)
├── openspec/                 # Product specifications and change artifacts
├── build-logic/              # Shared Gradle convention plugins
└── openapi/                  # Merged unified OpenAPI spec
```

---

## Key Build Commands

### Backend services

```bash
./services/gradlew build                          # build all services
./services/gradlew -p services/ < service > build # build one service
./services/gradlew spotlessApply                  # format Java/KTS/XML
./services/gradlew bufFormatApply                 # format proto files
./services/gradlew test                           # run all tests
```

### Web

```bash
pnpm --dir frontends/web dev      # start dev server
pnpm --dir frontends/web build    # production build
pnpm --dir frontends/web lint     # biome lint
pnpm --dir frontends/web lint:fix # auto-fix lint issues
```

### Android

```bash
./frontends/android-app/gradlew -p frontends/android-app :app:assembleDebug
```

### OpenAPI / SDK generation

```bash
pnpm run openapi:services                 # generate per-service OpenAPI specs from tests
pnpm run openapi:join                     # merge specs into openapi/unified-openapi.yaml
pnpm run openapi:unified                  # full pipeline (generate + merge + lint)
pnpm --dir frontends/web run generate:sdk # regenerate web TypeScript SDK
```

---

## CI / Pre-commit

Git hooks (via lefthook) run automatically on commit:

- `gitleaks` — secret scanning on staged files
- `spotlessApply` — format Java/Kotlin/XML
- `bufFormatApply` — format proto files
- `biome check --fix` — format/lint web TypeScript
- `prettier` — format markdown, JSON, YAML, TOML, shell
- `commitlint` — validate commit messages against conventional commits

Root-level tooling:

```bash
pnpm lint   # markdownlint
pnpm format # prettier
```

---

## Kubernetes Deployment

See [services/k8s/deploy.en.md](services/k8s/deploy.en.md) for the full
deployment guide covering k3s setup, Helm charts (Kafka, Kong, LiveKit, PLG
stack), secret management, and overlay differences between dev and prod.

---

## API Reference

The unified OpenAPI spec is generated at `openapi/unified-openapi.yaml` after
running `pnpm run openapi:unified`. It covers:

- `user-management` — auth, users, profiles
- `meeting-management` — meetings, join requests, settings
- `chat-management` — chat rooms, messages

---

## Contributing

1. Fork the repository and create a branch from `main`
2. Run `pnpm install` and `./services/gradlew build` to verify your setup
3. Make your changes following the code conventions (hexagonal architecture for
   services, MVVM for Android, standard Next.js patterns for web)
4. Ensure all hooks pass (`pnpm lint`, `./services/gradlew test`, etc.)
5. Open a PR targeting `main` with a conventional commit message

---

## License

Apache-2.0 — see [LICENSE](LICENSE) for details.
