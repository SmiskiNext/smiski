# Why

Web hosts can already create meetings, but the workspace home screen does not
yet provide a focused upcoming-meetings experience comparable to Android. Adding
an upcoming host meetings list and meeting detail/actions on the web closes a
key workflow gap so hosts can quickly review, start, join, copy, inspect, and
cancel their scheduled meetings from the main workspace surface.

## What Changes

- Add a web upcoming host meetings experience on the workspace home screen that
  filters `listHostMeetings()` results to scheduled meetings whose `startTime`
  is in the future and sorts them by ascending `startTime`.
- Replace the current basic home-screen cards with richer upcoming meeting cards
  that show fallback title, time range, status, description excerpt, short code,
  and host actions.
- Add host actions for start/join, copy link, open meeting settings, and cancel
  meeting with confirmation and list refresh behavior.
- Add a meeting detail dialog or sheet that shows full meeting metadata,
  read-only settings summary, and the same host actions as the card.
- Add localized English and Vietnamese strings for the new upcoming meeting
  list, detail view, confirmation dialog, and action feedback.
- Refactor the workspace home screen to delegate upcoming-meeting behavior into
  dedicated reusable components and a custom hook.

## Capabilities

### New Capabilities

- `web-host-upcoming-meetings`: Display, inspect, and manage upcoming host
  meetings from the web workspace home screen, including filtering, sorting,
  meeting detail presentation, and host actions.

### Modified Capabilities

- None.

## Impact

- Affected frontend area:
  `/home/PNguyen/.config/spec-ade/worktrees/94d59967-a4b1-4505-9db4-65c477d73a63/dev-web-app/frontends/web/src/components/workspace-home-screen.tsx`
  and new components under
  `/home/PNguyen/.config/spec-ade/worktrees/94d59967-a4b1-4505-9db4-65c477d73a63/dev-web-app/frontends/web/src/components/upcoming-meetings/`.
- Affected localization files:
  `/home/PNguyen/.config/spec-ade/worktrees/94d59967-a4b1-4505-9db4-65c477d73a63/dev-web-app/frontends/web/src/messages/en.json`
  and
  `/home/PNguyen/.config/spec-ade/worktrees/94d59967-a4b1-4505-9db4-65c477d73a63/dev-web-app/frontends/web/src/messages/vi.json`.
- Affected SDK usage: `listHostMeetings`, `cancelMeeting`, and existing
  meeting-settings flows on the web frontend.
- No backend API or schema changes are required.
