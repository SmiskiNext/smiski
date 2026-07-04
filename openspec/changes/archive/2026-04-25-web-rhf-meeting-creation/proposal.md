# Why

The web frontend still relies on ad-hoc form state for authentication, join
flow, and schedule screens, which makes validation inconsistent and leaves
meeting-creation capabilities unfinished even though the backend APIs and
generated SDK are already available. This change standardizes web form handling
on react-hook-form plus zod and unlocks instant and scheduled meeting creation
so hosts can complete the meeting lifecycle directly from the web app.

## What Changes

- Standardize multi-field web forms on `react-hook-form` with `zod` validation,
  including the routed auth form, join-meeting form, and workspace schedule
  form.
- Add a shared shadcn-compatible form wrapper and reusable meeting validation
  schemas for frontend forms.
- Introduce web instant meeting creation with a new meeting action menu, instant
  meeting dialog, meeting settings form, create/start workflow state management,
  success feedback, and redirect into the meeting room.
- Replace the static workspace schedule screen with a validated schedule-meeting
  flow that submits to the existing backend API, supports invitees and meeting
  settings, and shows success/error states.
- Add English and Vietnamese copy for the new meeting-creation flows and remove
  the unused legacy auth screen.

## Capabilities

### New Capabilities

- `web-meeting-creation`: Web host flows for instant meeting creation, scheduled
  meeting creation, shared meeting settings, and localized success/error
  handling.

### Modified Capabilities

- `web-join-meeting`: Refine the web join flow so validated form behavior,
  inline password errors, and preserved user input remain part of the capability
  contract during multi-step join attempts.

## Impact

- Affected frontend areas: `frontends/web/src/components/auth/*`,
  `frontends/web/src/components/join-meeting/*`,
  `frontends/web/src/components/workspace-schedule-screen.tsx`,
  `frontends/web/src/components/home-screen.tsx`,
  `frontends/web/src/components/workspace-home-screen.tsx`, new
  `create-meeting/*` components, and web message catalogs.
- Affected dependencies: existing installed frontend packages `react-hook-form`,
  `zod`, and `@hookform/resolvers`.
- Affected APIs and SDK usage: `createInstantMeeting`, `scheduleMeeting`, and
  `startMeeting` from `frontends/web/src/generated/sdk.gen.ts`; no backend API
  changes are required.
- Cleanup impact: deletion of `frontends/web/src/components/auth-screen.tsx` as
  unused duplicate UI.
