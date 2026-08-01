## Why

Invitees who receive a calendar invitation email today can click Yes/Maybe/No in
their mail client (Gmail/Outlook), but the resulting iMIP `METHOD:REPLY` email
is never ingested, so their RSVP never reaches the system — the authoritative
status only changes through the in-app REST endpoints. Separately, instant
meetings emit invitation emails with no `DTSTART`/`DTEND`, producing calendar
entries that mail clients render as invalid or timeless. This change closes the
email RSVP loop and makes instant-meeting invitations valid calendar objects.

## What Changes

- Add an inbound email RSVP pipeline in the `notification` service: a Resend
  `email.received` webhook (Svix-signed) fetches the `text/calendar` reply via
  the Resend Received-Emails/Attachments API, parses the iMIP `METHOD:REPLY`
  with `biweekly` to extract calendar `UID`, responding `ATTENDEE` email, and
  `PARTSTAT`, then publishes a new CloudEvent
  (`meet.invitee.email-reply.received`).
- Add a Kafka consumer in the `meet` service for the new event: it resolves the
  meeting by `calendarUid`, finds the invitee by meeting + sender email, and
  applies the existing accept/decline/tentative domain transition — reusing the
  current invitee-response events so the organizer still gets a `METHOD:REPLY`
  notification.
- Add `MeetingRepository.findByCalendarUid` (plus adapter, JPA query, and a new
  index on `calendar_uid`) in `meet`, and add
  `MeetingInviteeRepository.findByMeetingIdAndEmail` for invitee lookup by
  sender email.
- Instant meetings SHALL be created with a real `MeetingTimeRange` of `now` to
  `now + <configurable default duration>` so their invitation emails carry valid
  `DTSTART`/`DTEND`. **BREAKING** (spec-level): reverses the current rule that
  an instant meeting's `startTime`/`endTime` are absent.
- Add a configurable default instant-meeting duration property.
- Security/trust model for email RSVP: verify the Svix webhook signature, rely
  on provider SPF/DKIM, accept only when the sender email matches an existing
  invitee of the resolved meeting, and treat repeated/redelivered replies
  idempotently.

## Capabilities

### New Capabilities

- `reply-invite-email`: Inbound email RSVP — receiving and verifying a Resend
  `email.received` webhook, extracting the iMIP `METHOD:REPLY`, publishing the
  email-reply event, and applying the invitee status change in `meet` by
  `calendarUid` + sender email, with an email-based (non-account) trust model
  and idempotent handling.

### Modified Capabilities

- `create-instant-meeting`: Instant meetings now carry a real scheduled time
  range (`now` to `now + configurable default duration`) instead of an absent
  `startTime`/`endTime`; the invitations event and calendar email therefore
  carry valid start/end times.

## Impact

- **notification service**: new webhook controller, Svix signature verification
  (new `svix` dependency), Resend Received-Emails/Attachments API client, iMIP
  reply parser (biweekly, already present), new CloudEvent producer reusing the
  existing `KafkaTemplate<String, CloudEvent>`, `SecurityConfig` path handling.
- **meet service**: new Kafka consumer, new email-reply application use
  case/service (email-match authorization, no `X-Account-Id`),
  `MeetingRepository.findByCalendarUid`
    - adapter + JPA query + new `calendar_uid` index migration,
      `MeetingInviteeRepository.findByMeetingIdAndEmail`, `Meeting.instant()`
      time-range change, new duration config.
- **proto**: new event message for `meet.invitee.email-reply.received` (buf).
- **APIs**: no change to the three in-app RSVP REST endpoints; RBAC model
  unchanged.
- **Tests**: instant-meeting integration/unit tests updated for non-null
  start/end; new consumer/webhook/parser tests.
- **Not in scope**: fixing the "invitee without `view-meeting` can't reach
  in-app RSVP" gap (documented in design only); handling mail clients that never
  emit iMIP replies (best-effort).
