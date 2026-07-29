## Why

The instant-create and scheduled-create flows hand-build their request bodies
and hand-write their response/error types, which drifts from the `meet`
backend's OpenAPI contract (the instant body already omits `settings` and sends
a flat issue shape instead of the nested `issueLink` the spec requires). The
repo already publishes a generated, contract-accurate TypeScript client
(`@smiskinext/smiski-ts`) that is currently unused. Adopting it while keeping
Forge Remote transport removes contract drift and gains request/response
validation for free.

## What Changes

- Route instant-create and scheduled-create through the generated SDK
  (`createInstant`, `schedule`) instead of hand-written payload builders, while
  keeping `requestRemote('meet-backend', ...)` as the transport so Forge still
  attaches the FIT. A small `fetch`→`requestRemote` adapter bridges the two.
- **BREAKING** (wire-shape): align the outbound bodies to the OpenAPI contract —
  instant gains `settings` and a nested `issueLink`; scheduled gains
  `organizerEmail` and `organizerDisplayName`. Host identity still comes from
  the invoking user; no client-asserted `X-Tenant-ID`/`X-Account-Id` headers.
- **BREAKING** (frontend error contract): `createInstantMeeting` and
  `scheduleMeeting` return the SDK `{ data, error }` result instead of throwing
  a custom `InstantMeetingError`. The create/schedule modals read `result.error`
  to surface backend problem+json messages.
- Remove the dead `apiRequest` transport path: it invokes a resolver
  (`backendRequest`) that does not exist. This deletes `api/client.ts`,
  `api/endpoints.ts`, `api/recordings.ts`, `api/participants.ts`,
  `api/permissions.ts`, and the never-called `api/meetings.ts` functions
  (`getIssueMeetings`, `getProjectMeetings`, `getMeeting`, `updateMeeting`,
  `cancelMeeting`, `startMeeting`, `endMeeting`,
  `findRunningMeetingHostedByUser`).
- Keep the in-memory `mocks/db` layer untouched — other hooks (project/issue
  meeting lists, single meeting, host conflict, participants, recording,
  update/cancel/start/end mutations) still depend on it and are out of scope.

## Capabilities

### New Capabilities

<!-- none -->

### Modified Capabilities

- `ui-backend-interaction`: The instant and scheduled create requirements change
  from hand-built payloads to the generated SDK client over Forge Remote, the
  outbound body shape aligns to the OpenAPI contract (instant `settings` +
  `issueLink`; scheduled `organizerEmail` + `organizerDisplayName`), and backend
  failures surface via the SDK `{ data, error }` result rather than a thrown
  error.

## Impact

- **Frontend**: `app/static/smiski-ui` — `src/api/meetings.ts`, new
  `src/api/forgeRemoteFetch.ts`, `src/api/mappers.ts`, `src/api/config.ts`,
  `src/api/index.ts`, `src/hooks/useMeetingMutations.ts`,
  `src/components/shared/StartInstantMeetingModal.tsx`,
  `src/components/shared/ScheduleMeetingModal.tsx`, associated tests.
- **Dependencies**: `app/static/smiski-ui/package.json` adds
  `@smiskinext/smiski-ts` as a build-time dependency (bundled by Vite into
  `dist`; Forge deploys only `dist`).
- **Removed files**: `api/client.ts`, `api/endpoints.ts`, `api/recordings.ts`,
  `api/participants.ts`, `api/permissions.ts`.
- **Not affected**: backend services, Forge resolver (`app/src/index.ts`),
  `manifest.yml`, the `mocks/` layer, and all hooks that still read from
  `mocks/db`.
