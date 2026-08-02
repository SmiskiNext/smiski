## ADDED Requirements

### Requirement: Inbound email reply webhook ingestion

The notification service SHALL expose an inbound webhook endpoint that receives
the email provider's `email.received` event for iMIP calendar replies. The
service SHALL verify the webhook's Svix signature before processing and SHALL
reject any request whose signature is missing or invalid without publishing any
event. Because the webhook payload carries metadata only, the service SHALL
retrieve the full received message and its `text/calendar` attachment through
the provider's received-email API in order to obtain the iMIP reply body.

#### Scenario: Valid signed webhook is accepted

- **WHEN** the notification service receives an `email.received` webhook
  carrying a valid Svix signature
- **THEN** it retrieves the received message's `text/calendar` reply content and
  proceeds to parse it

#### Scenario: Invalid or missing signature is rejected

- **WHEN** an inbound webhook request arrives with a missing or invalid Svix
  signature
- **THEN** the service rejects the request, changes no invitee state, and
  publishes no event

#### Scenario: Non-calendar inbound mail is ignored

- **WHEN** a received message carries no `text/calendar` reply attachment
- **THEN** the service skips it without error, changes no state, publishes no
  event, and remains available for subsequent requests

### Requirement: iMIP reply parsing

The notification service SHALL parse the retrieved `text/calendar` payload as an
iCalendar object with `METHOD:REPLY` and SHALL extract the calendar `UID`, the
responding `ATTENDEE` email address, and the attendee `PARTSTAT`. The service
SHALL map `PARTSTAT` values to invitee responses as follows: `ACCEPTED` to
accepted, `DECLINED` to declined, and `TENTATIVE` to tentative. A payload that
cannot be parsed into a valid reply, or that carries an unrecognized `PARTSTAT`,
SHALL be skipped without terminating the endpoint.

#### Scenario: Accepted reply is extracted

- **WHEN** the payload is a `METHOD:REPLY` calendar whose `ATTENDEE` carries
  `PARTSTAT=ACCEPTED`
- **THEN** the service extracts the calendar `UID`, the attendee email, and an
  accepted response

#### Scenario: Tentative and declined replies are extracted

- **WHEN** the payload's `ATTENDEE` carries `PARTSTAT=TENTATIVE` or
  `PARTSTAT=DECLINED`
- **THEN** the service extracts the corresponding tentative or declined response

#### Scenario: Unparseable or unknown-status reply is skipped

- **WHEN** the payload cannot be parsed as a valid `METHOD:REPLY` calendar or
  carries an unrecognized `PARTSTAT`
- **THEN** the service skips it without error and publishes no event

### Requirement: Email reply event publication

The notification service SHALL publish a `meet.invitee.email-reply.received`
CloudEvent carrying the extracted calendar `UID`, the responding attendee email,
and the mapped response status, using the same messaging transport used for its
other produced events. The event SHALL be keyed so that it is routed to the meet
service's consumer.

#### Scenario: Extracted reply produces an event

- **WHEN** the service has extracted a valid `UID`, attendee email, and response
  status from an inbound reply
- **THEN** it publishes a `meet.invitee.email-reply.received` event carrying
  those three values

#### Scenario: No event when extraction fails

- **WHEN** signature verification, retrieval, or parsing does not yield a valid
  `UID`, attendee email, and response status
- **THEN** the service publishes no `meet.invitee.email-reply.received` event

### Requirement: Email reply applies invitee response in meet

The meet service SHALL consume the `meet.invitee.email-reply.received` event and
SHALL apply the response to the matching invitee. The service SHALL resolve the
meeting by the event's calendar `UID` and the invitee by that meeting together
with the responding attendee email. When a matching, non-removed invitee is
found, the service SHALL apply the accept, decline, or tentative transition,
persist the invitee, and publish the corresponding invitee-response event so the
organizer receives the existing reply notification. The applied status
transition SHALL obey the existing invitee status-transition rules.

#### Scenario: Email reply updates the matching invitee

- **WHEN** the meet service consumes an event whose `UID` resolves to a meeting
  and whose attendee email matches a non-removed invitee of that meeting, and
  the transition is permitted
- **THEN** the invitee status is updated, `respondedAt` is recorded, and the
  corresponding invitee-response event is published

#### Scenario: Response reuses organizer reply notification

- **WHEN** an email reply successfully updates an invitee to accepted, declined,
  or tentative
- **THEN** the resulting invitee-response event drives the existing organizer
  reply email, and no additional loop back to the invitee occurs

### Requirement: Email reply trust and idempotency

The meet service SHALL authorize an email-driven response solely by matching the
responding attendee email to an existing, non-removed invitee of the resolved
meeting; it SHALL NOT require or read an account identity header for this path.
When the calendar `UID` resolves to no meeting, or the email matches no active
invitee of that meeting, the service SHALL ignore the event without changing
state and without error. When the response would re-apply the invitee's current
status or otherwise violate the transition rules, the service SHALL treat the
event as a no-op: it SHALL change no state and publish no duplicate event, and
it SHALL NOT fail in a way that blocks further processing.

#### Scenario: Unknown meeting or non-invitee email is ignored

- **WHEN** the event's `UID` matches no meeting, or the attendee email matches
  no active invitee of the resolved meeting
- **THEN** the service ignores the event, changes no state, and publishes no
  event

#### Scenario: Duplicate reply is idempotent

- **WHEN** an email reply is delivered more than once, or repeats the invitee's
  current status
- **THEN** the service applies the change at most once and publishes no
  duplicate invitee-response event, without raising an error that blocks the
  partition

#### Scenario: Reply to a removed invitation is not applied

- **WHEN** the resolved invitee has been soft-removed
- **THEN** the service does not apply the response and publishes no event
