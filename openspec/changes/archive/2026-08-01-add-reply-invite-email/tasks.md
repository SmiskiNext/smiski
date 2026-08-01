## 1. Proto: email-reply event contract

- [x] 1.1 Add `invitee_email_reply.proto` in `services/proto` defining the
      `meet.invitee.email-reply.received` message with `calendar_uid`,
      `invitee_email`, and `status` fields (package
      `io.github.smiskinext.event.meet.v1`)
- [x] 1.2 Run `./services/gradlew bufFormatApply` and build proto to generate
      Java types ← (verify: generated type present, buf lint clean)

## 2. Meet: instant meeting time range (create-instant-meeting delta)

- [x] 2.1 Add configurable default instant-meeting duration property (e.g.
      `app.meet.instant.default-duration`, default `PT1H`) via
      `@ConfigurationProperties`
    - `application.yaml`, validating it is strictly positive
- [x] 2.2 Change `Meeting.instant(...)` to build
      `MeetingTimeRange.of(now, now + defaultDuration)` instead of `null` (pass
      the duration in from the application service)
- [x] 2.3 Update `CreateInstantMeetingApplicationService` to read the configured
      duration and pass it into `Meeting.instant(...)`
- [x] 2.4 Confirm `MeetingCreatedEvent` and `MeetingInvitationsCreatedEvent` now
      carry non-null start/end for instant meetings (via `recordInvitationsSent`
      reading the now-present `timeRange`) ← (verify: instant meeting persists
      non-null startTime/endTime with start<end, and invitations-sent event
      carries them — matches create-instant-meeting spec)

## 3. Meet: lookup paths for reply resolution

- [x] 3.1 Add `MeetingRepository.findByCalendarUid(String calendarUid)` port
      method
- [x] 3.2 Implement it in the JPA repository + `MeetingRepositoryAdapter` (query
      by `calendar_uid`)
- [x] 3.3 Add Flyway migration `V<n>__meetings_calendar_uid_index.sql` creating
      an index on `calendar_uid` (spanning partitions)
- [x] 3.4 Add
      `MeetingInviteeRepository.findByMeetingIdAndEmail(UUID meetingId, Email email)`
      port method + JPA/adapter implementation ← (verify: both lookups return
      the correct row and the index migration applies cleanly on a clean DB)

## 4. Meet: apply email-driven invitee response

- [x] 4.1 Add application command + `ApplyEmailInviteeResponseUseCase` interface
      and `*ApplicationService` impl accepting
      `(calendarUid, inviteeEmail, status)`, authorizing by email match only (no
      `AccountContext`)
- [x] 4.2 In the service: resolve meeting by `calendarUid`, resolve invitee by
      meeting + email; ignore (log, no state change) when meeting or active
      invitee is not found
- [x] 4.3 Apply accept/decline/tentative via existing domain transitions; treat
      a disallowed/same-status transition or soft-removed invitee as a no-op (no
      error, no event); persist + publish the existing invitee-response event on
      real change
- [x] 4.4 Add the Kafka consumer for `meet.invitee.email-reply.received` (decode
      CloudEvent JSON, log-and-skip malformed) that invokes the use case ←
      (verify: unknown UID/non-invitee ignored, duplicate reply idempotent with
      no duplicate event, valid reply updates status and emits invitee-response
      event — matches reply-invite-email trust/idempotency requirements)

## 5. Notification: inbound webhook + parsing + event publish

- [x] 5.1 Add the `svix` dependency and a webhook-secret config property
      (fail-fast if required-but-absent, consistent with existing email config
      style)
- [x] 5.2 Add `POST /webhooks/resend/inbound` controller; permit the path in
      `SecurityConfig`; verify the Svix signature inside the handler and reject
      invalid/missing signatures with no side effects
- [x] 5.3 Add a Resend received-email client that fetches the full message +
      `text/calendar` attachment referenced by the metadata-only payload
- [x] 5.4 Parse the `text/calendar` `METHOD:REPLY` with `biweekly` to extract
      `UID`, responding `ATTENDEE` email, and `PARTSTAT`; map PARTSTAT →
      response; skip unparseable/unknown-status payloads without terminating
- [x] 5.5 Publish `meet.invitee.email-reply.received` via the existing
      `KafkaTemplate<String, CloudEvent>`; publish nothing when extraction fails
      ← (verify: valid signed calendar reply → event with UID+email+status;
      invalid signature/no-calendar/unparseable → no event and endpoint stays
      available — matches reply-invite-email ingestion/parsing/publication
      requirements)

## 6. Tests (derived from spec scenarios)

- [x] 6.1 Update instant-meeting integration test
      (`MeetingControllerIntegrationTest` instant cases) and unit tests to
      assert non-null start/end with start<end (create-instant-meeting: "Instant
      meeting carries a start and end time", "Invitations event carries zone and
      times")
- [x] 6.2 Unit test configured vs built-in default duration
      (create-instant-meeting: "Configured duration is applied", "Missing
      configuration falls back to the built-in default")
- [x] 6.3 Meet application test: email reply updates matching invitee +
      publishes invitee-response event (reply-invite-email: "Email reply updates
      the matching invitee", "Response reuses organizer reply notification")
- [x] 6.4 Meet application test: unknown meeting / non-invitee email ignored;
      duplicate reply idempotent; removed invitee not applied
      (reply-invite-email: "Unknown meeting or non-invitee email is ignored",
      "Duplicate reply is idempotent", "Reply to a removed invitation is not
      applied")
- [x] 6.5 Notification test: valid signed webhook parses and publishes event
      (reply-invite-email: "Valid signed webhook is accepted", "Accepted reply
      is extracted", "Tentative and declined replies are extracted", "Extracted
      reply produces an event")
- [x] 6.6 Notification test: invalid/missing signature rejected, non-calendar
      and unparseable mail skipped with no event (reply-invite-email: "Invalid
      or missing signature is rejected", "Non-calendar inbound mail is ignored",
      "Unparseable or unknown-status reply is skipped", "No event when
      extraction fails") ← (verify: all reply-invite-email scenarios have a
      corresponding passing test and the notification consumer/webhook stays
      available after skips)

## 7. Verification & docs

- [x] 7.1 Run `./services/gradlew -p services/meet build` and
      `./services/gradlew -p services/notification build`; fix failures
- [x] 7.2 Run `./services/gradlew spotlessApply` and `pnpm run openapi` if any
      OpenAPI/proto surface changed ← (verify: both service builds green,
      formatting/lint clean)

## 8. Post-verification fixes

- [x] 8.1 FIX CRITICAL: `findByCalendarUid` JPQL query bypassed by Hibernate
      `@TenantId` discriminator on tenant-less Kafka thread — converted to
      native query (`nativeQuery = true`) matching the
      `findScheduledExpiredIdsAcrossTenants` pattern
- [x] 8.2 FIX CRITICAL: `ApplyEmailInviteeResponseApplicationService` sets
      `TenantContext` from the resolved meeting's tenantId before invitee lookup
      and save (try/finally with clear) — matching the
      `NoShowMeetingCancelerApplicationService` pattern
- [x] 8.3 Add controller-level integration test for
      `ResendInboundWebhookController` covering valid signature → processing +
      invalid/missing signature → 400 with no event published
- [x] 8.4 Add cross-tenant Hibernate integration test
      (`CrossTenantEmailReplyLookupIntegrationTest`) proving `findByCalendarUid`
      resolves from a tenant-less context and `findByMeetingIdAndEmail`
      correctly requires matching tenant context
