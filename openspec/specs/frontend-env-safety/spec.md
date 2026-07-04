# ADDED Requirements

## Requirement: Web app provides a documented environment variable example file

The web frontend SHALL ship a `frontends/web/.env.local.example` file listing
all required and optional `NEXT_PUBLIC_*` environment variables with inline
documentation describing their purpose and expected format.

### Scenario: .env.local.example exists and covers all required variables

- **WHEN** `frontends/web/.env.local.example` is inspected
- **THEN** it SHALL contain entries for `NEXT_PUBLIC_API_BASE_URL`,
  `NEXT_PUBLIC_LIVEKIT_URL`, and all other `NEXT_PUBLIC_*` variables required
  for the web app to function

### Scenario: .env.local.example is committed and version-controlled

- **WHEN** the repository is cloned fresh
- **THEN** `frontends/web/.env.local.example` SHALL be present without any
  manual setup step

## Requirement: Web app fails fast when NEXT_PUBLIC_API_BASE_URL is absent or empty

The API client provider component SHALL throw an error at initialization time if
`NEXT_PUBLIC_API_BASE_URL` is missing or resolves to an empty string, rather
than silently constructing requests with an empty base URL.

### Scenario: Missing API base URL causes an immediate thrown error

- **WHEN** `frontends/web/src/components/api-client-provider.tsx` is loaded and
  `NEXT_PUBLIC_API_BASE_URL` is not set
- **THEN** a descriptive error SHALL be thrown before any API client instance is
  created

### Scenario: Empty string API base URL is treated as absent

- **WHEN** `NEXT_PUBLIC_API_BASE_URL` is set to an empty string `""`
- **THEN** the same error SHALL be thrown as when the variable is entirely
  absent

### Scenario: Valid API base URL proceeds normally

- **WHEN** `NEXT_PUBLIC_API_BASE_URL` is set to a non-empty URL string
- **THEN** the API client provider SHALL initialize successfully with no error

## Requirement: Web app fails fast when NEXT_PUBLIC_LIVEKIT_URL is absent

The meeting component SHALL throw an error at initialization time if
`NEXT_PUBLIC_LIVEKIT_URL` is not set, rather than falling back to `localhost`
and silently failing to connect in non-local environments.

### Scenario: Missing LiveKit URL causes an immediate thrown error

- **WHEN** `frontends/web/src/components/meeting/index.tsx` is loaded and
  `NEXT_PUBLIC_LIVEKIT_URL` is not set
- **THEN** a descriptive error SHALL be thrown before any LiveKit connection is
  attempted

### Scenario: Valid LiveKit URL proceeds normally

- **WHEN** `NEXT_PUBLIC_LIVEKIT_URL` is set to a non-empty URL string
- **THEN** the meeting component SHALL initialize successfully and attempt the
  LiveKit connection

## Requirement: Android release build config documents required placeholder overrides

The `frontends/android-app/app/build.gradle.kts` file SHALL contain TODO
comments adjacent to all release build config fields that use placeholder values
(such as `example.com` base URLs), clearly stating that these values must be
replaced with real production values before a release build.

### Scenario: Release config placeholders are annotated

- **WHEN** `frontends/android-app/app/build.gradle.kts` is inspected
- **THEN** every `buildConfigField` or `resValue` using a placeholder value
  SHALL be immediately preceded or followed by a TODO comment describing what
  the value should be replaced with
