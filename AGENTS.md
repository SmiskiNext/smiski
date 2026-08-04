# AGENTS.md

Guidance for OpenCode sessions. Keep it verifiable against config and scripts,
not prose. This file is an index — detailed rules live next to the code:

- Backend services → `services/AGENTS.md`
- Forge app → `app/AGENTS.md`

## What this is

Monorepo: Spring Boot 4 / Java 25 microservices (hexagonal DDD) under
`services/` + an Atlassian **Forge** app under `app/` embedding online meetings
into a Jira issue panel. API-first: each service emits its own OpenAPI spec.

Top-level dirs: `services/` (backend + `k8s/` + `docker/` compose stack), `app/`
(Forge), `scripts/` (workspace tooling config), `openspec/` (specs),
`build-logic/` (Gradle convention plugins), `requirements/`, `docs/`.

**Legacy `zms/`** is a gitignored full snapshot of the old Zero Meeting System.
Treat it as **read-only reference**; consult only when asked or when porting
behavior. Never extend or wire into it. Removed dirs (`user-management`,
`meeting-management`, `chat-management`, `frontends/web`) exist only in `zms/`.

## Commands

Local stack: Docker Compose under `services/docker/`. Java service images are
built by `bootBuildImage`, not by compose — build them before starting.

```sh
cp services/docker/.env.example services/docker/.env # fill SMISKI_HOST_IP + CURSOR_SECRET
./services/gradlew -p services/tenant bootBuildImage
./services/gradlew -p services/meet bootBuildImage
./services/gradlew -p services/notification bootBuildImage
docker compose -f services/docker/compose.yaml up -d
docker compose -f services/docker/compose.yaml ps
docker compose -f services/docker/compose.yaml logs -f envoy
docker compose -f services/docker/compose.yaml down # add -v to wipe data
```

The gateway is the only app entry point: `http://localhost:30000`.

Root formatting/specs: `pnpm lint` (markdownlint), `pnpm format` (prettier),
`pnpm run openapi` (regenerate + lint service specs). Toolchain is pinned by
`.mise.toml` (Java 25, node, pnpm, gitleaks, lefthook, buf).

Backend build/test/format commands live in `services/AGENTS.md`. Forge
deploy/tunnel/lint commands live in `app/AGENTS.md`.

## Pre-commit (lefthook, parallel on staged files)

`gitleaks protect --staged` · Spotless (`services/**`) · Buf (`services/proto`)
· Biome `check --fix` (`scripts/`, `app/`) · Prettier + markdownlint for
md/json/yaml/toml/sh elsewhere. `commit-msg` runs commitlint
(`commitlint.config.js`); `pre-push` blocks direct pushes to `main`.
