## Why

Invitees currently have no way to accept, decline, or tentatively respond to a
meeting invitation, and no calendar invitation ever reaches their inbox. The
meet domain already models invitation state (`InviteeStatus` with
`NEEDS_ACTION/ACCEPTED/DECLINED/TENTATIVE`, iCalendar `calendarUid` and
`calendarSequence`) and has dead `accept()`/`decline()` domain methods with no
endpoints or consumers wired to them. This change closes the loop: a real
calendar invite email on invitation, in-app RSVP endpoints, and a calendar reply
email to the organizer on response.

## What Changes

- Add in-app RSVP endpoints on the meet service:
  `POST /meetings/{id}/invitees/{inviteeId}:accept`, `:decline`, and
  `:tentative`. The acting account must own the target invitation.
- Add the missing `tentative()` domain behavior (method, event, proto) so
  "maybe" is supported end to end alongside the existing accept/decline.
- Enrich the `InviteeAccepted`/`InviteeDeclined` events and the new
  `InviteeTentative` event with meeting context (title, start/end time,
  timezone, organizer, `calendarUid`, `calendarSequence`, invitee display name)
  so a calendar reply can be built without a cross-service lookup.
- Add calendar-invitation email delivery to the notification service: consume
  `meet.meeting.invitations.created` and send each invitee an `.ics` attachment
  with `METHOD:REQUEST`; consume the invitee response events and send the
  organizer an `.ics` with `METHOD:REPLY` carrying the invitee's `PARTSTAT`.
- Introduce ICS generation (biweekly) and email delivery (Resend) in the
  notification service, with fixed consumer groups (send-once, not broadcast)
  and a retry + dead-letter topic on send failure.

## Capabilities

### New Capabilities

- `invitee-rsvp-response`: In-app endpoints and domain behavior for an invitee
  to accept, decline, or tentatively respond to their own meeting invitation,
  including status-transition rules, ownership authorization, and the enriched
  response events published on success.
- `calendar-invitation-email`: Notification-service delivery of iCalendar emails
  — a `METHOD:REQUEST` invite to each invitee on invitation creation and a
  `METHOD:REPLY` to the organizer on each invitee response — including ICS
  generation, Resend delivery, fixed consumer groups, and retry/dead-letter
  handling.

### Modified Capabilities

- `event-driven`: The invitee response events (`meet.invitee.accepted`,
  `meet.invitee.declined`, and the new `meet.invitee.tentative`) gain
  meeting-context fields required to build an iCalendar reply.

## Impact

- **proto**: `invitee_response.proto` — new `InviteeTentative` message; added
  meeting-context fields on
  `InviteeAccepted`/`InviteeDeclined`/`InviteeTentative`.
- **meet**: domain (`MeetingInvitee.tentative()`, `InviteeTentativeEvent`,
  enriched accepted/declined events), application (3 RSVP use cases + services,
  commands, results), infrastructure (proto mappers), presentation (3 endpoints,
  request/response DTOs). Emits a new `openapi.yaml`.
- **notification**: new build dependencies (biweekly, resend-java); email port +
  model; Resend adapter; ICS generator; two Kafka consumers; email/retry config;
  `RESEND_API_KEY`/sender configuration (fail-fast when the key is absent).
- **infrastructure/config**: `RESEND_API_KEY` and sender address env vars for
  the notification service; new Kafka topic `meet.invitee.tentative` and email
  dead-letter topic.
