## [2026-04-30] Round 1 (from apply auto-verify)

### Verifier

- Fixed: Removed unused `InviteListPhase` type alias from
  `use-invite-management.ts` that caused a biome lint error.
- Fixed: Moved `resolveErrorMessage` function outside the `useInviteManagement`
  hook to module scope, resolving biome `useExhaustiveDependencies` lint errors
  that would have required adding it to three `useCallback` dependency arrays.
- Fixed: Removed `meetingId` prop from `InviteManagementSectionProps` type since
  the purely presentational component does not use it directly; the hook handles
  the meetingId internally.
- Fixed: Biome auto-format applied to `invite-management-section.tsx` (function
  signature line breaking and JSX text node wrapping).
- Fixed: `handleAddSubmit` now correctly passes a boolean return from
  `onAddInvitee` to determine whether to call `reset()`, instead of reading
  stale `addState.error` from a closure.
- Fixed: Token validation `useEffect` and auto-submit `useEffect` moved to
  correct positions in `useJoinMeeting` to ensure `lookupAndJoin` is defined
  before the auto-submit effect references it.
