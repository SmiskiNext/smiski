## Why

`services/notification` was scaffolded with the correct directory skeleton but
never wired to the hexagonal DDD pattern: the application layer is hollow
(usecase, service, command, query are empty), `SseConnectionManager` sits as a
Spring `@Component` in `application/sse/` instead of infrastructure, and
consumers/controllers inject concrete classes rather than interfaces. ArchUnit
naming rules and the clean-architecture dependency constraints are at risk of
failing as the service grows.

## What Changes

- Add `domain/NotificationError` (sealed interface) + `NotificationErrorCode`
  (enum) so use-case boundaries can return `Result<T, NotificationError>`.
- Add `domain/port/SseRelayPort` — pure-Java outbound port for SSE push
  operations.
- Add `application/SseSubscriptionPort` — application-layer interface for SSE
  emitter subscription (returns `SseEmitter`; kept outside domain to preserve
  domain framework-agnosticism).
- Create 7 `*UseCase` interfaces in `application/usecase/` (one per inbound
  trigger).
- Create 7 `*ApplicationService` impls in `application/service/` with
  `@Service`.
- Create 7 `Command` records in `application/command/` and 7 `Result` records in
  `application/result/` (rename from the erroneous `application/response/`).
- Move `SseConnectionManager` from `application/sse/` to `infrastructure/sse/`;
  it implements both `SseRelayPort` and `SseSubscriptionPort`.
- Move `SseProperties` from `application/sse/` to `infrastructure/config/`.
- Rename `InboundEmailReplyService` → `InboundEmailReplyProcessorAdapter`
  (naming convention: outbound-port adapters must end in `*Adapter`).
- Move SSE data DTOs (`JoinRequest*Data`) from `application/sse/` to
  `presentation/response/`.
- Add `presentation/request/` and `presentation/response/` sub-packages.
- Update 5 Kafka consumers to inject and call UseCase interfaces.
- Update 2 controllers to inject UseCase/port interfaces instead of concrete
  classes.
- Add `NotificationOpenApiGenerationTest` (service has REST endpoints but no
  spec generation test).
- Delete now-empty `application/sse/` package.

No external behavior changes: same SSE events, same email logic, same endpoints,
same Kafka topics.

## Capabilities

### New Capabilities

_None_ — this is a pure structural refactor. No new endpoints, events, or
behaviors are introduced.

### Modified Capabilities

_None_ — requirements of existing capabilities are unchanged. Only the internal
code structure is normalised to the hexagonal DDD pattern.

## Impact

- `services/notification/src/main/java/...notification/` — ~35 files
  added/moved/renamed
- `services/notification/src/test/java/...` — new ApplicationService unit tests
- `services/notification/src/integrationTest/java/...` — new OpenAPI generation
  test
- No API contract changes; no Kafka topic changes; no DB schema (service has
  none)
- ArchUnit `CleanArchitectureTest` will continue to pass (and become more strict
  as the UseCase layer is now populated)
