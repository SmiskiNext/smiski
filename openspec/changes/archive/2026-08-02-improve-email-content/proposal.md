## Why

Calendar emails sent by the notification service have hardcoded English subject
and body strings with no structured meeting information, no HTML formatting, no
Jira issue deep-link, and no way to configure or localise the content without a
code change. The notification service also has no persistent storage, so it
cannot resolve the sending tenant's Atlassian site URL — which is required to
build the Jira deep-link — without adding a local tenant projection.

## What Changes

- Add PostgreSQL + Flyway to the `notification` service and introduce a
  `tenants` projection table synced from `tenant.tenant.installed` /
  `tenant.tenant.uninstalled` Kafka events. This table stores `site_url` so the
  notification service can construct Jira deep-links without calling another
  service at send time.
- Fix the `meet` service's tenant-installed consumer: it currently discards
  `site_url` even though the `TenantInstalled` proto carries it. Update the
  command, application service, JPA entity, and baseline schema to persist it.
- Enrich three Kafka event payloads that are missing fields needed for a
  standardised email body:
    - `MeetingInvitationsCreated` proto: add `issue_id`, `issue_key`,
      `project_key`.
    - `MeetingInfoUpdated` proto: add `short_code` at the top-level message.
    - `InviteeAccepted`, `InviteeDeclined`, `InviteeTentative` protos: add
      `issue_id`, `issue_key`, `project_key`, `short_code`.
    - Update the corresponding `meet` domain events and proto mappers
      accordingly.
- Replace all hardcoded email subject/body strings with a new
  `EmailContentBuilder` component backed by Spring `MessageSource`. Add EN and
  VI message-bundle files under `messages/notification.properties`.
- Add `htmlBody` to `CalendarEmail` and update `ResendEmailSender` to send
  multipart (HTML + plain-text) emails.
- Each email now includes: meeting title, formatted start/end time with
  time-zone, organiser name, meeting short code, meeting ID, a "View in Jira"
  button linking to `{siteUrl}/browse/{issueKey}` (omitted gracefully when
  either value is absent), and an invitee list (invitation/update emails) or
  response status (organiser reply emails).
- Default email locale is a single service-wide config property (default `en`);
  per-recipient locale is explicitly out of scope for this change.

## Capabilities

### New Capabilities

- `notification-tenant-projection`: PostgreSQL + Flyway stack in the
  `notification` service; `tenants` table (tenant_id, cloud_id, site_url,
  status, updated_at, uninstalled_at, purge_after); Kafka consumers for
  `tenant.tenant.installed` and `tenant.tenant.uninstalled` following the same
  pattern as `meet`.
- `email-content-templates`: Message-bundle-backed email content (EN/VI);
  multipart HTML + plain-text body; structured meeting-info block; conditional
  Jira issue deep-link; configurable default locale.

### Modified Capabilities

- `calendar-invitation-email`: Email subject and body requirements change —
  content must now come from a message bundle, include structured meeting
  information, and carry an optional Jira issue deep-link. HTML body is now
  required alongside plain text.
- `consume-install-app`: `meet` service's tenant-installed handler must persist
  `site_url` from the event into the local `tenants` projection (currently the
  field is dropped). Baseline schema updated in-place (no new migration).
- `event-driven`: Three event payloads gain new fields (`issue_id`, `issue_key`,
  `project_key` on invitation and invitee-response protos; `short_code` on the
  meeting-info-updated proto and invitee-response protos).

## Impact

- **services/proto**: `meeting_invitations_created.proto`,
  `meeting_snapshot.proto` (MeetingInfoUpdated message),
  `invitee_response.proto` — field additions only; no field removals or
  renumbering (non-breaking).
- **services/meet**: `TenantRecord`, `HandleTenantInstalledCommand`,
  `HandleTenantInstalledApplicationService`, `TenantJpaEntity`,
  `TenantRepositoryAdapter`, `B1.0.0__baseline.sql` (add `site_url` column);
  `MeetingInvitationsCreatedEvent` + mapper;
  `MeetingInfoUpdatedEventProtoMapper`; invitee-response domain events +
  mappers.
- **services/notification**: new Gradle dependencies (flyway, postgresql,
  data-jpa); `NotificationApplication.java` exclusion list; `application.yaml`
  datasource config; new `compose.yaml`; Flyway baseline; domain + application +
  infrastructure layers for tenant projection; `EmailConsumerProperties` (add
  locale); `EmailContentBuilder`; `CalendarEmail` (add htmlBody);
  `ResendEmailSender`; all three email consumers updated.
- **No API changes** — this is entirely internal to the event-driven email
  pipeline.
