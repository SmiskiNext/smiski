# Smiski UI API Adapter

This folder is the boundary between the React UI and the future Smiski backend.
The UI should keep importing hooks from `src/hooks`; backend details should stay
inside this folder.

For the Vietnamese backend wiring checklist based on the current Spring
services, see [`BACKEND_INTEGRATION.vi.md`](./BACKEND_INTEGRATION.vi.md).

## Goal

The app still runs with local mocks by default, but it can be switched to real
backend APIs when the services are ready. The switch is intentionally small:

```bash
VITE_SMISKI_DATA_SOURCE=backend
```

When this is not set, hooks continue using `src/mocks/db.ts`, so frontend work
does not need a running backend.

## Files

| File                | Responsibility                                                                                                 |
| ------------------- | -------------------------------------------------------------------------------------------------------------- |
| `config.ts`         | Reads Vite env vars and decides mock/backend plus direct/resolver transport.                                   |
| `client.ts`         | Shared `apiRequest<T>()` wrapper, API version prefixing, query params, JSON parsing, and `ApiError`.           |
| `endpoints.ts`      | Central list of backend route assumptions. Change paths here when backend routes are finalized.                |
| `mappers.ts`        | Converts backend DTOs/envelopes into frontend domain types. Also builds backend request bodies from UI inputs. |
| `meetings.ts`       | Meeting list/detail/create/update/start/end/cancel/room-token API functions.                                   |
| `participants.ts`   | Meeting participant roster API function.                                                                       |
| `recordings.ts`     | Recording lookup/start/stop API functions.                                                                     |
| `permissions.ts`    | Project meeting permission lookup API function.                                                                |
| `currentUser.ts`    | Jira current-user lookup through `requestJira`.                                                                |
| `projectMembers.ts` | Jira project member lookup through `requestJira`.                                                              |
| `issues.ts`         | Jira project issue lookup through `requestJira`.                                                               |
| `mappers.test.ts`   | Pure tests for DTO mapping and request-body construction.                                                      |

## Data Source Switch

Hooks decide whether to call mocks or these API functions through
`shouldUseBackendApi()`.

```ts
shouldUseBackendApi(); // true only when VITE_SMISKI_DATA_SOURCE=backend
```

Affected hook areas:

- issue/project meeting reads
- meeting detail reads
- meeting create/schedule/update/cancel/start/end mutations
- participant roster reads
- permission reads
- recording reads and mutations
- LiveKit room-token reads

## Transport Modes

There are two backend transport modes.

### Resolver Transport

Default outside Vite development.

```bash
VITE_SMISKI_DATA_SOURCE=backend
```

The browser calls Forge resolver `backendRequest`. The resolver forwards the
request to `SMISKI_API_BASE_URL` (the Caddy API gateway origin), adding:

- `X-Account-Id` from Forge context
- `X-Tenant-ID` from Forge `cloudId`

Use this mode in Forge deploy/tunnel because the resolver has Jira invocation
context.

### Direct Transport

Default in Vite development when `VITE_SMISKI_API_BASE_URL` is present.

```bash
VITE_SMISKI_DATA_SOURCE=backend \
    VITE_SMISKI_API_BASE_URL=http://localhost:30000 \
    pnpm run dev
```

The browser calls the backend directly with `fetch`. This is useful for local
UI/backend integration, but it does not automatically add Forge tenant/account
headers. The local gateway/backend must handle that for development, or the
request will fail authorization.

You can force the transport explicitly:

```bash
VITE_SMISKI_API_TRANSPORT=direct
VITE_SMISKI_API_TRANSPORT=resolver
```

## Environment Variables

| Variable                    | Meaning                                                                                               |
| --------------------------- | ----------------------------------------------------------------------------------------------------- |
| `VITE_SMISKI_DATA_SOURCE`   | `mock` or `backend`; defaults to `mock`.                                                              |
| `VITE_SMISKI_API_TRANSPORT` | `resolver` or `direct`; defaults to `direct` in Vite when base URL exists, otherwise `resolver`.      |
| `VITE_SMISKI_API_BASE_URL`  | Gateway/backend origin for direct browser fetch, for example local Caddy at `http://localhost:30000`. |
| `VITE_SMISKI_API_VERSION`   | API version path segment; defaults to `1`, producing `/api/1/...`.                                    |
| `VITE_SMISKI_LIVEKIT_URL`   | Optional fallback LiveKit URL if the backend token response returns only a token.                     |
| `SMISKI_API_BASE_URL`       | Forge resolver backend origin. Configure this to the public Caddy API gateway.                        |

## Endpoint Status

The current backend OpenAPI already has:

- `POST /api/1/meetings:instant`
- `POST /api/1/meetings:schedule`
- `PUT /api/1/meetings/{id}`
- `PUT /api/1/meetings/{id}/invitees`

This UI also defines expected routes for list/detail/start/end/cancel,
participants, permissions, recording, and room-token. Those are centralized in
`endpoints.ts` because some of them are not finalized in backend OpenAPI yet.

When the backend route changes, update `endpoints.ts` first. If response bodies
change, update `mappers.ts` and `mappers.test.ts`.

## Mapping Rules

UI components use frontend domain types from `src/domain`, not backend DTOs.

Examples:

- backend `meeting.issueLink.issueKey` becomes frontend `meeting.issueKey`
- backend `organizerDisplayName` can become frontend `hostName`
- list responses may be arrays or envelopes like `{ meetings }`, `{ items }`,
  `{ content }`, or `{ data }`
- permission responses may use `hasViewMeeting` or `canViewMeeting`
- LiveKit responses may be `{ token, url }` or `{ livekit: { token, url } }`

The mapper is intentionally tolerant while backend contracts are still settling.
Once OpenAPI is final, tighten these mappings if useful.

## Request Enrichment

The UI forms collect simple inputs. `mappers.ts` enriches them into backend
request bodies:

- adds default meeting settings
- builds `issueLink`
- turns selected Jira account IDs into invitee objects
- derives organizer display name/email from the current Jira user
- builds a default one-hour `timeRange` for scheduled meetings
- creates a stable browser device ID for instant-meeting host payloads

Fallback email addresses use `example.invalid` when Jira does not expose email.
That is acceptable for local/prototype wiring, but production invite emails
should come from an authorized identity source.

## How To Test

Mock UI only:

```bash
pnpm run dev
```

Backend adapter in local Vite:

```bash
VITE_SMISKI_DATA_SOURCE=backend \
    VITE_SMISKI_API_BASE_URL=http://localhost:30000 \
    pnpm run dev
```

Static checks:

```bash
pnpm run build
pnpm run test
pnpm run lint
```

From the app root, use:

```bash
pnpm build
pnpm test
pnpm lint
```

## Before Production

- Replace placeholder Forge egress origins in `app/manifest.yml`.
- Configure `SMISKI_API_BASE_URL` in Forge.
- Finalize backend routes and update `endpoints.ts`.
- Finalize response/request DTOs and update `mappers.ts`.
- Move LiveKit token issuance to the backend and avoid the temporary resolver
  shim unless explicitly testing the prototype path.
- Ensure backend re-checks all permissions and meeting-state transitions.
