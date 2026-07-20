## 1. Domain Model And Events

- [x] 1.1 Add aggregate update behavior that compares incoming values, enforces
      host authorization, and applies the status matrix for all mutable fields.
- [x] 1.2 Make the required meeting value objects and aggregate state support
      safe updates while preserving validation invariants.
- [x] 1.3 Add publishable `meeting.info.update` and `meeting.settings.update`
      events with old/new snapshots and the requested event metadata. ← (verify:
      event names, payload snapshots, and no-op suppression match the capability
      spec)

## 2. Application And Persistence

- [x] 2.1 Add update command, use case, result, mapper, and transactional
      application service using the locked meeting repository lookup.
- [x] 2.2 Map missing meetings, non-host requests, terminal statuses, and
      invalid values through existing `MeetingError` and Problem Details
      conventions.
- [x] 2.3 Extend persistence mapping only where needed to round-trip updated
      meeting information, time range, time zone, and settings.

## 3. HTTP API And Contracts

- [x] 3.1 Add `PUT /meetings/{id}` request validation and account/tenant context
      handling.
- [x] 3.2 Return the full updated meeting snapshot with `200 OK` and update the
      generated OpenAPI contract.
- [x] 3.3 Document the status-aware mutability and error responses in the
      controller/API annotations.

## 4. Tests

- [x] 4.1 Add domain tests for host authorization and successful
      scheduled/running field updates.
- [x] 4.2 Add domain tests for rejecting zone/time-range changes outside
      `SCHEDULED`, and rejecting all changes in `COMPLETED`/`CANCELED`.
- [x] 4.3 Add domain/application tests for no-op detection, one event per
      changed group, both events when both groups change, and no events on
      failure. ← (verify: all event scenarios are covered and event count/type
      assertions are exact)
- [x] 4.4 Add integration tests for successful host updates, non-host rejection,
      missing meeting, missing account, validation failures, and full snapshot
      response. ← (verify: HTTP status, Problem Details codes, persistence, and
      response fields match the API convention)
- [x] 4.5 Run meet service unit tests, integration tests, formatting, and
      OpenAPI generation/lint checks. ← (verify: generated contract includes the
      update endpoint and all checks pass)
