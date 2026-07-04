# ADDED Requirements

## Requirement: Backend test workflow triggers on PRs touching services

The system SHALL run a `backend-test` GitHub Actions workflow on every pull
request to `main` that includes changes under `services/**`. The workflow SHALL
use JDK 25 and `gradle/actions/setup-gradle@v4` for caching.

### Scenario: PR modifies a backend service file

- **WHEN** a pull request includes at least one changed file matching
  `services/**`
- **THEN** the `backend-test.yml` workflow is triggered

### Scenario: PR does not touch services

- **WHEN** a pull request contains no files under `services/**`
- **THEN** the `backend-test.yml` workflow is skipped

### Scenario: All backend tests pass

- **WHEN** `./services/gradlew test` completes with exit code 0
- **THEN** the workflow reports success

### Scenario: A backend test fails

- **WHEN** `./services/gradlew test` exits with a non-zero code
- **THEN** the workflow reports failure and the test report is available in the
  job log

## Requirement: Web test workflow triggers on PRs touching the web frontend

The system SHALL run a `web-test` GitHub Actions workflow on every pull request
to `main` that includes changes under `frontends/web/**`. The workflow SHALL set
up Node LTS and pnpm, install dependencies, then run
`pnpm --dir frontends/web test`.

### Scenario: PR modifies a web frontend file

- **WHEN** a pull request includes at least one changed file matching
  `frontends/web/**`
- **THEN** the `web-test.yml` workflow is triggered

### Scenario: PR does not touch the web frontend

- **WHEN** a pull request contains no files under `frontends/web/**`
- **THEN** the `web-test.yml` workflow is skipped

### Scenario: All web unit tests pass

- **WHEN** `pnpm --dir frontends/web test` exits with code 0
- **THEN** the workflow reports success

### Scenario: A web test fails

- **WHEN** `pnpm --dir frontends/web test` exits with a non-zero code
- **THEN** the workflow reports failure and the test output is visible in the
  log

## Requirement: Android workflow triggers on PRs touching the Android app

The system SHALL run an `android` GitHub Actions workflow on every pull request
to `main` that includes changes under `frontends/android-app/**`. The workflow
SHALL set up JDK, the Android SDK, and Gradle caching, then:

1. Decode `GOOGLE_SERVICES_JSON_BASE64` GitHub Secret into
   `frontends/android-app/app/google-services.json`
2. Run
   `./frontends/android-app/gradlew -p frontends/android-app :app:spotlessCheck`
3. Run unit tests via
   `./frontends/android-app/gradlew -p frontends/android-app :app:testDebugUnitTest`
4. Run
   `./frontends/android-app/gradlew -p frontends/android-app :app:assembleDebug`
5. Upload the generated APK as an artifact via `actions/upload-artifact@v4`

### Scenario: PR modifies an Android source file

- **WHEN** a pull request includes at least one changed file matching
  `frontends/android-app/**`
- **THEN** the `android.yml` workflow is triggered

### Scenario: PR does not touch Android

- **WHEN** a pull request contains no files under `frontends/android-app/**`
- **THEN** the `android.yml` workflow is skipped

### Scenario: Android Spotless check and build succeed

- **WHEN** all Spotless checks pass, unit tests pass, and assembleDebug
  completes without error
- **THEN** the workflow reports success and the APK artifact is uploaded and
  accessible from the workflow run summary

### Scenario: Android build fails due to missing google-services.json

- **WHEN** the `GOOGLE_SERVICES_JSON_BASE64` secret is absent or empty
- **THEN** the decode step fails with a clear error message before any Gradle
  task runs

### Scenario: Android Spotless violation detected

- **WHEN** `spotlessCheck` detects a formatting violation
- **THEN** the workflow fails at the Spotless step and logs the violating file
  and diff; the assembleDebug step does not run
