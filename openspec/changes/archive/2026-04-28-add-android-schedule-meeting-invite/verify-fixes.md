## [2026-04-28] Round 2 (from verify warnings)

### Verifier

- Fixed: WARNING 1 — `tvInviteeCount` now always shows `visible` with "0 / 10
  invitees" text even when empty list. In `rebuildInviteeChips()`, moved the
  visibility/text update before the empty-list early-return so the counter is
  always set first.
- Fixed: WARNING 2 — Removed all inline `//` comments from invitee-related and
  newly-added sections across 3 files: `ScheduleFragment.java` (removed 9
  field/section comments), `ScheduleViewModel.java` (removed 2 inline comments
  in `scheduleMeeting()`), `MeetingRepositoryImpl.java` (removed 1 inline
  comment in `buildScheduleMeetingSettings()`).
