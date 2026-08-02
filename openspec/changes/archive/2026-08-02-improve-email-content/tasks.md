## 1. Proto — Field Additions

- [x] 1.1 Add `issue_id` (14), `issue_key` (15), `project_key` (16) to
      `MeetingInvitationsCreated` in `meeting_invitations_created.proto`
- [x] 1.2 Add `short_code` (10) to `MeetingInfoUpdated` in
      `meeting_snapshot.proto`
- [x] 1.3 Add `issue_id` (18), `issue_key` (19), `project_key` (20),
      `short_code` (21) to `InviteeAccepted`, `InviteeDeclined`,
      `InviteeTentative` in `invitee_response.proto`
- [x] 1.4 Run `./services/gradlew bufFormatApply` and verify proto compilation
      succeeds

## 2. meet — Tenant Projection Fix (site_url)

- [x] 2.1 Add `site_url VARCHAR(512)` column to `CREATE TABLE tenants` in
      `meet/src/main/resources/db/migration/B1.0.0__baseline.sql`
- [x] 2.2 Add `@Nullable String siteUrl` field to `TenantRecord` domain record
- [x] 2.3 Add `@Nullable String siteUrl` to `HandleTenantInstalledCommand`
- [x] 2.4 Update `HandleTenantInstalledApplicationService` to pass `siteUrl`
      when constructing `TenantRecord`
- [x] 2.5 Add `site_url` column to `TenantJpaEntity` with
      `@Column(name = "site_url")`
- [x] 2.6 Update `TenantRepositoryAdapter` upsert SQL to write `site_url`
- [x] 2.7 Update `TenantInstalledEventConsumer` to extract `proto.getSiteUrl()`
      into `HandleTenantInstalledCommand` ← (verify: blank site_url maps to null
      in DB, not empty string)

## 3. meet — Domain Events & Proto Mappers

- [x] 3.1 Add `issueId`, `issueKey`, `projectKey` fields to
      `MeetingInvitationsCreatedEvent` record
- [x] 3.2 Update `MeetingInvitationsCreatedEventProtoMapper` to map new issue
      fields from the domain event
- [x] 3.3 Update callers that build `MeetingInvitationsCreatedEvent`
      (`ScheduleMeetingApplicationService`,
      `AddMeetingInviteesApplicationService`,
      `CreateInstantMeetingApplicationService`) to pass issue fields from the
      meeting aggregate
- [x] 3.4 Add `shortCode` field to `MeetingInfoUpdatedEvent` domain record (if
      not already present)
- [x] 3.5 Update `MeetingInfoUpdatedEventProtoMapper` to populate `short_code`
      from the domain event
- [x] 3.6 Add `issueId`, `issueKey`, `projectKey`, `shortCode` to
      `InviteeAcceptedEvent`, `InviteeDeclinedEvent`, `InviteeTentativeEvent`
      domain records
- [x] 3.7 Update proto mappers for all three invitee response events to populate
      the new fields
- [x] 3.8 Update application services that publish invitee response events to
      pass issue and short-code fields from the meeting aggregate ← (verify: all
      three event types carry non-blank issue_key and short_code in integration
      tests)

## 4. notification — PostgreSQL + Flyway Stack

- [x] 4.1 Add `spring.boot.starter.flyway`, `flyway-database-postgresql`,
      `spring.boot.starter.data.jpa` (or `spring-jdbc`) dependencies to
      `notification/build.gradle.kts`
- [x] 4.2 Remove `DataSourceAutoConfiguration`, `HibernateJpaAutoConfiguration`,
      `FlywayAutoConfiguration` from `excludeName` list in
      `NotificationApplication`
- [x] 4.3 Add datasource config block to
      `notification/src/main/resources/application.yaml` (env vars:
      `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`,
      `POSTGRES_PASSWORD`)
- [x] 4.4 Add datasource config to `application-dev.yaml`
- [x] 4.5 Create `notification/compose.yaml` with a Postgres service (matching
      meet's compose pattern)
- [x] 4.6 Create
      `notification/src/main/resources/db/migration/B1.0.0__baseline.sql` with
      `tenants` table (tenant_id PK, cloud_id, site_url, status CHECK,
      updated_at, uninstalled_at, purge_after) ← (verify: schema matches
      design.md D1, no hash partitioning on tenants table)

## 5. notification — Tenant Projection Domain + Application Layer

- [x] 5.1 Create `domain/model/TenantProjection.java` record (`tenantId`,
      `cloudId`, `siteUrl`, `status`, `updatedAt`, `uninstalledAt`,
      `purgeAfter`)
- [x] 5.2 Create `domain/port/TenantProjectionRepository.java` interface with
      `upsert(TenantProjection)` and
      `findSiteUrl(String tenantId): Optional<String>`
- [x] 5.3 Create `application/command/HandleTenantInstalledCommand.java` record
- [x] 5.4 Create `application/command/HandleTenantUninstalledCommand.java`
      record
- [x] 5.5 Create `application/usecase/HandleTenantInstalledUseCase.java`
      interface
- [x] 5.6 Create `application/usecase/HandleTenantUninstalledUseCase.java`
      interface
- [x] 5.7 Create
      `application/service/HandleTenantInstalledApplicationService.java`
- [x] 5.8 Create
      `application/service/HandleTenantUninstalledApplicationService.java`

## 6. notification — Tenant Projection Infrastructure Layer

- [x] 6.1 Create
      `infrastructure/persistence/model/TenantProjectionJpaEntity.java`
- [x] 6.2 Create `infrastructure/persistence/TenantProjectionJpaRepository.java`
      (Spring Data)
- [x] 6.3 Create
      `infrastructure/persistence/TenantProjectionRepositoryAdapter.java`
      implementing `TenantProjectionRepository`
- [x] 6.4 Create `infrastructure/messaging/TenantKafkaProperties.java`
      (`@ConfigurationProperties(prefix = "app.notification.kafka")` — add
      `tenantInstalledConsumerGroup`, `tenantUninstalledConsumerGroup`)
- [x] 6.5 Create `infrastructure/config/TenantKafkaConfig.java` with
      `tenantKafkaListenerContainerFactory` bean (mirror `meet`'s
      `TenantKafkaConfig`)
- [x] 6.6 Create `infrastructure/messaging/TenantInstalledEventConsumer.java`
      subscribing to `tenant.tenant.installed`
- [x] 6.7 Create `infrastructure/messaging/TenantUninstalledEventConsumer.java`
      subscribing to `tenant.tenant.uninstalled`
- [x] 6.8 Add `tenant-installed-consumer-group` and
      `tenant-uninstalled-consumer-group` env vars to `application.yaml` ←
      (verify: notification starts with DB, both consumers are assigned
      partitions)

## 7. notification — EmailContentBuilder & CalendarEmail

- [x] 7.1 Add `@Nullable String htmlBody` field to `CalendarEmail` domain record
- [x] 7.2 Update `ResendEmailSender.send()` to call `.html(email.htmlBody())`
      when `htmlBody` is non-null alongside `.text(email.body())`
- [x] 7.3 Add `String defaultLocale = "en"` property to `EmailProperties`
      (`app.notification.email.default-locale`)
- [x] 7.4 Create `infrastructure/email/EmailNotificationBundleContributor.java`
      implementing `MessageBundleContributor` returning
      `classpath:messages/notification`
- [x] 7.5 Create `src/main/resources/messages/notification.properties` with
      keys: `email.invitation.subject`, `email.invitation.body.text`,
      `email.update.subject`, `email.update.body.text`,
      `email.response.subject`, `email.response.body.text`,
      `email.fallback.meeting-title`, `email.fallback.unscheduled`
- [x] 7.6 Create `src/main/resources/messages/notification_vi.properties` with
      Vietnamese translations for all keys
- [x] 7.7 Create `infrastructure/email/EmailContentBuilder.java` — injects
      `MessageSource`, `EmailProperties`, `TenantProjectionRepository`; exposes
      `buildInvitation(...)`, `buildUpdate(...)`, `buildResponse(...)` methods
      each returning a `CalendarEmail` with both `body` and `htmlBody` set ←
      (verify: HTML body contains meeting title, time, organiser, short code,
      meeting ID, and Jira link when site_url + issue_key present)

## 8. notification — Update Email Consumers

- [x] 8.1 Update `MeetingInvitationsCreatedEmailConsumer` to extract `tenantId`,
      `issueKey`, `issueId`, `shortCode` from proto; delegate to
      `EmailContentBuilder.buildInvitation(...)` instead of inline string
      assembly
- [x] 8.2 Update `MeetingInfoUpdatedEmailConsumer` to extract `tenantId`,
      `shortCode` from proto, `issueKey` from `newInfo.issueLink`; delegate to
      `EmailContentBuilder.buildUpdate(...)`
- [x] 8.3 Update `InviteeRespondedEmailConsumer` to extract `tenantId`,
      `issueKey`, `shortCode` from each proto variant; delegate to
      `EmailContentBuilder.buildResponse(...)` ← (verify: all three consumers
      send both HTML and plain-text body; missing site_url/issueKey results in
      email without deep-link, not an error)

## 9. Tests — meet

- [x] 9.1 Update `HandleTenantInstalledApplicationServiceTest` to assert
      `site_url` is persisted (including null case)
- [x] 9.2 Update `TenantInstalledEventConsumerTest` to assert `site_url` is
      extracted from proto and included in command
- [x] 9.3 Add test for `HandleTenantInstalledCommand` with blank `site_url` →
      null stored
- [x] 9.4 Update `MeetingInvitationsCreatedEventProtoMapper` unit test to assert
      new issue fields are mapped
- [x] 9.5 Update invitee response event mapper tests to assert `issue_key`,
      `short_code` fields are mapped

## 10. Tests — notification

- [x] 10.1 Add `HandleTenantInstalledApplicationServiceTest` — upsert creates
      row with `site_url`
- [x] 10.2 Add `HandleTenantUninstalledApplicationServiceTest` — upsert marks
      tenant uninstalled
- [x] 10.3 Add `TenantInstalledEventConsumerTest` — proto decoded, command
      dispatched, blank `site_url` → null
- [x] 10.4 Add `TenantUninstalledEventConsumerTest` — malformed event skipped
      without breaking consumer
- [x] 10.5 Add `EmailContentBuilderTest` — invitation body contains title, time,
      organiser, short code, meeting ID, Jira link when all fields present
- [x] 10.6 Add `EmailContentBuilderTest` — Jira link omitted when `site_url` is
      null
- [x] 10.7 Add `EmailContentBuilderTest` — Jira link omitted when `issue_key` is
      blank
- [x] 10.8 Add `EmailContentBuilderTest` — missing title falls back to bundle
      key `email.fallback.meeting-title`
- [x] 10.9 Add `EmailContentBuilderTest` — missing start/end time falls back to
      `email.fallback.unscheduled`
- [x] 10.10 Add `ResendEmailSenderTest` — `htmlBody` non-null → `.html()`
      called; null → plain text only
- [x] 10.11 Update `MeetingInvitationsCreatedEmailConsumerTest` — consumer
      delegates to `EmailContentBuilder`, new proto fields extracted ← (verify:
      build passes, all new unit tests green)

## 11. Verification

- [x] 11.1 Run `./services/gradlew bufFormatApply` — no proto format errors
- [x] 11.2 Run `./services/gradlew -p services/meet build` — all tests pass
- [x] 11.3 Run `./services/gradlew -p services/notification build` — all tests
      pass
- [x] 11.4 Run `./services/gradlew spotlessApply` — no formatting violations
