# notification — Service Architecture

> **Current-state architecture.** This document describes what exists in the
> `notification` service today (B1.0.0 phase). For hexagonal layering, DDD
> patterns, and shared conventions see `services/AGENTS.md`. For the system-wide
> view see `docs/architecture.md`.

## 1. Service Identity

- **Name:** notification
- **Package:** `io.github.smiskinext.notification`
- **Bounded Context:** Real-time fan-out and invitation email — a stateless hub
  with no database.
- **Responsibility:** (Target) consume meeting domain events and fan them out to
  clients (SSE) and to invitees (email via Resend). Today the service is a
  skeleton: only the Spring Boot bootstrap and consumer-group configuration
  exist.

## 2. Context & Dependencies

- **Upstream (callers):** Kafka (event source), Kong Gateway (SSE, once the
  endpoint is built).
- **Downstream (dependencies):** Resend (email API), Valkey (Pub/SUB fan-out —
  target). No database, no Flyway.

### C4 — System Context

> The diagrams below are written in [D2](https://d2lang.com) using C4 shapes. D2
> does not parse Markdown — extract each `d2` block into its own file before
> rendering (`d2 --layout elk <file>.d2 out.svg`).

```d2
vars: {d2-config: {layout-engine: elk}}
direction: down

meet: "meet\n[Software System]\nemits meet.* events"
notification: "notification\n[Software System]\nfan-out hub: SSE + invitation email\n(stateless, no database)"
client: "Jira user\n[Person]\nreceives real-time updates" {shape: person}
resend: "Resend\n[Software System]\ntransactional email"

meet -> notification: "meet.* events (via Kafka)"
notification -> resend: "send invitation email"
notification -> client: "SSE (real-time, via Kong)"
```

### C4 — Container

```d2
vars: {d2-config: {layout-engine: elk}}
direction: down

client: "Jira user\n[Person]" {shape: person}
kong: "Kong Gateway\n[Container]" {shape: hexagon}
kafka: "Kafka\n[CloudEvents · key = tenant_id]" {shape: queue}

notification: "notification" {
  app: "notification\n[Spring Boot]\nno database"
}

resend: "Resend\n[External · email API]"
valkey: "Valkey\n[pub/sub fan-out — target]" {shape: cylinder}

kafka -> notification.app: "consume meet.* (consumers — target)"
notification.app -> resend: "send email (adapter — target)"
client -> kong: "SSE"
kong -> notification.app: "SSE (endpoint — target)"
notification.app -> valkey: "pub/sub fan-out across instances (target)"
```

## 3. API Surface

- **Endpoint groups:** None implemented yet. **Target:** an SSE endpoint for
  real-time client fan-out.
- **Full spec:** No `openapi.yaml` is generated for this service (no
  presentation layer yet).

## 4. Events

### Published

None. `notification` is a consumer/side-effect service and does not publish
domain events.

### Consumed

No consumer code exists yet (`infrastructure/messaging/` is `.gitkeep`). The
config declares three consumer groups, which map to the `meet` topics below as
the intended sources.

| Topic (target source)                             | Consumer group (config key)                                                   | Action (target)                   |
| ------------------------------------------------- | ----------------------------------------------------------------------------- | --------------------------------- |
| `meet.meeting.invitations-sent`                   | `notification-meeting-invitations` (invitation-consumer-group)                | Send invitation emails via Resend |
| `meet.meeting.invite-tokens-invalidated`          | `notification-meeting-invite-invalidated` (invite-invalidated-consumer-group) | Notify affected invitees          |
| `meet.invitee.accepted` / `meet.invitee.declined` | `notification-invitee-responded` (invitee-responded-consumer-group)           | Fan out invitee responses         |

> Consumer-group names come from
> `services/notification/src/main/resources/application.yaml`
> (`app.notification.kafka.*`). The topic mapping reflects intended sources; no
> `@KafkaListener` is wired yet.

## 5. Data Stores

No database. `notification` has no Flyway migrations and no persistence layer.
Valkey Pub/Sub is the target fan-out mechanism across instances but is not yet
configured in this service.

## 6. External Integrations

- **Resend (email API)**
    - Purpose: transactional invitation email delivery.
    - Method: REST API. Credentials are supplied in Kubernetes via
      `ZMS_RESEND_API_KEY`, `ZMS_RESEND_FROM_EMAIL`, `ZMS_RESEND_FROM_NAME`
      (`services/k8s/base/services/notification.yaml`). No email adapter exists
      in code yet (`infrastructure/email/` is `.gitkeep`).
- **Valkey (target)**
    - Purpose: SSE Pub/Sub fan-out across notification instances.
    - Method: Redis Pub/Sub. Not yet configured in this service.

## 7. Operations

- **Configuration** (`src/main/resources/application.yaml`)
    - `spring.kafka.bootstrap-servers` — Kafka connection (consumer).
    - `app.notification.kafka.invitation-consumer-group` — default
      `notification-meeting-invitations`.
    - `app.notification.kafka.invite-invalidated-consumer-group` — default
      `notification-meeting-invite-invalidated`.
    - `app.notification.kafka.invitee-responded-consumer-group` — default
      `notification-invitee-responded`.
    - Kubernetes-supplied: `ZMS_RESEND_API_KEY`, `ZMS_RESEND_FROM_EMAIL`,
      `ZMS_RESEND_FROM_NAME`, `INVITATION_JOIN_BASE_URL`.
- **Build & Test**
    - Build: `./services/gradlew -p services/notification build`
    - Test: `./services/gradlew -p services/notification test`
    - See `services/AGENTS.md` for the full command reference.
- **Deployment**
    - Manifest: `services/k8s/base/services/notification.yaml` (actuator only,
      no public routes; `replicas: 1`).
    - Notes: `docs/architecture.md` targets multiple instances with Valkey
      Pub/Sub fan-out; current manifest runs a single replica.

## 8. Service-Specific Notes

### Notable Concerns

- **Skeleton service.** Only `NotificationApplication` and consumer-group config
  exist. The `domain`, `application`, and all `infrastructure` adapters
  (`messaging`, `email`, `sse`) are empty (`.gitkeep`). No consumers, no email
  sender, no SSE endpoint yet.
- **No database, no Flyway** — by design. State is derived entirely from Kafka
  events.
- **Topic naming divergence:** `docs/architecture.md` notes notification was
  historically tied to `meeting-management.*` topics; the target sources are the
  `meet.*` topics listed above. Consumer groups are already named for the new
  scheme.
- **Event-carried identity (target):** invitee identity should be fully carried
  in events, removing any gRPC `user-management` lookup. No gRPC channel is
  configured in this service today.

### Glossary

- **SSE:** Server-Sent Events — one-way real-time channel from server to client.
- **Resend:** Third-party transactional email API used for invitation delivery.
- **Fan-out:** Distributing a single event to many connected clients, targeted
  via Valkey Pub/Sub across instances.

### Last Updated

- 2026-07-12
