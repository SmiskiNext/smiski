## Why

The meet service exposes create, list, update, and delete operations, but there
is no way to fetch a single meeting by id together with the people involved. The
Jira issue panel needs one request to render a meeting's details, who was
invited (with their RSVP status), and who actually joined the call.

## What Changes

- Add `GET /api/1/meetings/{id}` returning a single meeting snapshot plus its
  invitee list and its joined-participant list in one payload.
- The invitee list reuses the existing active-invitee read model
  (`MeetingInviteeRepository.findSummariesByMeetingId`).
- The participant list is derived from participation logs, collapsed to one row
  per account (a rejoin does not duplicate the person) and includes people who
  have already left (each row carries `joinedAt` and `leftAt`).
- Implement the currently-stubbed
  `ParticipationLogRepository.findDistinctParticipantSummariesByMeetingId` (and
  the JPA query it needs); today it throws `UnsupportedOperationException`.
- Any authenticated account in the meeting's tenant may read the meeting; access
  is not restricted to the host. Tenant isolation is enforced. Unknown, soft
  deleted, or other-tenant meetings return `404 MEETING_NOT_FOUND`.

## Capabilities

### New Capabilities

- `get-meeting-detail`: Retrieve a single tenant-scoped meeting by id, including
  its invitees (with RSVP status) and its distinct joined participants
  (including those who have left).

### Modified Capabilities

<!-- No existing spec-level requirements change. -->

## Impact

- **API**: New endpoint `GET /api/1/meetings/{id}`; regenerated
  `services/meet/openapi.yaml`.
- **Presentation**: New `GetMeetingResponse` DTO; new handler on
  `MeetingController`.
- **Application**: New `GetMeetingUseCase`, `GetMeetingApplicationService`,
  `GetMeetingQuery`, `GetMeetingResult`, and result mapper.
- **Domain/Infrastructure**: Implement
  `ParticipationLogRepositoryAdapter.findDistinctParticipantSummariesByMeetingId`
  and add the backing query to `ParticipationLogJpaRepository`. Reuses existing
  `MeetingRepository.findById` and
  `MeetingInviteeRepository.findSummariesByMeetingId`.
- **Tests**: Application use-case tests, controller/problem+json integration
  tests, participation-log adapter integration test, OpenAPI generation.
