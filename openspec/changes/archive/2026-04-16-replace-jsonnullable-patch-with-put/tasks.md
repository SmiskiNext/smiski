# Tasks

## 1. Remove PATCH profile updates from user-management

- [x] 1.1 Remove `patchMe()` from
      `../../../../services/user-management/src/main/java/io/github/smiskinext/usermanagement/presentation/MeController.java`
      and delete `PatchUserRequest.java`, `PatchUserCommand.java`, and
      `PatchUpdateUserUseCase.java`
- [x] 1.2 Migrate controller/integration coverage from `PATCH /api/v1/me` to
      `PUT /api/v1/me` and delete patch-only unit tests that no longer apply
- [x] 1.3 Verify `PUT /api/v1/me` still preserves `UserUpdatedEvent` publication
      and existing username/avatar update behavior

## 2. Replace preferences PATCH with raw-map PUT semantics

- [x] 2.1 Replace `patchPreferences()` in
      `../../../../services/user-management/src/main/java/io/github/smiskinext/usermanagement/presentation/MeController.java`
      with `putPreferences()` that accepts a raw JSON object body for full
      replacement
- [x] 2.2 Implement a full-replacement preferences command/use-case flow and
      delete `PatchPreferencesRequest.java`, `PatchPreferencesCommand.java`, and
      `PatchUpdatePreferencesUseCase.java`
- [x] 2.3 Migrate unit and integration tests to PUT semantics: raw object
      replaces all preferences, `{}` clears preferences, and null request bodies
      are rejected

## 3. Replace meeting settings PATCH with full PUT semantics

- [x] 3.1 Replace `PATCH /api/v1/meetings/{id}/settings` in
      `../../../../services/meeting-management/src/main/java/io/github/smiskinext/meetingmanagement/presentation/MeetingController.java`
      with a PUT endpoint that uses a full settings request body
- [x] 3.2 Implement a replacement meeting settings command/use case that
      preserves host authorization, status validation, `maxParticipants` rules,
      and `MeetingSettingsUpdatedEvent` publication
- [x] 3.3 Implement explicit PUT password behavior for meeting settings:
      `password = null` clears the stored hash and non-null password values are
      hashed before persistence
- [x] 3.4 Preserve LIVE-meeting access-opening side effects by verifying
      `PendingJoinRequestApprover.approveAll(...)` still runs when admission
      policy changes to `ALLOW_ALL` or `allowGuest` changes from `false` to
      `true`
- [x] 3.5 Delete patch-specific meeting settings request/command/use case
      classes and update `MeetingControllerTest.java` plus any related use-case
      tests to cover PUT behavior

## 4. Remove JsonNullable support from backend services and shared config

- [x] 4.1 Search for remaining `JsonNullable` references in
      `services/user-management`, `services/meeting-management`, and
      `services/shared` and remove migrated endpoint dependencies on those types
- [x] 4.2 Remove `JsonNullableJackson3Module` from
      `services/shared/src/main/java/io/github/phunguy65/zms/shared/infrastructure/web/JacksonConfig.java`
- [x] 4.3 Remove `jackson-databind-nullable` from affected Gradle modules and
      clean up imports, tests, and supporting code that no longer need it

## 5. Regenerate and validate OpenAPI artifacts

- [x] 5.1 Regenerate service OpenAPI documents for `user-management` and
      `meeting-management` after the PUT-only endpoint changes
- [x] 5.2 Run `pnpm run openapi:unified` to rebuild
      `openapi/unified-openapi.yaml` with Redocly join and linting
- [x] 5.3 Verify the unified spec contains `PUT /api/v1/me`,
      `PUT /api/v1/me/preferences`, and `PUT /api/v1/meetings/{id}/settings` and
      no longer contains the removed PATCH operations
- [x] 5.4 Verify the unified spec no longer contains `JsonNullable` component
      schemas or migrated request-body wrappers

## 6. Regenerate Android SDK and remove client-side nullable support

- [x] 6.1 Update `frontends/android-app/app/build.gradle.kts` so generated
      Android DTOs no longer use `openApiNullable` wrappers, then regenerate the
      SDK with `:app:openApiGenerate`
- [x] 6.2 Remove `JsonNullableModule` registration from
      `frontends/android-app/app/src/main/java/io/github/phunguy65/zms/di/NetworkModule.java`
- [x] 6.3 Remove Android ProGuard keep rules and any generated/runtime
      references to `org.openapitools.jackson.nullable`
- [x] 6.4 Fix compile-time fallout from removed PATCH APIs and verify
      regenerated Android API interfaces no longer expose the removed PATCH
      update methods

## 7. Verification

- [x] 7.1 Run `./services/gradlew spotlessCheck`
- [x] 7.2 Run `./services/gradlew -p services/user-management test`
- [x] 7.3 Run `./services/gradlew -p services/meeting-management test`
- [x] 7.4 Run
      `./services/gradlew -p services/user-management generateOpenApiDocsFromTests`
- [x] 7.5 Run
      `./services/gradlew -p services/meeting-management generateOpenApiDocsFromTests`
- [x] 7.6 Run `pnpm run openapi:unified`
- [x] 7.7 Run `./frontends/android-app/gradlew spotlessCheck`
- [x] 7.8 Run
      `./frontends/android-app/gradlew :app:testDebugUnitTest :app:openApiGenerate :app:assembleDebug`
