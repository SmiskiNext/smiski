## Why

The current `PUT /api/1/meetings/{id}/invitees` endpoint replaces the entire
invitee list in one request, forcing the client to always resend the full set
and coupling three distinct intents (add, rename, remove) behind a single
diff-based call. This makes the API hard to reason about, produces a
display-name "update" path that no consumer actually uses, and prevents a client
from adding or removing a single invitee without recomputing the whole list.
Splitting the operation into explicit add and remove endpoints makes each intent
first-class, removes dead update machinery, and aligns with the service's
RESTful action conventions.

## What Changes

- **BREAKING** Remove `PUT /api/1/meetings/{id}/invitees` (full-list replacement
  with accountId diffing).
- Add `POST /api/1/meetings/{id}/invitees` to create one or more new invitees.
  The request carries `{ invitees: [ { email, accountId, displayName } ] }` with
  at least one entry; an empty list is rejected with `400 VALIDATION_ERROR`.
- Add `POST /api/1/meetings/{id}/invitees:batchDelete` to remove one or more
  invitees by invitee id. The request carries `{ inviteeIds: [ uuid ] }` with at
  least one entry; an empty list is rejected with `400 VALIDATION_ERROR`.
- **BREAKING** Drop the display-name update behavior entirely: remove the
  `meet.meeting.invitations.updated` event, its proto message, its proto mapper,
  the `MeetingInvitee.updateDisplayName` domain operation, and the
  `Meeting.recordInviteesUpdated` aggregate method.
- Both new endpoints are host-only, permitted only while the meeting is
  `SCHEDULED`, atomic (all-or-nothing), and return `200 OK` with the full
  snapshot of only the invitees affected by that call.
- Add invitee identity `id` to the meeting-detail response so a client can
  obtain the invitee id needed for `:batchDelete`.
- Adding an invitee whose `accountId` is already an active invitee of the
  meeting is rejected atomically with `409 CONFLICT` (new
  `INVITEE_ALREADY_EXISTS` code). Removing an invitee id that does not exist or
  was already removed is rejected atomically with `404 INVITEE_NOT_FOUND`.

## Capabilities

### New Capabilities

- `add-meeting-invitees`: Host-only creation of new meeting invitees on a
  scheduled meeting, with atomic duplicate-account rejection and a
  `meet.meeting.invitations.created` event.
- `remove-meeting-invitees`: Host-only atomic removal of meeting invitees by
  invitee id on a scheduled meeting, with a `meet.meeting.invitations.deleted`
  event.

### Modified Capabilities

- `update-meeting-invitees`: Replace the single replace-all `PUT` requirement
  set with the split add/remove endpoint contract; remove the display-name
  update, the accountId-diffing requirement, and the
  `meet.meeting.invitations.updated` event.
- `get-meeting-detail`: Each invitee entry in the meeting-detail response gains
  an invitee `id` field.
- `api-convention`: Record that a batch sub-resource creation may return
  `200 OK` (instead of `201 Created` with `Location`) when the operation targets
  a collection with no single created-resource URI, mirroring the existing
  `:batchDelete` action shape.

## Impact

- **meet service (backend)** — presentation controller and request/response
  DTOs, application use cases/services/commands/results, domain model
  (`MeetingInvitee`, `Meeting`), domain error/error-code + i18n bundle, domain
  events, messaging proto mappers.
- **proto module** — delete `meeting_invitations_updated.proto`.
- **meet OpenAPI spec** — regenerated `services/meet/openapi.yaml`.
- **meet tests** — unit, application, and integration tests for invitees;
  meeting-detail tests gain the `id` assertion.
- **Out of scope** — the Forge app frontend (`app/static/smiski-ui`) still
  references the removed `PUT` endpoint in `endpoints.ts` and docs; it is not
  called at runtime today and is intentionally left for a separate frontend
  change.
