## Context

The `meet` service already contains a fully-modeled domain `cancel()` method on
the `Meeting` aggregate, a `MeetingStatus.CANCELED` value, the `CancelReason`
enum (`HOST_CANCELED`, `NO_SHOW`), and a `MeetingCanceledEvent` with an
`InviteeInfo` sub-record. The baseline database schema already includes the
`cancel_reason` column with the correct `CHECK` constraint, and the persistence
mapper already reads and writes it.

What is missing is the end-to-end wiring through the application layer (use
case, service, command, result, mapper), the presentation layer (controller
endpoint, DTOs), the infrastructure layer (proto message + mapper, scheduled
no-show job), and tests at every layer.

A critical constraint: `OutboxEventProtoMapperRegistry.resolve()` throws
`IllegalStateException` when no mapper is registered for an event type.
Publishing `MeetingCanceledEvent` without adding both the protobuf message
definition and the `@Component` mapper would cause a runtime failure on the
first canceled meeting.

## Goals / Non-Goals

**Goals:**

- Expose `POST /meetings/{id}:cancel` for host-only manual cancellation of
  SCHEDULED meetings
- Add a background `@Scheduled` job to auto-cancel SCHEDULED meetings past their
  `end_time` with reason `NO_SHOW`
- Add `meeting_canceled.proto` and `MeetingCanceledEventProtoMapper` so the
  outbox publisher can serialize the event
- Load active invitees at cancellation time and include them in the event
  payload
- Add unit tests (domain, application), job tests, and controller integration
  tests

**Non-Goals:**

- Canceling RUNNING meetings (requires LiveKit room tear-down — deferred)
- Consuming `meet.meeting.canceled` in the notification service (no email sent
  yet)
- UI changes in the Forge app
- Modifying the domain `Meeting.cancel()` method (it is already correct)

## Decisions

### Decision 1: Authorization enforced at the application service layer, not the domain

`Meeting.cancel()` deliberately omits the host check so the no-show job can call
it without an acting account. Authorization is checked in
`CancelMeetingApplicationService` before calling `meeting.cancel()`, matching
the `RemoveMeetingInviteesApplicationService` precedent.

_Alternative considered_: Add `canceledBy` parameter to the domain method and
validate there (like `delete()` does). Rejected because the no-show job has no
acting account and would need a separate code path.

### Decision 2: Body-less endpoint, always HOST_CANCELED

`POST /meetings/{id}:cancel` requires no request body. The reason is always
`HOST_CANCELED` for this endpoint. `NO_SHOW` is exclusively produced by the
background job.

_Alternative considered_: Allow caller to specify the reason. Rejected because
clients should not be able to assert `NO_SHOW`, which is a system-generated
signal.

### Decision 3: Invitees loaded in the application service, not the domain

The application service loads active invitees via
`MeetingInviteeRepository.findByMeetingId()` (filtering for non-removed ones)
and maps them to `MeetingCanceledEvent.InviteeInfo` before calling the domain's
`cancel(reason, title, shortCode, startTime, invitees)` overload. This keeps the
domain free of repository dependencies.

### Decision 4: Proto definition follows meeting_invitations_deleted.proto template

`meeting_canceled.proto` reuses the flat-field structure with a nested
`InviteeInfo` message, consistent with `meeting_invitations_deleted.proto`. It
does not embed `MeetingSnapshot` (which carries fields irrelevant to
cancellation notifications).

### Decision 5: No-show job uses native SQL cross-tenant query + TenantContext per meeting

The job issues a `nativeQuery` to scan all SCHEDULED meetings with
`end_time < now AND deleted_at IS NULL` across all tenants (bypassing
Hibernate's `@TenantId` filter). For each matching row it sets
`TenantContext.setCurrentTenant(tenantId)`, loads the meeting with a pessimistic
write lock, calls `cancel(NO_SHOW, …)`, saves, publishes, and clears the
context. This mirrors the outbox relay's `findByIdAcrossTenants` pattern. The
per-meeting transaction ensures a failure for one tenant does not roll back
others.

_Alternative considered_: Use `TenantContext` bypass with a Spring Hibernate
filter disable. Rejected because the native query approach is already
established in the codebase (`OutboxEventJpaRepository`), requires no new
infrastructure, and is explicit.

### Decision 6: Job interval configurable via application.yaml

The no-show job uses `@Scheduled(fixedDelayString)` backed by a property
`smiski.meet.no-show-canceler.fixed-delay` defaulting to `PT5M`. This matches
the outbox relay pattern (`smiski.outbox.relay.fixed-delay`).

### Decision 7: No ShedLock — pessimistic lock prevents double-cancellation

With a pessimistic write lock (`SELECT FOR UPDATE`) on each meeting, concurrent
job executions on multiple instances will block on the same row. The second
execution will see status already `CANCELED` and the domain `canTransitionTo()`
check will return failure, causing it to be skipped. No external lock
coordinator is needed.

## Risks / Trade-offs

- **[Risk] Large number of no-show meetings at first run** → The job processes
  them in a single pass. Mitigation: add a configurable batch size limit
  (`smiski.meet.no-show-canceler.batch-size`, default 100) to the native query
  (`LIMIT :batchSize`).

- **[Risk] Tenant context leak between meetings in the job** →
  `TenantContext.clear()` is called in a `finally` block after each meeting. If
  an exception escapes, the context is still cleared before the next meeting.

- **[Risk] Proto schema evolution** → `meeting_canceled.proto` uses field
  numbers 1–9 (stable). Future additions append new field numbers. Never reuse
  or remove existing numbers.

## Migration Plan

1. Merge proto changes (`meeting_canceled.proto`) first so the compiled Java
   class is available before the service code referencing it.
2. No database migration required — `cancel_reason` column and `CANCELED` status
   already exist.
3. Deploy the service; the no-show job starts automatically on the configured
   fixed delay.
4. Rollback: remove the endpoint and job; the
   `CANCELED`/`HOST_CANCELED`/`NO_SHOW` values in the database are harmless if
   the application does not reference them.

## Open Questions

- None — all decisions made during exploration.
