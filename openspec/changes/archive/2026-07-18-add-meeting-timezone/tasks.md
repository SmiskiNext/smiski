## 1. Domain

- [x] 1.1 Add `MeetingTimeZone` value object in `domain/model/valueobject/`,
      validating IANA membership in `ZoneId.getAvailableZoneIds()` (rejects
      unknown ids and bare offsets) in the compact constructor
- [x] 1.2 Add non-null `timeZone` to `Meeting` aggregate: field, private ctor,
      `schedule()`, `instant()`, `reconstitute()`, and accessor
- [x] 1.3 Add `zoneId` to `MeetingCreatedEvent` and populate it in both
      `schedule()` and `instant()` registrations
- [x] 1.4 Add `zoneId` to `MeetingStartedEvent` and populate it in `start()`
      (instant started snapshot)
- [x] 1.5 Add `zoneId` and `endTime` to `MeetingInvitationsCreatedEvent` and
      `recordInvitationsSent(...)` ← (verify: created/started snapshots carry
      zoneId; invitations carries zoneId + scheduled endTime, absent for
      instant, per the event-publication requirements)

## 2. Application

- [x] 2.1 Add required `zoneId` to `ScheduleMeetingCommand`
- [x] 2.2 Add required `zoneId` to `CreateInstantMeetingCommand`
- [x] 2.3 Construct `MeetingTimeZone` and pass it into `Meeting.schedule()` in
      the schedule application service, and include the scheduled `endTime` in
      the invitations payload
- [x] 2.4 Construct `MeetingTimeZone` and pass it into `Meeting.instant()` in
      the instant application service

## 3. Presentation

- [x] 3.1 Add a Jakarta `@IanaZoneId` constraint + validator that fails invalid
      zone ids as `VALIDATION_ERROR`
- [x] 3.2 Add required top-level `zoneId` (`@NotBlank @IanaZoneId`) to
      `ScheduleMeetingRequest` and map it in `toCommand`
- [x] 3.3 Add required top-level `zoneId` (`@NotBlank @IanaZoneId`) to
      `CreateInstantMeetingRequest` and map it in `toCommand`
- [x] 3.4 Expose `zoneId` in `ScheduleMeetingResponse` and
      `CreateInstantMeetingResponse` snapshots (and their `from(...)` factories)
      ← (verify: both endpoints echo zoneId; missing/invalid zoneId returns 400
      VALIDATION_ERROR)

## 4. Persistence

- [x] 4.1 Add `V2__add_meetings_zone_id.sql`: add
      `zone_id VARCHAR(64) NOT NULL DEFAULT 'UTC'`, then `DROP DEFAULT`
- [x] 4.2 Add non-null `zoneId` field (+ constructor arg + accessor) to
      `MeetingJpaEntity`
- [x] 4.3 Map `zoneId` both directions in `MeetingPersistenceMapper` ← (verify:
      `ddl-auto: validate` starts clean and a persisted meeting round-trips its
      zoneId)

## 5. Messaging / proto contract

- [x] 5.1 Add `zone_id` to `meeting_snapshot.proto` using a new field number
- [x] 5.2 Add `zone_id` and `end_time` to `meeting_invitations_created.proto`
      using new field numbers (respect existing `reserved 7`)
- [x] 5.3 Set `zone_id` in `MeetingCreatedEventProtoMapper` (and any
      `MeetingSnapshot` builder used by the started mapper)
- [x] 5.4 Set `zone_id` and `end_time` in
      `MeetingInvitationsCreatedEventProtoMapper` (leave times at proto3 default
      for instant) ← (verify: `bufFormatApply` + Buf STANDARD lint pass;
      produced proto carries zone_id and end_time)

## 6. Contract regeneration

- [x] 6.1 Regenerate `services/meet/openapi.yaml`
      (`generateOpenApiDocsFromTests`) and run root `pnpm run openapi`

## 7. Fixtures & existing tests

- [x] 7.1 Add a valid `zoneId` to every existing schedule/instant request body
      in the meet unit and integration suites so pre-existing scenarios
      (settings, issueLink, invitees, time range) still pass without duplicating
      them

## 8. Tests — scheduled meeting (one per new/changed scenario)

- [x] 8.1 Successful scheduled creation returns a snapshot containing `zoneId`
      and no LiveKit token
- [x] 8.2 Missing `zoneId` returns `400 VALIDATION_ERROR` and creates nothing
- [x] 8.3 Unknown IANA id or bare offset (`+07:00`) returns
      `400 VALIDATION_ERROR` and creates nothing
- [x] 8.4 Valid IANA `zoneId` is persisted non-null and echoed in the response
- [x] 8.5 meeting-created outbox snapshot carries `startTime`, `endTime`, and
      `zoneId`; no meeting-started row
- [x] 8.6 meeting-invitations-sent outbox payload carries `zoneId`, `startTime`,
      `endTime`, and per-invitee tokens

## 9. Tests — instant meeting (one per new/changed scenario)

- [x] 9.1 Successful instant creation returns a snapshot containing `zoneId`
      plus the host LiveKit token
- [x] 9.2 Missing `zoneId` returns `400 VALIDATION_ERROR` and creates nothing
- [x] 9.3 Unknown IANA id or bare offset returns `400 VALIDATION_ERROR` and
      creates nothing
- [x] 9.4 Valid IANA `zoneId` is persisted non-null and echoed in the response
- [x] 9.5 meeting-created and meeting-started outbox snapshots both carry
      `zoneId`
- [x] 9.6 meeting-invitations-sent outbox payload carries `zoneId` with
      `startTime`/`endTime` absent

## 10. Tests — domain value object

- [x] 10.1 `MeetingTimeZone` accepts a valid IANA region id, and rejects an
      unknown id and a bare UTC offset
