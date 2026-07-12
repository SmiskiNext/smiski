# Architecture Overview

This document gives agents a fast, high-level understanding of the Smiski
codebase. Smiski is an online meeting module embedded in Jira via an Atlassian
**Forge** app and powered by **LiveKit**. Update this document as the codebase
evolves.

> For the detailed design (D2 diagrams, event flows, CQRS routing, retention
> state machine), see the full system architecture notes in `docs/`.

## 1. Project Structure

Monorepo: Spring Boot 4 / Java 25 microservices (hexagonal DDD) under
`services/`, plus an Atlassian Forge app under `app/`.

```shell
[Repo Root]/
├── services/       # Backend microservices (hexagonal: domain → application → infrastructure → presentation)
│ ├── tenant/       # Tenancy source of truth (Forge install/upgrade/uninstall)
│ ├── meet/         # Meeting core: CRUD, join requests, LiveKit token issuance
│ ├── record/       # Recording lifecycle driven by LiveKit Egress
│ ├── notification/ # Real-time SSE hub + invitation email (no database)
│ ├── proto/        # Shared protobuf / Buf definitions
│ ├── docker/       # docker-compose + allowlisted secrets (.env)
│ └── k8s/          # Kubernetes manifests
├── app/            # Atlassian Forge app (UI Kit) embedded in a Jira issue panel
├── scripts/        # `pnpm smiski` CLI (citty + tsx + zx)
├── build-logic/    # Gradle convention plugins
├── openspec/       # Specs / change proposals
├── requirements/   # Requirement docs
├── docs/           # Project documentation (this file)
├── zms/            # Legacy Zero Meeting System snapshot (gitignored, read-only reference)
├── .mise.toml      # Pinned toolchain (Java 25, node, pnpm, gitleaks, lefthook, buf)
├── AGENTS.md       # Guidance index for agents
└── README.md       # Project overview
```

## 2. High-Level System Diagram

A C4 container-level view. The system is a stateless control plane (four Spring
Boot services) fronted by Kong and coordinated asynchronously through Kafka.
Media never touches the backend — LiveKit (an SFU) forwards it directly between
participants.

> The diagram below is written in [D2](https://d2lang.com). D2 does not parse
> Markdown — extract the `d2` block into its own file before rendering.

```d2
# ELK layout engine handles layered diagrams with many edges far better
# than the default. Render with:  d2 --layout elk system-context.d2 out.svg
vars: {
  d2-config: {
    layout-engine: elk
  }
}
direction: down

# ── Layer 1: external actor ──────────────────────────────
jira: "Jira Cloud" {
  shape: cloud
  forge: "Forge App\n[Container: UI Kit]"
}

# ── Layer 2: edge / gateway ──────────────────────────────
kong: "Kong Gateway\n[Container]\nverify JWT · inject tenant/account" {
  shape: hexagon
}

# ── Layer 3: control plane (stateless Spring Boot) ───────
system: "Smiski — Control Plane" {
  tenant: "tenant\n[Spring Boot]"
  meet: "meet\n[Spring Boot]"
  record: "record\n[Spring Boot]"
  notification: "notification\n[Spring Boot]"
}

# ── Layer 4: event backbone ──────────────────────────────
kafka: "Kafka\n[CloudEvents · key = tenant_id]" {
  shape: queue
}

# ── Layer 5: data & media plane ──────────────────────────
data: "Data & Media Plane" {
  tenantdb: "tenant_db\n[PostgreSQL]" { shape: cylinder }
  meetdb: "meet_db\n[PostgreSQL\nprimary + replica]" { shape: cylinder }
  recdb: "record_db\n[PostgreSQL]" { shape: cylinder }
  valkey: "Valkey\n[pub/sub · read models]" { shape: cylinder }
  livekit: "LiveKit\n[SFU + Egress]"
  rustfs: "RustFS\n[S3-compatible]" { shape: cylinder }
}

# ── Request flow (top → down) ────────────────────────────
jira.forge -> kong: "Forge Remote (JWT / JWKS)"
kong -> system.tenant: "REST"
kong -> system.meet: "REST"
kong -> system.record: "REST"
kong -> system.notification: "SSE"

# ── Event flow (services → Kafka → consumers) ────────────
system.tenant -> kafka: "tenant.*"
system.meet -> kafka: "meet.*"
system.record -> kafka: "record.*"
kafka -> system.meet: "tenant.* projection"
kafka -> system.record: "tenant.* projection"
kafka -> system.notification: "consume"

# ── Data & media flow (services → stores) ────────────────
system.tenant -> data.tenantdb
system.meet -> data.meetdb
system.meet -> data.valkey
system.meet -> data.livekit: "token / webhook"
system.record -> data.recdb
system.record -> data.livekit: "egress"
system.record -> data.rustfs
system.notification -> data.valkey: "pub/sub"
data.livekit -> data.rustfs: "upload"
```

- **Request flow:** Forge → Kong → services (REST + SSE).
- **Event flow:** services publish domain events to Kafka via a transactional
  outbox; consumers project tenant state and drive SSE / email.
- **Media flow:** clients connect directly to LiveKit using tokens minted by
  `meet`; recordings are uploaded by LiveKit Egress to RustFS.

## 3. Core Components

### 3.1. Frontend

- **Name:** Forge App (Atlassian UI Kit)
- **Description:** Embeds online meetings into a Jira issue panel. Identity is
  derived from Jira (no separate login).
- **Technologies:** Atlassian Forge, UI Kit, Forge Remote.
- **Deployment:** Atlassian Forge platform.

### 3.2. Backend Services

All services are stateless Spring Boot (Java 25) apps using hexagonal DDD and
the database-per-service pattern. They communicate asynchronously over Kafka
(CloudEvents), keyed by `tenant_id` (the Jira `cloudId`).

#### 3.2.1. tenant

- **Description:** Source of truth for tenancy — Forge app install / upgrade /
  uninstall; emits `tenant.app.installed` / `tenant.app.uninstalled`.
- **Technologies:** Spring Boot, Postgres, Kafka.
- **Deployment:** Kubernetes.

#### 3.2.2. meet

- **Description:** Meeting core — CRUD, join requests, LiveKit token issuance.
  Read-heavy with write bursts (join/leave webhooks); uses CQRS (level 2) with a
  read replica and Valkey live read models.
- **Technologies:** Spring Boot, Postgres (primary + replica), Valkey, Kafka,
  LiveKit.
- **Deployment:** Kubernetes.

#### 3.2.3. record

- **Description:** Independent recording lifecycle driven by LiveKit Egress
  (PENDING → RECORDING → COMPLETED / FAILED), with fixed retention (soft-delete
  15d, hard-delete 30d). Hot paths are transcoding (CPU) and RustFS upload
  (network), not the database.
- **Technologies:** Spring Boot, Postgres, Kafka, LiveKit Egress, RustFS (S3).
- **Deployment:** Kubernetes (egress node isolated).

#### 3.2.4. notification

- **Description:** Real-time SSE hub and invitation email sender. No database —
  it consumes Kafka events and fans out to clients via Valkey Pub/Sub. Invitee
  identity is fully event-carried (no gRPC lookup).
- **Technologies:** Spring Boot, Valkey, Kafka, Resend (email API).
- **Deployment:** Kubernetes (multiple instances).

## 4. Data Stores

### 4.1. Postgres (per service)

- **Type:** PostgreSQL.
- **Purpose:** Aggregate state per service (`tenant_db`, `meet_db` with
  primary + replica, `record_db`). Business tables are
  `PARTITION BY HASH (tenant_id)` with 16 partitions; `tenant_id` leads every
  PK/FK/unique constraint for partition pruning and local joins.
- **Key tables:** `tenants` (projection, unpartitioned), `meetings`,
  `participation_logs`, `meeting_invitees`, `invite_tokens`, `recordings`,
  `outbox_event`.

### 4.2. Kafka

- **Type:** Kafka (CloudEvents format).
- **Purpose:** Event backbone for inter-service communication. Topics follow
  `<service>.<aggregate>.<action>`; partition key `tenant_id` preserves
  per-tenant ordering. Populated by a transactional outbox poller
  (`FOR UPDATE SKIP LOCKED`).

### 4.3. Valkey

- **Type:** Valkey (Redis-compatible).
- **Purpose:** Cache, read models, and Pub/Sub. Holds LiveKit token cache, join
  request state and replay results, live participant read model, SSE Pub/Sub
  channels, and idempotency keys (webhook / consumer dedupe, TTL 1h).

### 4.4. RustFS

- **Type:** S3-compatible object storage.
- **Purpose:** Stores completed recording files uploaded by LiveKit Egress.

## 5. External Integrations / APIs

- **Jira Cloud / Atlassian Forge** — host platform and identity source;
  integration via Forge Remote (JWT / JWKS).
- **LiveKit (SFU + Egress)** — real-time media forwarding and server-side
  recording; integration via tokens and webhooks.
- **Resend** — transactional email delivery for meeting invitations; REST API.

## 6. Deployment & Infrastructure

- **Cloud / Platform:** Kubernetes (manifests in `services/k8s/`); Atlassian
  Forge for the frontend app.
- **Key services:** Kong Gateway (JWT verification, tenant/account injection),
  Postgres, Kafka, Valkey, LiveKit, RustFS.
- **Toolchain:** Pinned via `.mise.toml` (Java 25, node, pnpm, gitleaks,
  lefthook, buf). Local orchestration through the `pnpm smiski` CLI.
- **CI/CD & quality gates:** Pre-commit hooks via lefthook (gitleaks, Spotless,
  Buf, Biome, Prettier, markdownlint); commitlint on commit-msg; direct pushes
  to `main` blocked on pre-push.

## 7. Security Considerations

- **Authentication:** JWT issued from Jira/Forge, verified at Kong (JWKS). No
  separate login.
- **Authorization / isolation:** Multi-tenant partitioning by `tenant_id` (=
  Jira `cloudId`); Kong injects tenant/account context into every request.
- **Idempotency:** Webhook and Kafka consumers de-duplicate via Valkey `idem:*`
  keys (TTL 1h) to guard against replays.
- **Secrets:** Allowlisted secrets loaded from `services/docker/.env`; gitleaks
  scans staged changes pre-commit.

## 8. Development & Testing Environment

- **Local setup:** `pnpm smiski setup` (mise tools + pnpm + hooks + .env), then
  `pnpm smiski dev` (infra up + backend services in parallel). Use
  `pnpm smiski doctor` for tool status.
- **Formatting / specs:** `pnpm lint` (markdownlint), `pnpm format` (prettier),
  `pnpm run openapi` (regenerate + lint service specs).
- **Build & test:** Gradle (backend). Detailed backend commands live in
  `services/AGENTS.md`; Forge commands in `app/AGENTS.md`.

## 9. Future Considerations / Roadmap

Known divergences between the target design and the current codebase:

- Standardize Kafka topic names to `<service>.<aggregate>.<action>`
  (notification still uses `meeting-management.*`).
- Remove the gRPC `user-management` dependency from notification (invitee
  identity is now event-carried).
- Build application/presentation layers and the outbox poller for `tenant`,
  `meet`, and `record`.
- Add Valkey Pub/Sub SSE fan-out across notification instances.
- Add the meeting-history archival job (deferred) and the recording retention
  job.
- Reconsider `AUTO_OFFSET_RESET`; use `earliest` for email/SSE-critical
  consumers.

## 10. Project Identification

- **Project Name:** Smiski
- **Repository URL:** [Insert Repository URL]
- **Primary Contact/Team:** [Insert Lead Developer/Team Name]
- **Date of Last Update:** 2026-07-12

## 11. Glossary / Acronyms

- **SFU:** Selective Forwarding Unit — LiveKit's media server that forwards
  streams between participants.
- **Egress:** LiveKit's server-side recording/export pipeline.
- **CQRS:** Command Query Responsibility Segregation — writes go to the primary
  DB, reads go to a replica and Valkey read models.
- **Outbox:** Transactional outbox pattern — events written in the same
  transaction as the aggregate, then published to Kafka by a poller.
- **Event-carried state transfer:** Events carry the data consumers need,
  removing synchronous cross-service lookups.
- **cloudId:** Jira site identifier, used as `tenant_id` for partitioning.
- **Forge:** Atlassian's app development platform hosting the frontend UI.
- **SSE:** Server-Sent Events — one-way real-time channel from server to client.
- **CloudEvents:** Standard event envelope format used for Kafka messages.
