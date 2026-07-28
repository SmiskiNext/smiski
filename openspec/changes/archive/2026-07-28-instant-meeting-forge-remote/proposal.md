## Why

The Forge app calls the `meet` backend with a plain egress `fetch` and
**self-attaches** `X-Tenant-ID`/`X-Account-Id` headers
(`app/src/meetSdkClient.ts`). This violates the Forge Remote design
(`requirements/references/forgeapp-remote-backend.md`): a Forge app must reach a
self-hosted backend via Forge Remote so Atlassian attaches a signed **Forge
Invocation Token (FIT)** as `Authorization: Bearer`, and the client must never
assert tenant/account identity itself (unsigned, spoofable). The backend already
expects identity to arrive from the trusted gateway boundary (`meet`
`SecurityConfig` performs no token verification and reads
`X-Tenant-ID`/`X-Account-Id` injected by the gateway).

## What Changes

- Declare the `meet` backend as a Forge **remote** in `app/manifest.yml`
  (`remotes` + `modules.endpoint` with the appropriate `auth` token flags) so
  Forge attaches the FIT to outbound calls.
- Call the backend with `requestRemote` **directly from Custom UI** for the
  instant-create flow, replacing the current `invoke('createInstantMeeting')`
  resolver round-trip. The frontend builds the JSON body (unchanged shape) and
  maps the response as today.
- **BREAKING**: stop sending `X-Tenant-ID`/`X-Account-Id` from the app entirely.
  Forge attaches only the FIT; deriving tenant/account from the FIT is the
  gateway's job (implemented later — out of scope here).
- Remove the now-unused resolver path: delete the `createInstantMeeting`
  resolver from `app/src/index.ts`, delete `app/src/meetSdkClient.ts`, and drop
  the `@smiskinext/smiski-ts` dependency from the app (the backend SDK is no
  longer invoked from the Forge function).
- **Known consequence (intentional)**: until the gateway verifies the FIT and
  injects the identity headers, instant-create will not complete end-to-end.
  This change makes the app-side contract correct; the gateway work is a
  separate, later change.

## Capabilities

### New Capabilities

<!-- None. -->

### Modified Capabilities

- `ui-backend-interaction`: The "Instant meeting creation through the backend
  SDK" requirement changes — the app now calls the backend via Forge Remote
  (`requestRemote` from Custom UI) with Forge attaching a FIT, and the app no
  longer attaches tenant/account headers. Identity resolution from the FIT
  becomes a gateway responsibility rather than an app responsibility.

## Impact

- **App manifest** (`app/manifest.yml`): add `remotes` + `modules.endpoint`;
  reconcile the `external.fetch.backend` placeholder with the remote.
- **App frontend** (`app/static/smiski-ui/src/api/meetings.ts`):
  `createInstantMeeting` switches from `invoke(...)` to `requestRemote(...)`;
  removes any tenant/account header attachment; keeps
  `buildInstantMeetingPayload` and `meetingFromBackend`.
- **App resolver** (`app/src/index.ts`): remove `createInstantMeeting` resolver
  and its imports. Keep `getRoomToken`, `searchWorkspaceUsers`, and existing
  stubs.
- **App files removed**: `app/src/meetSdkClient.ts`; app dependency
  `@smiskinext/smiski-ts` (verify no other importer — current usage is only the
  removed resolver path).
- **Tests**: `app/static/smiski-ui/src/api/createInstantMeeting.test.ts` updates
  its mock from `@forge/bridge` `invoke` to `requestRemote`.
- **Out of scope**: Kong gateway (FIT verification + header injection), the
  `meet` backend service, `getRoomToken`, `searchWorkspaceUsers`, and the
  schedule/list/update/delete flows.
