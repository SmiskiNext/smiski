# Implementation Tasks

## 1. Proto contract (proto module)

- [x] 1.1 Add `InviteeTentative` message to `invitee_response.proto` mirroring
      `InviteeDeclined`, with event id, tenant id, meeting id, inviter id,
      invitee id, invitee email, status, and response timestamp
- [x] 1.2 Add meeting-context fields (meeting title, start time, end time, zone
      id, organizer email, organizer display name, invitee display name,
      calendar uid, calendar sequence) to `InviteeAccepted`, `InviteeDeclined`,
      and `InviteeTentative` using new field numbers
- [x] 1.3 Run `./services/gradlew bufFormatApply` and confirm the proto passes
      Buf `STANDARD` lint ← (verify: new fields use new field numbers, backward
      compatible, lint passes)

## 2. Meet domain

- [x] 2.1 Add `InviteeTentativeEvent` domain event mirroring
      `InviteeDeclinedEvent`, carrying the enriched meeting-context fields
- [x] 2.2 Enrich `InviteeAcceptedEvent` and `InviteeDeclinedEvent` records with
      the meeting-context fields
- [x] 2.3 Add `MeetingInvitee.tentative(...)` domain method mirroring
      `accept()`/`decline()`, enforcing the `TENTATIVE` transition rules and
      registering `InviteeTentativeEvent`
- [x] 2.4 Update `accept()`/`decline()` to accept and embed the meeting context
      into their enriched events ← (verify: transition rules match spec, removed
      invitee rejected, correct event registered per response)

## 3. Meet application layer

- [x] 3.1 Add `AcceptMeetingInviteeCommand`, `DeclineMeetingInviteeCommand`,
      `TentativeMeetingInviteeCommand` (meeting id, invitee id, acting account,
      tenant id) and their result records
- [x] 3.2 Add `RespondToMeetingInviteeUseCase` interfaces (accept/decline/
      tentative) in `application.usecase`
- [x] 3.3 Implement `*ApplicationService` for each: load meeting and invitee,
      verify acting account owns the invitee (else authorization failure),
      invoke the domain method, and `publishEventsOf(invitee)` ← (verify:
      ownership check rejects non-owner with 403, unknown meeting/invitee → 404)

## 4. Meet infrastructure

- [x] 4.1 Add `InviteeTentativeEventProtoMapper`; update accepted/declined
      mappers to populate the new meeting-context fields
- [x] 4.2 Register topic `meet.invitee.tentative` and CloudEvent type
      `io.github.smiskinext.meet.invitee.tentative.v1` in the outbox mapping ←
      (verify: unmapped event fails enqueue without partial write)

## 5. Meet presentation

- [x] 5.1 Add request/response DTOs for the invitee response endpoints (response
      DTO exposes the full invitee snapshot)
- [x] 5.2 Add `POST /meetings/{id}/invitees/{inviteeId}:accept`, `:decline`, and
      `:tentative` handlers in `MeetingController`, resolving the acting account
      from `AccountContext` and mapping `Result` via `ResultResponder` ←
      (verify: 200 snapshot on success, 400 missing header, 403 non-owner, 404
      unknown, 409 invalid transition per spec)

## 6. Notification build + config

- [x] 6.1 Add `biweekly` to the version catalog and to notification
      `build.gradle.kts`; add `resend-java` (catalog entry exists) to the build
- [x] 6.2 Add a validated `@ConfigurationProperties` type binding the Resend API
      key and sender address that fails startup when the key is blank ← (verify:
      missing key fails startup, configured key/sender used)
- [x] 6.3 Configure the email consumer retry topic and dead-letter topic, and
      wire the fixed consumer groups from `application.yaml`

## 7. Notification ICS + email

- [x] 7.1 Add an `EmailSender` domain port and an email model (recipient,
      subject, body, ICS attachment with method parameter)
- [x] 7.2 Implement `ResendEmailSender` infrastructure adapter over the Resend
      SDK sending a `text/calendar` attachment
- [x] 7.3 Implement an ICS generator (biweekly) producing `METHOD:REQUEST`
      (per-invitee attendees) and `METHOD:REPLY` (single responding attendee
      with mapped PARTSTAT) calendars using the event's UID/SEQUENCE ← (verify:
      REQUEST uses calendarUid/calendarSequence, REPLY PARTSTAT maps
      accepted→ACCEPTED, declined→DECLINED, tentative→TENTATIVE)

## 8. Notification consumers

- [x] 8.1 Implement `MeetingInvitationsCreatedEmailConsumer` on the fixed
      invitations group: decode the CloudEvent, build a REQUEST ICS, send one
      invite email per invitee ← (verify: one email per invitee, malformed event
      skipped without terminating consumer)
- [x] 8.2 Implement `InviteeRespondedEmailConsumer` on the fixed
      invitee-responded group for accepted/declined/tentative: decode, build a
      REPLY ICS, send the organizer email ← (verify: organizer receives REPLY
      with correct PARTSTAT, single-delivery per event across replicas, retries
      then routes to DLT on repeated failure)

## 9. Tests (meet)

- [x] 9.1 Domain unit tests: `tentative()` transitions and rejection of
      declined/removed invitations; enriched events carry meeting context
      (covers invitee-rsvp-response transition scenarios + event enrichment)
- [x] 9.2 Application unit tests: ownership authorization (owner allowed,
      non-owner rejected), unknown meeting/invitee handling for each response
      use case (covers response ownership scenarios)
- [x] 9.3 Presentation integration tests: `:accept`/`:decline`/`:tentative`
      returning 200 snapshot, 400 missing header, 403 non-owner, 404 unknown,
      409 invalid transition (covers invitee self-response endpoint scenarios)
- [x] 9.4 Regenerate meet `openapi.yaml` via `generateOpenApiDocsFromTests`

## 10. Tests (notification)

- [x] 10.1 ICS generator unit tests: REQUEST UID/SEQUENCE + per-invitee
      attendees; REPLY PARTSTAT mapping for accepted/declined/tentative (covers
      calendar reply mapping scenarios)
- [x] 10.2 Consumer integration tests: invitation event → invite email per
      invitee; response events → organizer REPLY email; malformed event skipped;
      retries then DLT on send failure; single delivery under one fixed group
      (covers calendar email + retry/DLT + single-delivery scenarios)
- [x] 10.3 Configuration test: startup fails when the Resend API key is blank
      (covers fail-fast configuration scenario)

## 11. Verification

- [x] 11.1 Run `./services/gradlew spotlessApply` and `bufFormatApply`
- [x] 11.2 Run `./services/gradlew -p services/ meet test integrationTest`
- [x] 11.3 Run
      `./services/gradlew -p services/ notification test integrationTest` ←
      (verify: all spec scenarios covered by passing tests, no ArchUnit
      violations)
