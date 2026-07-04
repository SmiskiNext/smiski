# Tasks

## 1. SDK Regeneration

- [x] 1.1 Run `pnpm run openapi:unified` from the monorepo root to ensure the
      merged spec is current
- [x] 1.2 Run `pnpm --dir frontends/web run generate:sdk` to regenerate
      `src/generated/sdk.gen.ts`
- [x] 1.3 Verify that `getInvitees`, `addInvitee`, `resendInvite`,
      `revokeInvite`, and `validateToken` are present in the generated SDK ←
      (verify: all five functions exist in sdk.gen.ts with correct path and body
      parameter shapes)

## 2. Invite Management Hook

- [x] 2.1 Create `src/components/upcoming-meetings/use-invite-management.ts`
      implementing `useInviteManagement(meetingId)`
- [x] 2.2 Add fetch-on-mount via `getInvitees` with loading and error state
- [x] 2.3 Implement `handleAddInvitee(email)`: calls `addInvitee`, refreshes
      list on success, sets per-action error on failure
- [x] 2.4 Implement `handleResend(inviteeId)`: calls `resendInvite`, refreshes
      list on success, sets per-row error on failure
- [x] 2.5 Implement `handleRevoke(inviteeId)`: calls `revokeInvite`, refreshes
      list on success, sets per-row error on failure
- [x] 2.6 Apply `ApiFailError` / `ApiError` / generic error pattern for all
      action error handlers ← (verify: hook returns correct loading/error shape
      per action; all three error types are handled)

## 3. Invite Management Section Component

- [x] 3.1 Create
      `src/components/upcoming-meetings/invite-management-section.tsx` as a
      purely presentational component
- [x] 3.2 Render loading skeleton while `getInvitees` is in flight
- [x] 3.3 Render localized empty state when invitee list is empty
- [x] 3.4 Render localized error state when `getInvitees` fails
- [x] 3.5 Render invitee rows with email-or-name display, invite-status badge
      (PENDING yellow / ACCEPTED green / DECLINED red), and token-status badge
- [x] 3.6 Show Resend button on rows where `tokenStatus` is PENDING, EXPIRED, or
      REVOKED; disable during resend action
- [x] 3.7 Show Revoke button on rows where invite `status` is PENDING and
      `tokenStatus` is PENDING; disable during revoke action
- [x] 3.8 Show per-row error message below the affected row on action failure
- [x] 3.9 Render add-invitee email input with zod-validated react-hook-form and
      Add button; hide both when invitee count is 10 or more
- [x] 3.10 Show inline validation error for malformed email without calling the
      API
- [x] 3.11 Show server error near the add form on `addInvitee` failure; preserve
      email input value ← (verify: all badge variants render correctly;
      Resend/Revoke visibility rules match spec scenarios; cap hides the form at
      exactly 10 invitees)

## 4. MeetingDetailDialog Integration

- [x] 4.1 Import and render `InviteManagementSection` below the Settings section
      in `src/components/upcoming-meetings/meeting-detail-dialog.tsx`
- [x] 4.2 Pass `meetingId` from the dialog's meeting prop to
      `InviteManagementSection` ← (verify: Invitees section appears in the
      dialog for SCHEDULED meetings; data loads on open)

## 5. i18n Keys

- [x] 5.1 Add invite management keys to `src/messages/en.json`: section title,
      empty state, loading state, error state, status labels
      (pending/accepted/declined), token status labels
      (pending/used/revoked/expired), action labels (resend/revoke/addInvitee),
      add-form placeholder and button, cap-reached message, per-action error
      messages
- [x] 5.2 Add the same keys to `src/messages/vi.json` with Vietnamese
      translations
- [x] 5.3 Add invite-token join keys to `src/messages/en.json`: validating
      token, invalid token, expired token, revoked token, used token, generic
      token error
- [x] 5.4 Add the same token join keys to `src/messages/vi.json` ← (verify: no
      missing translation keys at runtime; both locales render without fallback
      placeholders)

## 6. Token Validation in Join Flow

- [x] 6.1 Update `src/app/[locale]/join/[code]/page.tsx` to read the `token`
      search param and pass it as `inviteToken` to `JoinMeetingContainer`
- [x] 6.2 Add `inviteToken?: string` prop to `JoinMeetingContainer` in
      `src/components/join-meeting/index.tsx` and thread it to `useJoinMeeting`
- [x] 6.3 In `src/components/join-meeting/use-join-meeting.ts`, add token
      validation state: `isValidatingToken`, `tokenError`
- [x] 6.4 On mount (when `inviteToken` is provided), call `validateToken`; on
      success extract and set `shortCode`; on failure set `tokenError`
- [x] 6.5 Disable meeting code field and submit button while `isValidatingToken`
      is true
- [x] 6.6 When `preApproved: true` and `requirePassword: false`, auto-submit
      `requestJoin` after code resolution
- [x] 6.7 When `preApproved: true` and `requirePassword: true`, prefill code and
      show password field without auto-submitting
- [x] 6.8 When `preApproved: false`, prefill code and proceed with normal flow
- [x] 6.9 Clear `tokenError` when the user manually edits the meeting code field
      after a token failure ← (verify: auto-submit fires correctly for
      preApproved+no-password case; password gate is not bypassed when
      requirePassword is true; manual edit clears token state; invalid token
      shows correct localized error)
