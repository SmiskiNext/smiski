# Context

The monorepo already supports meeting join requests on the backend and has a
complete Android implementation for pre-join lookup, password-gated submission,
waiting-room approval, and denial handling. The web frontend, by contrast, only
has a static green-room mock and no public guest join route, which blocks both
authenticated workspace users and unauthenticated guests from entering meetings
through the browser.

This change is constrained to the Next.js web app under `frontends/web/`. It
must reuse the generated SDK operations, stay consistent with existing next-intl
and shadcn/ui patterns, and stop short of establishing a LiveKit connection
because the web app does not yet include that dependency. The output of this
flow is therefore an approved join token plus room metadata handed off to the
meeting-room route.

## Goals / Non-Goals

**Goals:**

- Provide a shared web join-meeting flow that works for both authenticated
  workspace users and public guests.
- Mirror the Android join semantics so lookup, password prompting, request
  submission, approval waiting, and error mapping behave consistently across
  clients.
- Keep guest access outside protected workspace routing while still converging
  both entry points on the same UI and hook abstractions.
- Implement bounded waiting-room SSE retry behavior for transient disconnects.
- Preserve a meeting-room handoff contract that can later be consumed by LiveKit
  integration without redesigning the join flow.

**Non-Goals:**

- Adding or changing backend APIs, authentication contracts, or
  meeting-management service behavior.
- Implementing LiveKit client setup, media publishing, or actual room connection
  on the web.
- Redesigning the broader workspace or home-page information architecture beyond
  the routing updates needed to enter the join flow.
- Introducing a new global auth context or replacing the current cookie-based
  guest/auth detection approach.

## Decisions

### Use a single `JoinMeeting` feature module with mode-based configuration

A shared feature module under `src/components/join-meeting/` will own the state
machine, form rendering, waiting dialog, and success handoff logic. The
green-room page and public guest page will each instantiate the same container
with different inputs: authenticated mode will derive display name from
session/cookie-backed user data already available in the page flow, while guest
mode will require manual display-name entry.

This keeps request orchestration, denial mapping, password handling, and SSE
logic in one place and avoids divergence between two implementations of the same
backend flow. The alternative was to build separate guest and authenticated
page-specific flows, but that would duplicate reducer logic, error handling, and
retry behavior while increasing parity drift from Android.

### Model the pre-join flow as a reducer-backed state machine

`useJoinMeeting()` will use `useReducer` with the agreed states: `IDLE`,
`LOOKING_UP`, `NEEDS_PASSWORD`, `REQUESTING`, `WAITING_APPROVAL`, `APPROVED`,
`DENIED`, `EXPIRED`, and `ERROR`. The reducer will store the looked-up meeting,
the active request identifier, field-level validation errors, the approved token
payload, and transient UI messages.

A reducer is favored over scattered `useState` because the flow has explicit
sequential transitions and terminal states, especially once password gates and
SSE-driven approval outcomes are added. The alternative of independent hooks for
lookup, request submission, and waiting-room subscription would make it harder
to guarantee legal transitions and reset behavior when users change code, retry,
or recover from denial.

### Split the flow into two phases: lookup first, then join request

The web UI will first resolve the short code through
`getMeetingByShortCode({ query: { code } })` and only call `requestJoin` after a
meeting ID is known and any required password has been collected. Password-gated
meetings will transition into `NEEDS_PASSWORD` without sending a join request
until the user resubmits.

This exactly mirrors the Android `PreJoinFragment` and `CallViewModel` semantics
and gives the UI enough context to provide inline code or password feedback. An
alternative of optimistic `requestJoin` with only short code was not viable
because the backend contract is meeting-ID based and the user experience would
lose the explicit password-required checkpoint.

### Use native `EventSource` with app-managed retry policy for waiting-room approval

Pending join requests will subscribe to
`GET /api/v1/joinRequests/{requestId}/events` using the browser `EventSource`
API. The hook will listen for `join_request_approved`, `join_request_denied`,
and `join_request_expired` event types, close the stream on any terminal event,
and apply at most three reconnect attempts with exponential delays of 1s, 2s,
and 4s after transport failures.

Native `EventSource` is sufficient because the endpoint is permit-all, the
payload shapes are simple, and the browser already handles SSE framing. The
custom retry layer is still required because the product decision is to expose a
bounded reconnect policy rather than infinite reconnects. A fetch-stream
polyfill was considered, but it would add unnecessary implementation weight for
a browser-native feature already supported by the target stack.

### Store device identity per browser tab using `sessionStorage`

The join request body requires a `deviceId`. The web flow will generate it via
`crypto.randomUUID()` and cache it in `sessionStorage` so one browser tab reuses
a stable ID across lookup retries and waiting-room reconnects while different
tabs remain isolated.

`sessionStorage` is preferred over local storage because the requirement is
tab-scoped identity, not durable multi-session device registration. Regenerating
on every submission would weaken backend continuity for pending requests and
approvals. Persisting across tabs would also diverge from the stated behavior.

### Treat guest/auth determination as a routing and input concern, not a separate API path

Both guest and authenticated users will call the same backend endpoints, since
`requestJoin` and the SSE endpoint are permit-all. The distinction only affects
route access, display-name collection, and how users reach the green-room UI.
Guest pages live at `/{locale}/join/[code]`, and middleware will mark `/join` as
public so the route bypasses workspace auth enforcement. Authenticated flows
continue to enter through `/workspace/green-room`.

This approach avoids adding parallel data-fetching contracts or permission
branches in the reducer. An alternative of maintaining guest-only components and
auth-only request orchestration would add complexity with no backend benefit.

### Handoff approved room credentials through web-accessible meeting-room state

When a join is approved, the flow will navigate to `/workspace/meeting-room` and
provide the issued `token` and `roomName` through a web-accessible handoff
channel such as search params and/or session storage. This is necessary because
guests do not carry an auth cookie that would otherwise justify server-side
route access assumptions.

A transient in-memory React context was rejected because approval can come from
SSE after route transitions, refreshes, or tab lifecycle events, and because
guests need a path into the meeting-room page without relying on workspace-only
authenticated state. Server-side session mutation was also out of scope because
the backend contract already returns the token directly.

### Keep user-facing error mapping at the feature boundary

The reducer and container will normalize backend and transport failures into a
small set of UI outcomes: inline code errors for meeting lookup failures, inline
password errors for invalid password denials, toast or dialog feedback for
guest-forbidden, meeting-full, meeting-not-live, and generic network issues,
plus a retry affordance for recoverable transport errors.

This keeps API response interpretation close to the join flow and allows the
visual components to stay presentational. The alternative of pushing all
response-code interpretation into generic SDK middleware would over-generalize
meeting-specific business outcomes that only this feature understands.

## Risks / Trade-offs

- [Meeting-room guest access may conflict with current auth assumptions] →
  Mitigation: explicitly document the token handoff contract and update
  meeting-room gating in implementation so token presence can satisfy route
  entry for guests.
- [Browser `EventSource` reconnection behavior can overlap with app-level
  retries] → Mitigation: close and recreate the source under reducer control and
  treat terminal events as final so reconnect loops remain bounded.
- [Client-side guest detection via `access_token` cookie is heuristic] →
  Mitigation: use it only to decide UI defaults and route choice, not
  authorization; backend permit-all join endpoints remain the source of truth.
- [Mismatch between Android denial reasons and web copy keys could cause vague
  messaging] → Mitigation: define explicit i18n strings for the known denial
  classes and fall back to a generic join failure message when an unknown reason
  is returned.
- [Delaying LiveKit integration means approved users may only reach a
  token-handoff page] → Mitigation: scope the change clearly so the join flow is
  considered complete once credentials are stored and navigation succeeds.

## Migration Plan

1. Add the new `web-join-meeting` spec and design documents.
2. Implement the shared join feature module, public route, and green-room
   integration in the web app.
3. Update middleware and entry-point navigation so guests and authenticated
   users reach the correct flow.
4. Add English and Vietnamese translations and ensure meeting-room token handoff
   works for both user types.
5. Validate lookup, password-required joins, pending approvals, denials, expiry,
   and retry handling in development against the existing backend.

Rollback is low risk because the change is frontend-only. Reverting the web join
feature, guest route, and middleware entry restores the previous behavior
without requiring backend rollback.

## Open Questions

- Which existing web utility is the authoritative source for the authenticated
  user display name on `/workspace/green-room` if no dedicated auth context
  exists?
- Should the meeting-room page prefer search params, session storage, or a
  combined fallback order when reading the approved token for guest entry?
- Are denial reasons already normalized in the generated SDK types, or will the
  web implementation need to defensively map raw strings from backend payloads?
