## MODIFIED Requirements

### Requirement: Web app provides a documented environment variable example file

The web frontend SHALL ship a `frontends/web/.env.local.example` file listing
all required and optional `NEXT_PUBLIC_*` environment variables with inline
documentation describing their purpose and expected format. The file SHALL
include Docker Compose dev environment URLs as the default examples.

#### Scenario: .env.local.example exists and covers all required variables

- **WHEN** `frontends/web/.env.local.example` is inspected
- **THEN** it SHALL contain entries for `NEXT_PUBLIC_API_BASE_URL`,
  `NEXT_PUBLIC_LIVEKIT_URL`, and all other `NEXT_PUBLIC_*` variables required
  for the web app to function

#### Scenario: .env.local.example is committed and version-controlled

- **WHEN** the repository is cloned fresh
- **THEN** `frontends/web/.env.local.example` SHALL be present without any
  manual setup step

#### Scenario: Docker Compose dev URLs are documented

- **WHEN** `frontends/web/.env.local.example` is inspected
- **THEN** it SHALL document `http://localhost:30000` as the Docker Compose
  development API base URL and `ws://localhost:30000/livekit` as the Docker
  Compose development LiveKit URL
