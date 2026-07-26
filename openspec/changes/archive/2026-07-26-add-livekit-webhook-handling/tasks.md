## 1. Tenant-carrying token issuance

- [x] 1.1 Add a required `tenantId` field to `LiveKitTokenRequest` (domain value
      object); update its constructors/validation and both full and convenience
      factories/callers to compile.
- [x] 1.2 In `LiveKitAdapter.generateToken`, build a LiveKit `RoomConfiguration`
      with `name = roomName` and `metadata = tenantId` and attach it via
      `AccessToken.setRoomConfiguration(...)` for BOTH the HOST and PARTICIPANT
      branches.
- [x] 1.3 Pass the tenant into the token request from
      `CreateInstantMeetingApplicationService` (host token) and
      `RequestJoinApplicationService.admitImmediately` (participant token),
      sourcing the tenant from the meeting/command context. ← (verify: both call
      sites set tenantId; token room metadata carries tenant for host and
      participant flows per create-instant-meeting and join-meeting specs)

## 2. Participation-log repository lookups

- [x] 2.1 Add JPA queries to `ParticipationLogJpaRepository` for: active-by-SID
      (`livekitParticipantSid` match, `leftAt IS NULL`),
      active-by-meeting-and-identity (`meetingId` + `livekitIdentity`,
      `leftAt IS NULL`), and all-active-by-meeting (`meetingId`,
      `leftAt IS NULL`).
- [x] 2.2 Implement `findActiveBySid`, `findActiveByMeetingIdAndIdentity`, and
      `findActiveByMeetingId` in `ParticipationLogRepositoryAdapter` (replace
      the `UnsupportedOperationException` stubs) using the mapper to
      reconstitute domain aggregates. ← (verify: methods return correct active
      rows scoped by @TenantId; match the repository contract javadoc)

## 3. Webhook receiver endpoint (presentation)

- [x] 3.1 Register a `WebhookReceiver` Spring bean in the livekit config wired
      from `LiveKitProperties.apiKey`/`apiSecret`.
- [x] 3.2 Create `LiveKitWebhookController` with
      `@PostMapping("/webhooks/livekit")` consuming `application/webhook+json`,
      reading the RAW request body as a string and the `Authorization` header
      (no JSON `@RequestBody` binding before verification).
- [x] 3.3 Verify the payload via `WebhookReceiver.receive(rawBody, authHeader)`;
      on failure respond `401` with no side effects, on malformed/undecodable
      body respond a client error, on success enqueue and respond `200`. ←
      (verify: valid signature accepted, invalid/missing signature → 401 with no
      state change, malformed body rejected — matches livekit-webhook receiver
      scenarios)
- [x] 3.4 Confirm the endpoint resolves to `/api/1/webhooks/livekit` under the
      shared `ApiPathPrefix` and does not require the tenant header (no
      dependency on `TenantFilter` binding).

## 4. Asynchronous dispatch (internal topic)

- [x] 4.1 Define an internal messaging channel (Kafka topic + message shape
      carrying event type, room name, room metadata/tenant, participant
      identity, participant SID, participant attributes, and event timestamp)
      and a publisher invoked by the controller, keyed by room name.
- [x] 4.2 Add the Kafka consumer configuration needed to consume the internal
      webhook topic (alongside the existing producer config), using a consumer
      group so multiple meet instances share load. ← (verify: verified events
      are enqueued keyed by room name and endpoint returns 200 before DB work;
      per-room ordering preserved — matches fast-ack/ordering scenarios)

## 5. Webhook processing use case (application)

- [x] 5.1 Create `HandleLiveKitWebhookUseCase` (inbound port) and
      `HandleLiveKitWebhookApplicationService` implementation; parse
      `meeting-<uuid>` room names into a `MeetingId` and parse
      `<accountId>:<deviceId>` identities.
- [x] 5.2 Bind `TenantContext` from the message's room metadata for the
      processing transaction and clear it in a `finally`; when metadata carries
      no resolvable tenant, perform no tenant-scoped write and record the
      anomaly. ← (verify: tenant bound from room metadata; missing metadata not
      silently misattributed — matches tenant-resolution scenarios)
- [x] 5.3 Implement `room_started`: transition `SCHEDULED → RUNNING` (no-op if
      already RUNNING/COMPLETED or unknown meeting); enqueue
      `MeetingStartedEvent` only on real transition.
- [x] 5.4 Implement `participant_joined`: supersede an orphaned active session
      for the same meeting+identity, then create a `ParticipationLog`
      (account/device/role from identity+attributes) and assign the SID; skip if
      the SID is already recorded; enqueue `ParticipantJoinedEvent` only on real
      create.
- [x] 5.5 Implement `participant_left`: close the active log matching the event
      SID with `CloseReason.LEFT` and event-time `leftAt`; skip if already
      closed/unknown; enqueue `ParticipantLeftEvent` only on real close.
- [x] 5.6 Implement `room_finished`: transition `RUNNING → COMPLETED` (no-op if
      already COMPLETED/unknown) and close all still-active logs with `LEFT`;
      enqueue `MeetingCompletedEvent` only on real transition.
- [x] 5.7 Treat `track_*`, `egress_*`, `ingress_*`, and
      `participant_connection_aborted` as no-op acknowledgements. ← (verify: all
      four handled events map correctly and are idempotent on redelivery;
      non-handled events change no state — matches lifecycle/participant/no-op
      scenarios)
- [x] 5.8 Wire the consumer to invoke the use case and publish drained aggregate
      events through the existing outbox/`EventPublisher`.

## 6. Configuration alignment

- [x] 6.1 Update `services/meet/src/main/resources/application.yaml`: webhook
      path to the versioned integer scheme and the internal topic name/consumer
      settings.
- [x] 6.2 Align `services/docker/.env.example`, `services/docker/compose.yaml`
      (LiveKit `webhook.urls`), and `services/docker/caddy/Caddyfile` to route
      `/api/1/webhooks/livekit` to the meet service. ← (verify: LiveKit config,
      gateway route, and service path all resolve to the same integer-versioned
      path)

## 7. Tests (derived from spec scenarios)

- [x] 7.1 Unit (application): `room_started` starts a `SCHEDULED` meeting and
      enqueues a meeting-started event.
- [x] 7.2 Unit (application): `room_started` on an already-RUNNING/COMPLETED
      meeting is a no-op with no duplicate event.
- [x] 7.3 Unit (application): `participant_joined` creates a log, assigns the
      SID, sets role from attributes, and enqueues a participant-joined event.
- [x] 7.4 Unit (application): `participant_joined` supersedes an orphaned active
      session for the same identity before insert.
- [x] 7.5 Unit (application): duplicate `participant_joined` for an
      already-recorded SID creates no duplicate log.
- [x] 7.6 Unit (application): `participant_left` closes the matching active
      session with `LEFT`; redelivery / unknown SID is a no-op.
- [x] 7.7 Unit (application): `room_finished` completes a RUNNING meeting,
      closes all active logs with `LEFT`, and enqueues a meeting-completed
      event; already-COMPLETED is a no-op.
- [x] 7.8 Unit (application): tenant is bound from room metadata;
      missing/foreign tenant metadata performs no tenant-scoped write.
- [x] 7.9 Unit (application): `track_*`, `egress_*`, `ingress_*`, and
      `participant_connection_aborted` events change no state.
- [x] 7.10 Integration (presentation): valid signed webhook returns `200`;
      invalid/missing `Authorization` returns `401` with no state change;
      malformed body is rejected.
- [x] 7.11 Integration (infrastructure): the three new
      `ParticipationLogRepositoryAdapter` lookups return the correct active rows
      under `@TenantId` scoping.
- [x] 7.12 Unit/integration (livekit): issued HOST and PARTICIPANT tokens carry
      a room configuration whose metadata equals the tenant identifier. ←
      (verify: every ADDED/MODIFIED spec scenario has a corresponding test and
      all pass)

## 8. Verification

- [x] 8.1 Run `./services/gradlew spotlessApply` then
      `./services/gradlew -p services/meet test` (unit + ArchUnit) and fix
      violations.
- [x] 8.2 Run `./services/gradlew -p services/meet integrationTest`
      (Testcontainers) and fix failures.
- [x] 8.3 Run `./services/gradlew -p services/meet generateOpenApiDocsFromTests`
      and confirm the meet OpenAPI spec regenerates cleanly. ← (verify: full
      build green; webhook endpoint present/omitted per convention; no ArchUnit
      layering violations)
