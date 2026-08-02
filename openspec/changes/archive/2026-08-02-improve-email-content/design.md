## Context

The notification service currently sends calendar emails using hardcoded English
strings assembled inline in three Kafka consumers
(`MeetingInvitationsCreatedEmailConsumer`, `MeetingInfoUpdatedEmailConsumer`,
`InviteeRespondedEmailConsumer`). The service has no persistent storage
(`DataSource`, `HibernateJpa`, and `Flyway` are explicitly excluded from
`NotificationApplication`). As a result it cannot resolve the Atlassian site URL
needed to build Jira issue deep-links, and there is no path to localised or
structured email content without code changes.

Additionally, the `meet` service's tenant-installed Kafka consumer silently
discards the `site_url` field from the `TenantInstalled` proto (only `cloudId`
and `updatedAt` are mapped), and three proto message types are missing fields
required for a complete email body: `issue_key`/`issue_id`/`project_key` on
invitation and invitee-response events, and `short_code` on the
meeting-info-updated and invitee-response events.

## Goals / Non-Goals

**Goals:**

- Add a minimal PostgreSQL + Flyway stack to `notification` (no business tables
  — only the `tenants` projection).
- Sync the `tenants` projection in `notification` from `tenant.tenant.installed`
  / `tenant.tenant.uninstalled` Kafka events (same pattern as `meet`).
- Fix `meet`'s tenant-installed consumer to persist `site_url`.
- Enrich three proto messages with missing fields (non-breaking additions only).
- Replace hardcoded email strings with `MessageSource`-backed templates (EN/VI).
- Produce multipart (HTML + plain-text) calendar emails via Resend.
- Construct a Jira issue deep-link (`{siteUrl}/browse/{issueKey}`) included in
  every email when both values are present.

**Non-Goals:**

- Per-recipient locale resolution (single service-wide default locale only).
- HTML templating engine (Thymeleaf, Freemarker) — HTML is built in code.
- Changes to the Forge app or Jira API integration.
- New Kafka topics or changes to the outbox pattern in `meet` or `tenant`.
- UI or API endpoint changes.

## Decisions

### D1 — Notification gets its own tenant projection table (not gRPC lookup)

**Options considered:**

- A. `notification` calls `tenant` service over gRPC at email-send time to
  resolve `site_url`.
- B. `notification` subscribes to `tenant.tenant.installed` Kafka events and
  keeps a local `tenants` projection table. ← **chosen**
- C. `meet` enriches every meeting event with `site_url` by reading from its own
  tenant projection.

**Rationale:** Option B follows the established projection-sync pattern already
implemented in `meet`. It keeps `notification` fully decoupled — no synchronous
cross-service call during email delivery, no gRPC dependency, no timeout risk.
Option C would spread site-URL concerns into every meeting event payload and
every proto message. Option A introduces a synchronous dependency in the
critical-path email send loop.

**Consequence:** `notification` gains a minimal PostgreSQL + Flyway stack. The
`tenants` table is intentionally simple (no hash partitioning — it is a lookup
table with one row per tenant, not a business table per `db-schema` spec).

### D2 — Baseline-only schema in `notification`; `meet` baseline updated in-place

**Options considered:**

- A. Add an incremental `V4` migration to `meet` for `site_url`.
- B. Edit `meet`'s `B1.0.0__baseline.sql` directly and create `notification`'s
  `B1.0.0__baseline.sql` with `site_url` from the start. ← **chosen**

**Rationale:** The project is still in development; no production deployment has
applied these baselines. In-place baseline edits keep the schema single-source
and avoid an early migration chain. The AGENTS.md rule "Never edit an applied
migration" is satisfied because `B` migrations only run on clean databases.

### D3 — Email content built by a dedicated `EmailContentBuilder` component

All subject, plain-text body, and HTML body construction is extracted from the
three consumers into a single `EmailContentBuilder` in `infrastructure/email/`.
It receives a strongly-typed `EmailContext` record (carrying all fields needed —
tenant ID, title, times, organiser, issue key, short code, invitees, response
status) and returns a `CalendarEmail` with the `htmlBody` field populated.

**Rationale:** Keeps consumers thin (parse proto → build context → delegate).
Concentrates all i18n and formatting logic in one testable unit. Avoids
duplicating MessageSource injection across three consumers.

### D4 — HTML built in Java code, no template engine

Plain `String`-based HTML construction inside `EmailContentBuilder`. The emails
are short, structured, and language-specific content comes from `MessageSource`;
there is no dynamic logic that benefits from a template engine at this scope.
Adding Thymeleaf would introduce a dependency for a few dozen lines of markup.

### D5 — `CalendarEmail` gains an `@Nullable String htmlBody` field

`CalendarEmail` is a domain value object in `domain/model/`. Adding `htmlBody`
there keeps the email record self-contained and avoids a parallel data
structure. `htmlBody` is nullable; when absent, `ResendEmailSender` sends
plain-text only.

### D6 — Proto field additions are non-breaking (append-only)

All new fields are assigned the next available field numbers and are `string`
type with empty-string proto3 defaults. Existing consumers that do not read the
new fields continue to work without change.

| Proto                                                      | New fields                                           | Field numbers  |
| ---------------------------------------------------------- | ---------------------------------------------------- | -------------- |
| `MeetingInvitationsCreated`                                | `issue_id`, `issue_key`, `project_key`               | 14, 15, 16     |
| `MeetingInfoUpdated`                                       | `short_code`                                         | 10             |
| `InviteeAccepted` / `InviteeDeclined` / `InviteeTentative` | `issue_id`, `issue_key`, `project_key`, `short_code` | 18, 19, 20, 21 |

### D7 — Default email locale is a single `@ConfigurationProperties` field

`EmailProperties` gains `String defaultLocale = "en"`. The `EmailContentBuilder`
resolves all messages with
`Locale.forLanguageTag(properties.getDefaultLocale())`. No per-request locale
resolution is needed because email consumers run on Kafka threads outside the
servlet context.

The `notification` service already activates `WebI18nConfiguration` from
`shared` (it is a servlet app with `spring.mvc` configured), so a
`MessageSource` bean is available. A `MessageBundleContributor` bean registered
in `notification`'s infra config registers the `classpath:messages/notification`
bundle.

## Risks / Trade-offs

- **Tenant table lag** → New `notification` deployments will not have `site_url`
  until the `tenant.tenant.installed` event re-arrives (or is replayed). Emails
  sent before the projection is populated will omit the Jira deep-link
  gracefully (null-safe path).
- **meet baseline edit** → If any environment has already applied `meet`'s
  `B1.0.0__baseline.sql` and the column is added there, Flyway will not auto-add
  the column; a manual `ALTER TABLE` would be needed. Acceptable for the current
  dev-only state.
- **Proto field additions** → Adding fields to existing proto messages requires
  a `buf format` + regeneration pass. All consumers that ignore unknown fields
  continue to work; the `meet` mappers that produce the enriched events must be
  updated to populate the new fields.

## Migration Plan

1. Run `./services/gradlew bufFormatApply` after proto changes.
2. Bring up a fresh `notification` Postgres instance (dev: via
   `pnpm smiski infra up` after adding its compose entry to the stack).
3. `notification` Flyway baseline runs automatically on first startup.
4. `meet` baseline is applied only on a clean DB — existing dev DBs need the
   `site_url` column added manually if already initialised.
5. Rollback: remove the new `notification` DB volume; revert proto and Java
   changes; `meet` DB column is additive and safe to leave in place.

## Open Questions

- None — all decisions made during planning session.
