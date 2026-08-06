## Why

Under `MANUAL_APPROVAL` admission every caller is queued for host approval,
including people the host already invited and who already replied `ACCEPTED` or
`TENTATIVE`, and including the host themselves. Asking an already-accepted
invitee to request permission again is redundant friction, and because the Forge
app starts a scheduled meeting by calling the same `:join` endpoint
(`useStartMeeting` in `app/static/smiski-ui/src/hooks/useMeetingMutations.ts`),
the host is currently forced to approve their own pending request before they
can enter their own meeting.

## What Changes

- The join operation SHALL admit a caller immediately on a `MANUAL_APPROVAL`
  meeting when the caller is the meeting host, or is an active (non-removed)
  invitee of that meeting whose RSVP status is `ACCEPTED` or `TENTATIVE`.
- Callers who are not invited, whose invitation is still `NEEDS_ACTION`, whose
  invitation is `DECLINED`, or whose invitation was removed continue to create a
  `PENDING` join request exactly as today.
- When a bypassing caller already has a pending join request for the same
  device, that request is reconciled to `APPROVED` — its terminal outcome is
  persisted, it is removed from the meeting's pending queue, and a
  `JoinRequestApprovedEvent` is published — so the host's pending queue does not
  retain a stale entry and the caller's already-open SSE stream receives the
  token instead of hanging.
- Capacity enforcement is unchanged: a bypassing caller joining a meeting
  already at `maxParticipants` is rejected with `MEETING_FULL` and receives no
  token, matching today's `ALLOW_ALL` behavior.
- `ALLOW_ALL` meetings are unaffected and perform no additional invitee lookup.
- No HTTP contract change: the endpoint, request body, and response shape are
  identical, so `openapi.yaml` and the Forge app require no modification. A
  bypassing caller simply receives the existing `APPROVED` outcome instead of
  `PENDING`.

## Capabilities

### New Capabilities

None. This change modifies the behavior of an existing capability only.

### Modified Capabilities

- `join-meeting`: The "Pending join request under MANUAL_APPROVAL admission"
  requirement currently mandates that every caller on a `MANUAL_APPROVAL`
  meeting receives `PENDING`. It is narrowed to exclude the host and
  already-responding invitees, and a new requirement defines the
  immediate-admission bypass together with reconciliation of a pre-existing
  pending request for the same device.

## Impact

Affected code (all within `services/meet`):

- `application/service/RequestJoinApplicationService` — the `MANUAL_APPROVAL`
  branch gains a bypass decision before creating a pending request; gains
  `MeetingInviteeRepository` and `JoinRequestResultStore` dependencies.
- `application/helper/` — new helper deciding whether a caller bypasses
  approval, placed alongside the existing `InviteeResponseSupport`.
- `src/test/.../application/RequestJoinApplicationServiceTest` — constructor
  update plus bypass, non-bypass, capacity, and reconciliation cases.
- `src/integrationTest/.../presentation/JoinMeetingControllerIntegrationTest` —
  end-to-end cases with real `meeting_invitees` rows.

Unaffected, verified during exploration:

- HTTP surface: `MeetingController` `:join` handler, request/response DTOs, and
  `services/meet/openapi.yaml` are untouched.
- Forge app: `api/meetings.ts` `joinMeeting` already handles both `APPROVED` and
  `PENDING`; bypassing callers take the existing `APPROVED` path.
- `notification`: `JoinResolvedEventConsumer` reads only `joinRequestId`,
  `liveKitToken`, and `roomName` from `JoinApproved`, so the reconciliation
  event relays correctly and the `approvedBy` value carries no notification
  semantics.
- Host RSVP/decision flows: `AcceptMeetingInviteeApplicationService`,
  `ApplyEmailInviteeResponseApplicationService`,
  `AcceptJoinRequestsApplicationService`, and
  `DeclineJoinRequestsApplicationService` are unchanged.
- No database migration and no `AdmissionPolicy` enum change.

Explicitly out of scope:

- A pending request created from a _different_ device is not reconciled, because
  `JoinRequestRepository` indexes pending requests by device only
  (`findByDeviceId`) with no account-based lookup. This preserves today's
  behavior rather than regressing it.
- Issuing a `HOST`-role LiveKit token to the host of a scheduled meeting. The
  join path currently hardcodes `ParticipantRole.PARTICIPANT`; correcting that
  affects `ParticipantGrants` and the LiveKit adapter and is tracked separately.
