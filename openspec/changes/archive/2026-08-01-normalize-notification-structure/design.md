## Context

`services/notification` consumes Kafka events and sends emails (Resend) and SSE
streams to Jira issue panel clients. The service was scaffolded with the correct
hexagonal directory skeleton but the wiring was never completed:

- `application/usecase/`, `service/`, `command/`, `query/` are empty
  directories.
- `SseConnectionManager` (a Spring `@Component` using `SseEmitter`) lives in
  `application/sse/` — an application-layer package — violating the rule that
  the domain and application layers must be framework-agnostic.
- Kafka consumers inject `SseConnectionManager` directly (concrete class)
  instead of a UseCase interface.
- `MeetingEventsController` injects `SseConnectionManager` (concrete class)
  instead of an abstraction.
- `application/response/` exists but the project convention is
  `application/result/`.
- `InboundEmailReplyService` should be named `*Adapter` per the
  `*RepositoryAdapter` naming convention for outbound-port implementations.

Reference: `services/meet` is the most complete implementation and defines the
target pattern.

## Goals / Non-Goals

**Goals:**

- Populate the application layer with proper UseCase interfaces and
  ApplicationService implementations for all 7 inbound triggers (5 Kafka
  consumers + 2 controllers).
- Move `SseConnectionManager` to `infrastructure/sse/`; expose it only through
  port interfaces so no outer layer holds a concrete dependency.
- Define `domain/port/SseRelayPort` (push-side, Java-only) and
  `application/SseSubscriptionPort` (subscribe-side, may reference
  `SseEmitter`).
- Fix all naming and directory deviations: rename `application/response/` →
  `application/result/`; rename `InboundEmailReplyService` →
  `InboundEmailReplyProcessorAdapter`; move `SseProperties` to
  `infrastructure/config/`.
- Add `NotificationOpenApiGenerationTest` so the service emits `openapi.yaml` on
  `generateOpenApiDocsFromTests`.
- All 7 new ApplicationServices have corresponding fast unit tests in `test/`.

**Non-Goals:**

- Behavioral changes to SSE streams, email content, or endpoints.
- Extracting domain Value Objects (`MeetingId`, `AccountId`, etc.).
- Adding domain events to `domain/event/`.
- Adding `domain/projection/`.
- Any change to Kafka topics, proto contracts, or DB schema.

## Decisions

### Decision 1: Two SSE port interfaces instead of one

**Problem**: `SseEmitter` is a Spring MVC class. The domain layer must remain
framework-agnostic (no Spring imports). The push side (Kafka consumer →
emitters) and the subscribe side (controller → register emitter) have different
concerns.

**Decision**: Define two interfaces:

| Interface             | Location              | Returns      | Implemented by         |
| --------------------- | --------------------- | ------------ | ---------------------- |
| `SseRelayPort`        | `domain/port/`        | `void`       | `SseConnectionManager` |
| `SseSubscriptionPort` | `application/` (root) | `SseEmitter` | `SseConnectionManager` |

`SseRelayPort` stays in domain (pure Java, no Spring). `SseSubscriptionPort`
lives at the application layer root to allow `SseEmitter` in its signatures —
the application layer is not required to be Spring-free (only domain is).

**Alternatives considered**:

- Put both in `domain/port/` with `Object` return and cast in controllers —
  rejected (unsafe, obscures intent).
- Return a subscription token from a domain port and let the controller fetch
  the emitter separately — rejected (two-step anti-pattern, more moving parts).
- Keep `SseConnectionManager` in `application/sse/` with a non-Spring interface
  — rejected (the class itself imports Spring and cannot stay in application).

### Decision 2: SubscribeMeetingEventsUseCase wraps SseSubscriptionPort

`MeetingEventsController` injects `SubscribeMeetingEventsUseCase`. The
ApplicationService impl delegates to `SseSubscriptionPort.subscribe(id)` and
wraps the `SseEmitter` in `SubscribeMeetingEventsResult`. The controller
extracts it and returns it to Spring MVC.

This satisfies "controllers inject the interface, never the impl" without
placing Spring types in the domain layer.

### Decision 3: ProcessInboundEmailReplyUseCase owns webhook verification

Currently `ResendInboundWebhookController` calls `WebhookVerifier` directly.
After this change, the controller delegates to
`ProcessInboundEmailReplyUseCase`, which calls both `WebhookVerifier` (domain
outbound port) and `InboundEmailReplyProcessor` (domain outbound port). The
controller handles the `Result` and maps `NotificationError.InvalidSignature` to
HTTP 400.

This keeps signature verification inside the application layer boundary and out
of the controller, making the controller testable without infrastructure.

### Decision 4: 5 email/relay ApplicationServices are thin orchestrators

For each Kafka consumer use case (RelayJoinCreated, RelayJoinResolved,
SendMeetingInvitationEmail, SendMeetingInfoUpdatedEmail,
SendInviteeRespondedEmail), the ApplicationService decodes no data — the
consumer already parsed the proto. The service receives a typed Command, calls
the relevant outbound ports (`SseRelayPort`, `EmailSender`,
`PendingJoinRequestStore`, `JoinDecisionStore`), and returns a
`Result<Void, NotificationError>`.

### Decision 5: NotificationError variants

One variant covers this service's domain-level failure surface:

- `InvalidSignature` — webhook signature verification failed

Email send failures intentionally propagate as exceptions (not `Result` errors)
so the Kafka consumer's built-in retry and dead-letter-queue handling engages
automatically. This avoids duplicating retry logic and keeps the email
ApplicationServices as thin pass-through orchestrators.

Kafka consumer errors (malformed proto, missing fields) are caught by the
consumer and logged/skipped rather than propagated as `NotificationError` —
consistent with the existing "log and skip" pattern.

## Architecture after refactor

```
┌──────────────────────────────────────────────────────┐
│ presentation                                         │
│  MeetingEventsController ──→ SubscribeMeetingEventsUseCase  │
│  ResendInboundWebhookController ──→ ProcessInboundEmailReplyUseCase │
└────────────────────────┬─────────────────────────────┘
                         │ (inbound ports)
┌────────────────────────▼─────────────────────────────┐
│ application                                          │
│  usecase/  *UseCase interfaces (7)                   │
│  service/  *ApplicationService impls (7)             │
│  command/  *Command records (7)                      │
│  result/   *Result records (7)                       │
│  SseSubscriptionPort  ◄──────────────────────────┐  │
└────────────────────────┬──────────────────────────│──┘
                         │                          │
┌────────────────────────▼──────────────────────────│──┐
│ domain                                            │  │
│  port/  SseRelayPort  ◄───────────────────────┐  │  │
│         EmailSender, WebhookVerifier          │  │  │
│         InboundEmailReplyProcessor            │  │  │
│         PendingJoinRequestStore               │  │  │
│         JoinDecisionStore                     │  │  │
│  model/ CalendarEmail, JoinDecision,          │  │  │
│         PendingJoinRequest                    │  │  │
│  NotificationError, NotificationErrorCode     │  │  │
└───────────────────────────────────────────────│──│──┘
                                                │  │
┌───────────────────────────────────────────────▼──▼──┐
│ infrastructure                                       │
│  sse/  SseConnectionManager                         │
│        (implements SseRelayPort + SseSubscriptionPort) │
│  email/  ResendEmailSender, InboundEmailReplyProcessorAdapter │
│          SvixWebhookVerifier                        │
│  messaging/  JoinCreatedEventConsumer (→ RelayJoinCreatedUseCase)  │
│              JoinResolvedEventConsumer (→ RelayJoinResolvedUseCase) │
│              + 3 email consumers                    │
│  persistence/ adapters                              │
│  config/  SseProperties, KafkaConfig, RedisConfig   │
└──────────────────────────────────────────────────────┘
```

## SSE relay flow (after refactor)

```
Kafka → JoinCreatedEventConsumer
          │
          ▼ RelayJoinCreatedCommand(meetingId, pendingRequest)
        RelayJoinCreatedUseCase
          │
          ▼
        RelayJoinCreatedApplicationService
          ├─→ PendingJoinRequestStore.upsert(request)
          └─→ SseRelayPort.pushJoinRequestCreated(meetingId, request)
                │
                ▼
              SseConnectionManager (infrastructure/sse/)
                └─→ pushes to registered SseEmitter instances
```

## Risks / Trade-offs

- [`SseEmitter` in application layer] `SseSubscriptionPort` returns Spring's
  `SseEmitter`. AGENTS.md restricts Spring imports only in `domain/`; the
  application layer is allowed to use Spring types for technical ports. →
  Mitigation: document the trade-off explicitly; `SseRelayPort` (domain) stays
  Java-only.

- [ArchUnit CleanArchitectureTest] Adding `SseSubscriptionPort` at application
  root and having ApplicationService depend on it must not break existing
  ArchUnit rules. → Mitigation: run
  `./services/gradlew -p services/notification test` after each structural
  change; fix rules before adding next layer.

- [Large changeset] ~35 files in a single PR; risk of merge conflict and missed
  file. → Mitigation: tasks are ordered layer-by-layer (domain → application →
  infra → presentation → tests); each layer compiles before the next starts.

## Migration Plan

Pure code refactor — no DB changes, no Kafka topic changes, no API contract
changes.

1. Add `domain/` additions (`NotificationError`, `NotificationErrorCode`,
   `SseRelayPort`)
2. Add `application/` shell (ports, usecase interfaces, commands, results)
3. Add `application/service/` impls
4. Add `infrastructure/sse/SseConnectionManager` at new location; update
   infrastructure config
5. Delete `application/sse/` once all references are migrated
6. Update consumers and controllers
7. Add presentation sub-packages and DTOs
8. Add tests
9. Run `test` + `integrationTest` + `spotlessApply`

Rollback: revert all files; no DB migrations to undo.

## Open Questions

None — all structural decisions resolved in planning.
