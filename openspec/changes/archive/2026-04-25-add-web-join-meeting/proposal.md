# Why

The web frontend currently lacks the real "Join Meeting" flow even though the
backend APIs and Android implementation already exist, leaving both
authenticated workspace users and public guests unable to enter meetings from
the web app. Adding this now closes a major parity gap across clients and
enables the web app to participate in the existing meeting-management workflow
without backend changes.

## What Changes

- Add a web pre-join experience for authenticated users on
  `/workspace/green-room` that replaces the current static mockup with the real
  meeting lookup and join-request flow.
- Add a public guest join route at `/{locale}/join/[code]` so unauthenticated
  users can join meetings by short code without passing through workspace auth
  middleware.
- Implement a shared web join state machine that handles meeting lookup,
  password-gated joins, join request submission, waiting-room approval, denial,
  expiration, and transport errors.
- Add waiting-room SSE handling with bounded retry behavior for pending join
  requests.
- Update web navigation and middleware so home/workspace entry points route
  users into the correct join flow.
- Add localized join-meeting copy for English and Vietnamese.
- Prepare the handoff to the meeting room by persisting or passing the issued
  meeting token after approval; LiveKit room connection remains out of scope for
  this change.

## Capabilities

### New Capabilities

- `web-join-meeting`: Web meeting join flows for authenticated users and guests,
  including meeting lookup by code, password handling, join request submission,
  waiting-room SSE updates, and approved-room navigation handoff.

### Modified Capabilities

- None.

## Impact

- Affected web frontend areas under `frontends/web/`, including locale routes,
  workspace green-room UI, home/workspace join entry points, middleware, shared
  join components, and translation messages.
- Uses existing generated meeting-management SDK operations:
  `getMeetingByShortCode`, `requestJoin`, and guest join-request event
  subscription.
- No backend API changes are required; this change consumes already-implemented
  Spring Boot endpoints and mirrors the Android flow semantics on the web.
- No LiveKit dependency is introduced in this change; the output is limited to
  token acquisition and meeting-room navigation preparation.
