## Context

The `meeting-host-implicit-start` change shipped a backend rule where a host's
`requestJoin` on a `SCHEDULED` meeting implicitly elevates the meeting to `LIVE`
and returns an approved join. That change assumed the host's join would then
flow through the existing `ALLOW_ALL` fast-path that issues a LiveKit token. In
practice, the default `MeetingSettings.defaults()` returns `MANUAL_APPROVAL`,
and both the web instant-meeting dialog and schedule-meeting form default to
`waitingRoom: true` (which maps to `MANUAL_APPROVAL`). When the host requests
join, `RequestJoinUseCase` elevates the lifecycle correctly, but then drops into
the `MANUAL_APPROVAL` branch and creates a `PENDING` `JoinRequest` for the host
themselves. Because the host is the only authorized approver of their own
meeting, that pending request is never resolved.

The web client compounds the symptom. After receiving `PENDING`, the join flow
opens an `EventSource` against `/api/v1/joinRequests/{id}/events`. That URL is
relative, so the browser resolves it against the page origin — the Next.js dev
server at `http://localhost:3000` — even though the SDK sends REST traffic to
`NEXT_PUBLIC_API_BASE_URL` (e.g. `http://localhost:30000`). The dev server has
no rewrite for that path, the SSE handshake fails, the bounded backoff burns its
three retries, and the user sees a "max timeout/retry" failure.

The two bugs combine to make every fresh meeting unjoinable for its own host.
Either bug alone would cause it; both must be fixed for the flow to work.

## Goals / Non-Goals

**Goals:**

- A host's `requestJoin` SHALL return an approved response with a LiveKit token
  on first call, regardless of the meeting's `admissionPolicy`.
- The web `EventSource` for join-request events SHALL connect to the API gateway
  base URL, the same origin used for REST traffic.
- Existing host fast-path semantics (SCHEDULED → LIVE elevation, password skip,
  ENDED/CANCELLED rejection, idempotent concurrent host requests) SHALL remain
  intact.
- Pending join requests submitted by other users SHALL keep their existing
  lifecycle — they are not auto-approved by the host's join.

**Non-Goals:**

- Any change to `Meeting.start()`, `MeetingStatus.canTransitionTo`, the Flyway
  schema, or `AdmissionPolicy` semantics for non-hosts.
- Any change to password rules or the LiveKit token request shape.
- Any change to Android source. Android already routes joins through the same
  backend endpoint and benefits from the backend fix without a client edit.
- Adding a Next.js rewrite for `/api/*`. The fix is to make the SSE URL
  explicit, not to mask it through dev-server proxying.
- Bootstrapping a new web test framework. Web SSE behavior is verified by manual
  smoke testing for this change.
- Any change to the OpenAPI surface. Both endpoints involved already exist; only
  their internal logic and client URL construction change.

## Decisions

### Decision 1: Treat `isHost` as the gate for the LiveKit fast-path, not `admissionPolicy == ALLOW_ALL`

In `RequestJoinUseCase.execute`, the branch that issues a LiveKit token and
returns `JoinRequestStatus.APPROVED` is currently guarded by
`meeting.getSettings().admissionPolicy() == AdmissionPolicy.ALLOW_ALL`. The fix
is to enter that branch when the requester is the host **or** the policy is
`ALLOW_ALL`. Concretely, the guard becomes
`isHost || admissionPolicy == ALLOW_ALL`.

**Why here, not by changing the default policy:** Changing
`MeetingSettings.defaults()` from `MANUAL_APPROVAL` to `ALLOW_ALL` would mask
the bug while breaking the user-visible behavior that "Waiting room" defaults to
enabled. The host fast-path is the actual missing rule; encoding it where it
belongs preserves the waiting-room behavior for everyone else.

**Why not split the host path into a separate method:** The branch already uses
`isHost` to pick the participant role. The condition extension is one predicate
change. Extracting a method here would create indirection without adding
clarity, and the use case is already documented as the place where
host-vs-non-host decisions live (see `prepareMeetingForJoin`).

**Alternative considered:** Auto-approve the host's `JoinRequest` after creation
in the `MANUAL_APPROVAL` branch. Rejected: this would generate a spurious
`JoinRequestCreatedEvent`, briefly persist a request that immediately
disappears, and require additional event-handling code on consumers. The
existing fast-path already handles the host correctly on `ALLOW_ALL`; we just
need to reach it.

### Decision 2: Other users' pending requests are untouched

Once the host enters the room (via the fast-path), there is no implicit "approve
all pending" sweep. Pending requests created before the host joined remain
`PENDING` and are surfaced through the existing host-side waiting-room UI, which
already supports `ApproveJoinRequestUseCase` and
`ApproveAllJoinRequestsUseCase`.

**Why:** The host fast-path is about the host's own admission, not a
side-channel for bulk approval. Bundling them couples two distinct rules and
risks accidental approval of stale or denied-by-policy requests.

### Decision 3: SSE URL is built from a single source of truth

Add `getApiBaseUrl(): string` to `frontends/web/src/lib/api/client.ts` alongside
`configureApiClient`. The helper SHALL return the base URL the SDK client is
configured with — the same value passed in `api-client-provider.tsx` from
`process.env.NEXT_PUBLIC_API_BASE_URL`. Both `use-join-meeting.ts` and
`use-waiting-room.ts` SHALL use this helper to prefix the `EventSource` URL.

**Why a helper, not direct env access:** Reading
`process.env.NEXT_PUBLIC_API_BASE_URL` at multiple call sites duplicates
configuration assumptions and lets the SDK and SSE drift apart if the source
ever changes (e.g. dynamic config, runtime override). A single helper that the
SDK client also relies on enforces consistency by construction.

**Why not a Next.js rewrite for `/api/*`:** A rewrite hides the gateway
indirection and only works in dev. Production builds, server-side renders, and
non-Next.js clients (Android) all use the gateway URL directly. Making the
front-end explicit about which origin it targets matches the existing REST
behavior and avoids dev/prod drift.

### Decision 4: No OpenAPI or contract change

Both regressions are internal to the backend logic and the web client URL
construction. Request and response shapes are unchanged, so the unified OpenAPI
spec and the regenerated SDK do not change. This keeps Android out of the change
set entirely (it already calls the same endpoint and resolves SSE URLs from its
own gateway base).

## Risks / Trade-offs

- [Risk] **Tests that asserted the host receives `PENDING` on `MANUAL_APPROVAL`
  would now flip.** None are known in `RequestJoinUseCaseTest`, which uses
  `ALLOW_ALL` for all current scenarios. → **Mitigation**: Audit the test file
  for any `MANUAL_APPROVAL` host case and flip to `APPROVED` if found; add new
  explicit `MANUAL_APPROVAL` host scenarios.

- [Risk] **A host who clicks Join twice on a `MANUAL_APPROVAL` meeting could
  create two participation logs.** The existing pessimistic lock on the meeting
  aggregate serializes the two requests, but the second request would also enter
  the fast-path (because the host check still matches), reissue a token, and
  append a second `ParticipationLog`. → **Mitigation**: This is the existing
  behavior on `ALLOW_ALL` today; the change does not introduce it. The
  participation log layer already tolerates duplicate joins for the same
  identity (LiveKit reuses the same connection), and the LiveKit webhook handler
  reconciles. Out of scope to harden further.

- [Risk] **`getApiBaseUrl()` returns an empty string when
  `NEXT_PUBLIC_API_BASE_URL` is unset.** That would yield a relative URL again
  and recreate the bug. → **Mitigation**: `api-client-provider.tsx` already
  throws when the env var is missing. The helper sources its value from the same
  configured client, so it cannot diverge from what `configureApiClient`
  actually applied.

- [Trade-off] **The host fast-path now bypasses admission policy for the host.**
  This is the intended product behavior — a host should never be asked to wait
  for approval to enter their own meeting — but it does mean `admissionPolicy`
  semantics are subtly different for the host vs everyone else. Documented
  explicitly in the spec scenarios.

## Migration Plan

1. Land all changes in one PR. No data migration; the rule is purely procedural.
2. Verification path: backend unit tests in `RequestJoinUseCaseTest` cover the
   new host-on-`MANUAL_APPROVAL` scenarios; manual smoke test from a clean
   `pnpm dev` frontends/web run hitting Caddy at `localhost:30000` confirms the
   host can create an instant meeting, navigate to green-room, and enter the
   LiveKit room without seeing a waiting-approval screen.
3. Rollback: revert the merge commit. The use case and helper changes are
   localized; no schema or contract migration is needed.

## Open Questions

None. The two-bug structure is locked from the explore phase, the user confirmed
the locked plan, and no scope has been added since.
