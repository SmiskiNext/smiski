# email-content-templates Specification

## Purpose

Defines how the `notification` service composes calendar email content:
message-bundle-backed subjects and bodies (EN/VI) resolved via Spring
`MessageSource`, multipart HTML + plain-text delivery, a standardised
meeting-info block, and a conditional Jira issue deep-link. Locale is selected
by a single service-wide configuration property.

## Requirements

### Requirement: Message-bundle-backed email subject and body

The `notification` service SHALL resolve all email subject lines and body text
from a Spring `MessageSource` backed by locale-specific properties files
(`messages/notification.properties` for English,
`messages/notification_vi.properties` for Vietnamese). The locale SHALL be
determined by a single service-wide `app.notification.email.default-locale`
configuration property (default `en`). The `notification` service SHALL register
its bundle with the shared `MessageBundleContributor` mechanism so the
`MessageSource` bean picks it up alongside the common bundle.

#### Scenario: English message bundle resolves subjects and bodies

- **WHEN** `app.notification.email.default-locale` is `en` (or unset)
- **THEN** all email subjects and body text are resolved from
  `messages/notification.properties`

#### Scenario: Vietnamese message bundle resolves subjects and bodies

- **WHEN** `app.notification.email.default-locale` is set to `vi`
- **THEN** all email subjects and body text are resolved from
  `messages/notification_vi.properties`

#### Scenario: Unsupported locale falls back to English

- **WHEN** `app.notification.email.default-locale` is set to a locale not in the
  supported set
- **THEN** message resolution falls back to the English bundle

### Requirement: Structured multipart email (HTML + plain text)

Every calendar email sent by the `notification` service SHALL carry both an HTML
body and a plain-text body. The HTML body SHALL be set on the Resend email
request alongside the existing plain-text body. The `CalendarEmail` domain
record SHALL gain a nullable `htmlBody` field; when `htmlBody` is null the
`ResendEmailSender` SHALL send plain-text only (backward-compatible path).

#### Scenario: HTML and plain-text bodies are both sent

- **WHEN** `CalendarEmail.htmlBody` is non-null
- **THEN** `ResendEmailSender` sets both `.html()` and `.text()` on the Resend
  `CreateEmailOptions`

#### Scenario: Null htmlBody falls back to plain text only

- **WHEN** `CalendarEmail.htmlBody` is null
- **THEN** `ResendEmailSender` sets only `.text()`, matching current behavior

### Requirement: Standardised email body content

All outgoing calendar emails SHALL include the following information in both the
HTML and plain-text body:

- Meeting title (or a generic fallback when absent)
- Formatted start and end times with the meeting's IANA time zone (or
  "Unscheduled" fallback when absent)
- Organiser display name and email
- Meeting short code (displayed as a human-readable reference)
- Meeting UUID (displayed as a technical identifier)
- For invitation and update emails: a list of invitee display names
- For invitee-response emails: the responding invitee's display name and their
  RSVP status (ACCEPTED / DECLINED / TENTATIVE)
- An optional "View in Jira" button/link constructed as
  `{siteUrl}/browse/{issueKey}` (omitted when either value is absent)

The HTML body SHALL present information in a structured layout using standard
HTML elements (table or div-based). The plain-text body SHALL present the same
fields as labelled key-value lines.

#### Scenario: Invitation email body contains required fields

- **WHEN** a meeting-invitation email is composed for a meeting with all fields
  present
- **THEN** the body includes title, formatted time with zone, organiser, short
  code, meeting ID, invitee list, and the Jira deep-link

#### Scenario: Update email body contains rescheduled time

- **WHEN** a meeting-info-updated email is composed
- **THEN** the body includes the updated start and end times and all other
  standard fields

#### Scenario: Response email body identifies the responder and status

- **WHEN** an invitee-responded email is composed
- **THEN** the body identifies the invitee by name and email, states their RSVP
  status, and includes the Jira deep-link when available

#### Scenario: Missing optional fields are handled gracefully

- **WHEN** title, start time, end time, issue key, or site URL is absent from
  the event
- **THEN** the email uses the configured fallback text (e.g. "Meeting",
  "Unscheduled") or omits the corresponding block; the email is still sent

### Requirement: Email subject lines from message bundle

All email subject lines SHALL be resolved from the message bundle using
parameterised keys. The invitation subject SHALL use the meeting title as a
parameter. The update subject SHALL use the meeting title. The response subject
SHALL use the invitee display name and meeting title as parameters.

#### Scenario: Invitation subject uses meeting title

- **WHEN** a meeting-invitation email is composed with a non-blank title
- **THEN** the subject reads "Invitation: {title}" (from the EN bundle)

#### Scenario: Update subject uses meeting title

- **WHEN** a meeting-info-updated email is composed
- **THEN** the subject reads "Updated: {title}" (from the EN bundle)

#### Scenario: Response subject uses invitee name and title

- **WHEN** an invitee-response email is composed
- **THEN** the subject reads "{inviteeName} responded to {title}" (from the EN
  bundle)

#### Scenario: Missing title substitutes generic fallback

- **WHEN** the meeting title is absent
- **THEN** the subject substitutes the bundle key `email.fallback.meeting-title`
  (e.g. "Meeting")
