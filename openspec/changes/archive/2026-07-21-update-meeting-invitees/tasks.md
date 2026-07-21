## 1. Proto contract

- [x] 1.1 Add `MeetingInvitationsUpdated` message (with nested `InviteeInfo`) in
      `services/proto/src/main/proto/io/github/smiskinext/event/meet/v1/meeting_invitations_updated.proto`
      mirroring the created message fields
- [x] 1.2 Add `MeetingInvitationsDeleted` message (with nested `InviteeInfo`) in
      `services/proto/src/main/proto/io/github/smiskinext/event/meet/v1/meeting_invitations_deleted.proto`
- [x] 1.3 Run `./services/gradlew bufFormatApply` and confirm both protos pass
      Buf `STANDARD` lint ← (verify: messages compile and pass buf lint; field
      names/numbers backward-compatible)

## 2. Domain layer

- [x] 2.1 Make `MeetingInvitee.displayName` mutable and add
      `updateDisplayName(InviteeDisplayName)` that no-ops when unchanged and is
      rejected when the invitee is already removed
- [x] 2.2 Add `MeetingInvitationsUpdatedEvent` (topic
      `meet.meeting.invitations.updated`, aggregate type `meeting`) with an
      `InviteeInfo` record, following `MeetingInvitationsCreatedEvent`
- [x] 2.3 Add `MeetingInvitationsDeletedEvent` (topic
      `meet.meeting.invitations.deleted`, aggregate type `meeting`) with an
      `InviteeInfo` record
- [x] 2.4 Add `Meeting.recordInviteesUpdated(List<...>)` and
      `Meeting.recordInviteesRemoved(List<...>)` that register the new events
      only when the list is non-empty and do not bump `calendarSequence` ←
      (verify: events registered only for non-empty groups; no calendarSequence
      change)

## 3. Application layer

- [x] 3.1 Add `UpdateMeetingInviteesCommand` (meetingId, accountId, tenantId,
      list of {email, accountId, displayName})
- [x] 3.2 Add `UpdateMeetingInviteesResult` (list of invitee snapshots: id,
      accountId, email, displayName, role, status, invitedAt, respondedAt)
- [x] 3.3 Add `UpdateMeetingInviteesUseCase` interface extending the shared
      `UseCase`
- [x] 3.4 Implement `UpdateMeetingInviteesApplicationService`
      (`@Transactional`): load meeting with lock → 404 if absent → host +
      `SCHEDULED` checks → load active invitees → reject duplicate accountId →
      diff by accountId → create/updateDisplayName/remove → persist invitees →
      register non-empty batch events on meeting → `save(meeting)` +
      `publishEventsOf(meeting)` only when events exist → build result ←
      (verify: diff correctness, event grouping, atomicity, no-op path publishes
      nothing)

## 4. Presentation layer

- [x] 4.1 Add `UpdateMeetingInviteesRequest` record with `@Valid` invitee list
      (`@NotBlank @Email email`, `@NotBlank accountId`, `@NotBlank displayName`)
      and `toCommand(id, accountId, tenantId)`
- [x] 4.2 Add `UpdateMeetingInviteesResponse` record with static
      `from(UpdateMeetingInviteesResult)` producing `{ invitees: [...] }`
- [x] 4.3 Add `PUT /meetings/{id}/invitees` handler to `MeetingController`
      (resolve account header → 400 if missing, resolve tenant, map `Result` via
      `ResultResponder.ok`) with OpenAPI annotations for 200/400/403/404 ←
      (verify: route is `/api/1/meetings/{id}/invitees`, host-only, problem+json
      on errors)

## 5. Infrastructure (messaging)

- [x] 5.1 Add `MeetingInvitationsUpdatedEventProtoMapper` implementing
      `OutboxEventProtoMapper` for the updated event
- [x] 5.2 Add `MeetingInvitationsDeletedEventProtoMapper` implementing
      `OutboxEventProtoMapper` for the deleted event ← (verify: both mappers
      auto-register; unmapped-event failure path not triggered)

## 6. Tests

- [x] 6.1 Domain unit test: `MeetingInvitee.updateDisplayName` changes name and
      rejects when removed
- [x] 6.2 Domain unit test: `recordInviteesUpdated`/`recordInviteesRemoved`
      register events only for non-empty groups and leave `calendarSequence`
      unchanged
- [x] 6.3 Application test: new invitee created as `NEEDS_ACTION` (scenario: New
      invitee is created)
- [x] 6.4 Application test: existing invitee displayName updated,
      email/role/rsvp/status preserved (scenario: Existing invitee display name
      is updated)
- [x] 6.5 Application test: absent invitee soft-deleted (scenario: Absent
      invitee is removed)
- [x] 6.6 Application test: unchanged invitee left intact and no-op publishes no
      event (scenarios: Unchanged invitee is left intact, No-op synchronization
      publishes no event)
- [x] 6.7 Application test: re-adding a removed account creates a fresh
      `NEEDS_ACTION` invitee (scenario: Re-adding a previously removed account
      creates a fresh invitee)
- [x] 6.8 Application test: mixed change enqueues one created, one updated, one
      deleted event, each with only its invitees (scenario: Mixed change
      publishes each non-empty group once)
- [x] 6.9 Application test: empty list removes all invitees and returns empty
      list (scenario: Empty invitee list removes all invitees)
- [x] 6.10 Application test: non-host, non-`SCHEDULED` status, and duplicate
      accountId are rejected with no change and no event (scenarios: Non-host
      modification is rejected, Running/Completed/Canceled rejected, Duplicate
      accountId is rejected)
- [x] 6.11 Integration/controller test: host success `200` with invitee list,
      missing account header `400`, unknown meeting `404`, invalid invitee field
      `400` VALIDATION_ERROR (scenarios: Host replaces the invitee list, Missing
      account identity is rejected, Unknown meeting is rejected, Invalid invitee
      field is rejected) ← (verify: problem+json shape and status codes match
      api-convention)

## 7. Verification

- [x] 7.1 Run
      `./services/gradlew -p services/ meet generateOpenApiDocsFromTests` and
      confirm `services/meet/openapi.yaml` includes the new endpoint and schemas
- [x] 7.2 Run `./services/gradlew spotlessApply` and
      `./services/gradlew -p services/ meet build` (test + integrationTest)
      green ← (verify: full build passes, ArchUnit layering respected)
- [x] 7.3 Run `pnpm run openapi` (root) to regenerate + lint the meet spec
