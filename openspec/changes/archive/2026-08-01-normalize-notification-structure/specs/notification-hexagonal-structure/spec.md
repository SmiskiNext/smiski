## ADDED Requirements

### Requirement: Notification service uses hexagonal UseCase layer for all inbound triggers

The `notification` service SHALL route every inbound trigger (Kafka message or
HTTP request) through a `*UseCase` interface defined in `application/usecase/`.
No Kafka consumer or `@RestController` SHALL inject a concrete class or a domain
outbound port directly as its primary processing collaborator.

#### Scenario: Kafka consumer invokes UseCase interface

- **WHEN** a Kafka consumer receives a CloudEvent
- **THEN** it constructs a Command record and delegates to the injected
  `*UseCase` interface, not to any concrete infrastructure class

#### Scenario: Controller injects UseCase interface

- **WHEN** `MeetingEventsController` or `ResendInboundWebhookController` handles
  a request
- **THEN** the controller holds a field typed as a `*UseCase` interface, not a
  concrete `*ApplicationService` or infrastructure class

### Requirement: SseConnectionManager resides in infrastructure/sse/

`SseConnectionManager` SHALL live in `infrastructure/sse/`. No class in
`application/` SHALL import or instantiate `SseConnectionManager` directly.

#### Scenario: SSE push goes through SseRelayPort

- **WHEN** a `*ApplicationService` needs to push an SSE event to connected
  clients
- **THEN** it calls a method on `SseRelayPort` (defined in `domain/port/`)

#### Scenario: SSE subscription goes through SseSubscriptionPort

- **WHEN** `SubscribeMeetingEventsApplicationService` handles a subscription
  command
- **THEN** it calls `SseSubscriptionPort` (defined in `application/`) and
  returns the resulting `SseEmitter` wrapped in `SubscribeMeetingEventsResult`

### Requirement: Domain layer remains framework-agnostic

All classes under `domain/` SHALL contain zero Spring or JPA imports
(`org.springframework.*`, `jakarta.persistence.*`). This is enforced by the
existing `CleanArchitectureTest`.

#### Scenario: SseRelayPort contains no Spring types

- **WHEN** `SseRelayPort.java` in `domain/port/` is inspected
- **THEN** no method signature or import references `org.springframework.*`

#### Scenario: NotificationError is a sealed interface with no framework dependency

- **WHEN** `NotificationError.java` in `domain/` is inspected
- **THEN** it is a `sealed interface` extending `DomainError` from shared, with
  no Spring or JPA imports

### Requirement: NotificationError provides typed error variants

`domain/NotificationError.java` SHALL be a sealed interface with the variant
`InvalidSignature`. Email send failures intentionally propagate as exceptions so
Kafka retry/DLQ handles them (per Decision 4); they are not modeled as
`NotificationError` variants. Use-case methods that can fail SHALL return
`Result<T, NotificationError>`.

#### Scenario: Webhook verification failure mapped via Result

- **WHEN** `ProcessInboundEmailReplyApplicationService.execute()` is called with
  a payload whose Svix signature is invalid
- **THEN** the method returns
  `Result.failure(new NotificationError.InvalidSignature())` without throwing an
  exception

#### Scenario: Controller maps InvalidSignature to HTTP 400

- **WHEN** `ResendInboundWebhookController` receives a `Result` containing
  `NotificationError.InvalidSignature`
- **THEN** it returns `ResponseEntity` with status 400 and no response body

### Requirement: application/result/ contains UseCase result records

The directory `application/result/` SHALL exist and contain one result record
per UseCase. No directory named `application/response/` SHALL exist under the
notification service.

#### Scenario: Result records are framework-agnostic

- **WHEN** any class in `application/result/` is inspected
- **THEN** no field or import references Jackson annotations (`@JsonProperty`,
  `@JsonIgnore`) or OpenAPI annotations (`@Schema`)

### Requirement: OpenAPI specification is generated from tests

`NotificationOpenApiGenerationTest` SHALL exist in `integrationTest/` and emit
`services/notification/openapi.yaml` when `generateOpenApiDocsFromTests` is
executed.

#### Scenario: openapi.yaml contains both REST endpoints

- **WHEN**
  `./services/gradlew -p services/notification generateOpenApiDocsFromTests` is
  executed
- **THEN** `services/notification/openapi.yaml` is written and contains paths
  for `/meetings/{id}/events` and `/webhooks/resend/inbound`

### Requirement: Naming conventions match ArchUnit rules

All new application-layer impls SHALL end in `*ApplicationService`. All new
infrastructure outbound-port adapters SHALL end in `*Adapter`. The existing
`InboundEmailReplyService` SHALL be renamed to
`InboundEmailReplyProcessorAdapter`.

#### Scenario: ArchUnit test passes after refactor

- **WHEN** `./services/gradlew -p services/notification test` is executed
- **THEN** `ArchitectureTest` passes with zero violations
