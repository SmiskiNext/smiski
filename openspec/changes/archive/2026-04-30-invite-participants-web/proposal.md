# Why

The web app lacks invite participant management, leaving hosts with no way to
invite attendees, track invite status, or revoke/resend invitations from the
browser. Backend invite APIs and Android parity already exist; the web surface
is the only missing piece.

## What Changes

- Regenerate the web SDK from `openapi/unified-openapi.yaml` to expose the five
  invite endpoints (`getInvitees`, `addInvitee`, `resendInvite`, `revokeInvite`,
  `validateToken`) that are already declared in the spec but absent from
  `src/generated/sdk.gen.ts`
- Add an Invitees section to `MeetingDetailDialog` with an invitee list (status
  badges, per-row resend/revoke actions) and a single-email Add Invitee form
  (capped at 10)
- Introduce a `useInviteManagement` hook that owns all invite CRUD state and
  error handling
- Introduce an `InviteManagementSection` component that renders the Invitees
  section
- Extend the token-based join flow: `JoinMeetingContainer` accepts an optional
  `inviteToken` prop; `useJoinMeeting` validates the token on mount, auto-fills
  the meeting code, and optionally bypasses the waiting room when `preApproved`
  is true
- Pass the `?token=` search param from the guest join page into
  `JoinMeetingContainer`
- Add i18n keys for all new UI strings to both `en.json` and `vi.json`

## Capabilities

### New Capabilities

- `web-invite-management`: Host-facing UI inside `MeetingDetailDialog` for
  listing invitees, adding new ones by email, resending, and revoking individual
  invitations
- `web-invite-token-join`: Guest-facing token validation flow that auto-fills
  and optionally auto-submits the join form when an invite link is opened

### Modified Capabilities

- `web-join-meeting`: The join flow gains an optional invite-token validation
  step that prefills the meeting code and conditionally bypasses the waiting
  room when `preApproved` is true

## Impact

- `frontends/web/src/generated/` — regenerated SDK (do not edit manually)
- `src/components/upcoming-meetings/meeting-detail-dialog.tsx` — new Invitees
  section
- `src/components/upcoming-meetings/invite-management-section.tsx` — new file
- `src/components/upcoming-meetings/use-invite-management.ts` — new file
- `src/components/join-meeting/index.tsx` — new `inviteToken` prop
- `src/components/join-meeting/use-join-meeting.ts` — token validation logic
- `src/app/[locale]/join/[code]/page.tsx` — thread `token` search param through
- `src/messages/en.json` and `src/messages/vi.json` — new i18n keys
- No backend changes; no API contract changes; no breaking changes
