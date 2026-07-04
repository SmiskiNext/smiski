# Tasks

## 1. Refactor backend meeting settings model

- [x] 1.1 Update `MeetingSettings` to the simplified record shape, remove
      obsolete fields/constants, rename `passwordHash` to `password`, and set
      the new defaults
- [x] 1.2 Update `MeetingSettingsRequest` to validate and map
      `allowScreenShare`, `allowMicrophone`, `allowVideo`, `allowGuest`,
      `chatEnabled`, `maxParticipants`, and nullable `password`
- [x] 1.3 Update `MeetingSettingsResponse` to expose the simplified response
      contract and preserve `requirePassword`
- [x] 1.4 Update `MeetingSettingsJson` and `MeetingRepositoryAdapter` mappings
      to persist and rehydrate the simplified settings shape with hashed
      password storage semantics ← (verify: backend domain, request/response,
      and persistence mappings all use the same field set with no remaining
      timeout/mute/recording/screenShareMode fields)

## 2. Update backend tests for the breaking contract

- [x] 2.1 Rewrite `PutMeetingSettingsUseCaseTest` fixtures and assertions for
      the new settings structure and password naming
- [x] 2.2 Rewrite `MeetingControllerTest` request/response payloads to match the
      simplified PUT schema and removed fields ← (verify: PUT meeting settings
      tests cover new booleans, password clear/hash behavior, and preserved
      authorization/status rules)

## 3. Update OpenAPI contract and OpenSpec capability docs

- [x] 3.1 Update `openapi/unified-openapi.yaml` meeting settings
      request/response schemas to remove obsolete fields and add
      `allowScreenShare`, `allowMicrophone`, `allowVideo`, and `password`
- [x] 3.2 Regenerate Android OpenAPI DTOs from the updated unified OpenAPI
      schema
- [x] 3.3 Update `openspec/specs/meeting-settings-replacement-api/spec.md` and
      `openspec/specs/android-meeting-creation/spec.md` to document the
      simplified contract and Android defaults ← (verify: generated DTOs and
      spec artifacts reflect the same meeting settings fields and no removed
      schema fields remain)

## 4. Refactor Android meeting settings data flow

- [x] 4.1 Update `MeetingSettingsInput` to represent the new Android-facing
      settings model and defaults while keeping host video local-only
- [x] 4.2 Update `MeetingRepositoryImpl` to map Android schedule settings into
      the regenerated OpenAPI request DTOs using the simplified backend contract
- [x] 4.3 Update `ScheduleViewModel` to construct, validate, and expose the new
      meeting settings state for schedule submission ← (verify: Android domain
      and repository layers send the same field set as the regenerated API
      client, with requested default values applied)

## 5. Refactor Android schedule UI

- [x] 5.1 Update `ScheduleFragment` interaction logic to remove obsolete
      controls and bind the new microphone, video, guest, chat, and screen-share
      settings
- [x] 5.2 Update `fragment_schedule.xml` to remove
      timeout/mute/recording/screen-share-mode UI and present the simplified
      settings controls with accessible labels and existing design patterns ←
      (verify: schedule UI exposes only supported settings, preserves host-video
      local behavior, and starts with backend-aligned defaults)

## 6. Run verification

- [x] 6.1 Run `./gradlew :services:meeting-management:test`
- [x] 6.2 Run `./gradlew :android-app:app:assembleDebug` ← (verify: backend
      tests pass, Android app compiles against regenerated OpenAPI DTOs, and the
      breaking meeting settings refactor is implementation-ready)
