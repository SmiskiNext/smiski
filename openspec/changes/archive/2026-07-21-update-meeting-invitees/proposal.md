## Why

A meeting host can attach an invitee list only at scheduling time; there is no
way to change who is invited afterwards. Hosts need to add, correct, or remove
invitees on an already-scheduled meeting, and downstream services need
fine-grained events describing exactly which invitees were created, updated, or
removed so they can notify the right people.

## What Changes

- Add `PUT /api/1/meetings/{id}/invitees` that lets the meeting host replace the
  full invitee list of a `SCHEDULED` meeting in a single call. Request and
  response bodies both use the shape `{ "invitees": [ { ... } ] }`.
- Diff the submitted list against the current active invitees by `accountId`:
    - invitees present in the request but not in the DB are **created**
    - invitees present in both whose `displayName` changed are **updated**
    - invitees present in the DB but absent from the request are **removed**
      (soft delete via `removedAt`)
- Publish three batch domain events, each carrying only the affected invitees
  and only when its group is non-empty:
    - reuse `MeetingInvitationsCreatedEvent` (topic
      `meet.meeting.invitations.created`) for the created group
    - add `MeetingInvitationsUpdatedEvent` (topic
      `meet.meeting.invitations.updated`)
    - add `MeetingInvitationsDeletedEvent` (topic
      `meet.meeting.invitations.deleted`)
- Reject the operation when the actor is not the host, when the meeting is not
  `SCHEDULED`, when the account header is missing, or when the meeting does not
  exist. Reject a request containing duplicate `accountId` values.
- An empty `invitees` array removes all current invitees. A request that
  produces no effective change publishes no event and returns the current list.

Out of scope: notification consumers/email delivery (not yet built), changing
`email`, `role`, or `rsvp` of an existing invitee, and editing invitees while a
meeting is `RUNNING`.

## Capabilities

### New Capabilities

- `update-meeting-invitees`: host-only replacement of a scheduled meeting's
  invitee list, with accountId-based diffing and change-sensitive batch events
  for created, updated, and removed invitees.

### Modified Capabilities

<!-- None: existing update-meeting capability governs meeting fields/settings only;
     invitee management is a distinct new capability. -->

## Impact

- **API**: new endpoint `PUT /api/1/meetings/{id}/invitees`; regenerated
  `services/meet/openapi.yaml`.
- **Meet service domain**: `MeetingInvitee` gains a mutable `displayName` with
  an `updateDisplayName` behavior; `Meeting` gains `recordInviteesUpdated` and
  `recordInviteesRemoved`; two new domain events under `domain/event/`.
- **Meet service application**: new `UpdateMeetingInviteesUseCase`,
  `UpdateMeetingInviteesApplicationService`, command, and result.
- **Meet service presentation**: new request/response DTOs and a controller
  method on `MeetingController`.
- **Proto contract**: two new messages `MeetingInvitationsUpdated` and
  `MeetingInvitationsDeleted` in `event/meet/v1`, plus two new outbox proto
  mappers in `infrastructure/messaging/`.
- **Events**: two new Kafka topics `meet.meeting.invitations.updated` and
  `meet.meeting.invitations.deleted`; the created group reuses the existing
  topic. No consumers change (none exist yet).
- **Persistence**: no schema change; existing `meeting_invitees` table and its
  soft-delete (`removed_at`) semantics are reused.
