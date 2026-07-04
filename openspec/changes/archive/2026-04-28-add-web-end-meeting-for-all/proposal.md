# Why

Hosts can already end a meeting for all participants on the backend and Android
client, but the web meeting room still only supports a local leave flow. Adding
the missing host action now closes the cross-platform gap and lets web hosts
safely terminate active meetings without leaving participants in an orphaned
room.

## What Changes

- Extend the web meeting room leave dialog to distinguish hosts from non-hosts.
- Add a host-only "End for All" action that calls the existing meeting end API,
  keeps the dialog open while the request is in progress, and disconnects plus
  returns to the workspace on success.
- Preserve the current single-action leave confirmation flow for non-host
  participants.
- Add localized host-specific dialog copy and inline error feedback for failed
  end-meeting requests.
- Add unit test coverage for host/non-host rendering and host end-meeting
  loading behavior.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `web-live-meeting-room`: Update meeting room exit behavior so hosts can choose
  between leaving locally and ending the meeting for all participants, with
  localized loading and error feedback.

## Impact

- Affected frontend code:
  `frontends/web/src/components/meeting/leave-dialog.tsx`,
  `frontends/web/src/components/meeting/index.tsx`
- Affected localization files: `frontends/web/src/messages/en.json`,
  `frontends/web/src/messages/vi.json`
- Affected tests: web meeting room component tests for leave dialog behavior
- Uses existing generated web SDK `endMeeting()` and existing backend
  `POST /v1.0/meetings/{id}:end` API without backend changes
