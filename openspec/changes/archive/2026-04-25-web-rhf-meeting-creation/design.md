# Context

The web app already has backend support and generated SDK functions for instant
meeting creation, scheduled meeting creation, and meeting start, but the
host-facing web flows are incomplete and several existing forms still use local
component state. The requested change spans shared form infrastructure, existing
auth and join experiences, new host meeting-creation UI, and schedule-page
activation, so the design needs consistent validation, reusable form
composition, and explicit async workflow boundaries.

Key constraints are already fixed: the web stack is Next.js with shadcn-style UI
primitives, `react-hook-form`, `zod`, and `@hookform/resolvers` are installed,
backend APIs are complete, and the join-meeting reducer-based async flow must
remain intact. The schedule screen currently exists as static UI, while
`auth-screen.tsx` is unused and should be removed.

## Goals / Non-Goals

**Goals:**

- Standardize the web app's multi-field forms on `react-hook-form` plus `zod`
  with a shared shadcn-compatible form wrapper.
- Preserve existing async state-machine behavior where it already models
  non-trivial workflows, especially join-meeting SSE handling.
- Add a reusable meeting schema layer and shared meeting-settings form that can
  drive both instant and scheduled meeting creation.
- Implement host meeting creation from web entry points, including instant
  meeting creation/start flow, schedule submission, success feedback, and
  localized copy.
- Remove the dead legacy auth screen while keeping the routed auth flow
  functional.

**Non-Goals:**

- Changing backend APIs, request contracts, or generated SDK code.
- Reworking simple single-field join inputs on home surfaces that only navigate
  to another route.
- Replacing the existing join-meeting reducer workflow or SSE behavior with a
  different architecture.
- Adding new external UI libraries for chips, date pickers, or form abstractions
  beyond the already installed RHF/Zod stack.

## Decisions

### Use shadcn-style RHF primitives as the single form composition pattern

The change will add `frontends/web/src/components/ui/form.tsx` following the
shadcn pattern so all migrated forms share the same `Form`, `FormField`,
`FormItem`, `FormLabel`, `FormControl`, `FormDescription`, and `FormMessage`
structure. This keeps field rendering and error presentation consistent with the
existing UI library and avoids each feature inventing its own error wiring.

Alternatives considered:

- Continue with local `useState` or `useRef` patterns per form. Rejected because
  validation, touched-state handling, and error mapping would remain
  inconsistent.
- Introduce a custom project-specific form framework. Rejected because shadcn
  already provides a well-understood pattern aligned with the current component
  stack.

### Separate form state from async workflow state

The existing join-meeting reducer will continue to own lookup, join,
waiting-room, and SSE transitions, while RHF replaces only the field-value and
validation layer in `join-meeting/join-form.tsx`. The same separation will be
used for instant meeting creation: the dialog owns form state through RHF, and a
new reducer hook owns the create/start asynchronous workflow.

Alternatives considered:

- Move all async workflow state into RHF submission handlers. Rejected because
  multi-step transitions, retries, and terminal states are easier to reason
  about in a reducer.
- Rewrite join-meeting into a single RHF-driven state model. Rejected because it
  risks regressions in an already complex SSE flow without providing
  user-visible value.

### Centralize meeting validation and request mapping in shared schemas

`frontends/web/src/lib/schemas/meeting.ts` will define `meetingSettingsSchema`,
`instantMeetingSchema`, and `scheduleMeetingSchema`. Form defaults and
validation constraints will live there, while submit handlers will map validated
values into generated SDK request types. This ensures instant and scheduled
flows use one source of truth for settings defaults, optional password behavior,
participant limits, and future-time validation.

Alternatives considered:

- Define independent schemas inside each form component. Rejected because
  meeting settings would drift between instant and scheduled flows.
- Validate only on submit with manual branching. Rejected because it weakens
  inline feedback and increases duplication.

### Use a shared meeting-settings section across instant and schedule flows

A reusable `MeetingSettingsForm` component will render shared toggles and
optional password/max-participant controls using RHF field bindings.
Waiting-room UI will map to `admissionPolicy`, while the component stays
presentation-focused and leaves request-shape conversion to submit handlers or
schema transforms.

Alternatives considered:

- Duplicate settings UI in each flow. Rejected because the settings contract is
  the same and duplication would increase translation and maintenance cost.
- Use separate "instant" and "schedule" settings components. Rejected because
  the difference is workflow context, not field semantics.

### Expose web meeting creation from a shared action menu

A `NewMeetingDropdown` wrapper will present two host actions: start an instant
meeting in-place and navigate to the schedule page for later creation. This
matches the existing design direction from other clients, allows both desktop
card and FAB triggers to share one integration point, and avoids adding separate
bespoke handlers for every surface.

Alternatives considered:

- Put separate buttons directly on each screen. Rejected because duplicate
  behavior across workspace cards and floating actions would drift.
- Navigate to a dedicated instant-meeting page. Rejected because the requested
  UX favors a low-friction dialog with minimal host input.

### Handle instant meeting creation as create-then-start with explicit success handoff

The instant meeting hook will call `createInstantMeeting`, then immediately call
`startMeeting` for the created meeting, and only transition to `READY` once the
meeting-room handoff data is available. The dialog will show a success state
with meeting code and copy-link support before auto-redirecting to
`/workspace/meeting-room`. This preserves visibility into the short code while
still minimizing delay before launch.

Alternatives considered:

- Navigate immediately after create without a success state. Rejected because
  the feature requires showing the meeting code and link-sharing affordance.
- Require a manual second click to start after creation. Rejected because the
  requested instant flow is intended to be immediate.

### Upgrade the existing schedule page instead of adding a new route

The current `workspace-schedule-screen.tsx` will become the canonical
schedule-meeting form using RHF, the shared settings section, invitee chip
entry, and SDK submission. This preserves route structure, reduces navigation
churn, and converts an existing placeholder into a production flow.

Alternatives considered:

- Build a separate schedule dialog or route. Rejected because the page already
  exists and the requested scope explicitly keeps that route.

## Risks / Trade-offs

- [Form migration changes error timing and touched behavior] → Mitigate by
  keeping schemas narrow, mapping server failures through `setError`, and
  preserving existing generic error banners for non-field failures.
- [Shared settings defaults could diverge from backend expectations] → Mitigate
  by centralizing defaults in `meetingSettingsSchema` and mapping all
  meeting-creation flows through that schema.
- [Instant create/start is a two-call workflow that can partially fail] →
  Mitigate by modeling reducer phases explicitly, surfacing retryable errors,
  and only redirecting after `startMeeting` succeeds.
- [Schedule future-time validation depends on client timezone composition] →
  Mitigate by validating computed start times in schema or submit mapping using
  the browser-local date/time values consistently.
- [Invitee chip input is custom UI with keyboard and accessibility edge cases] →
  Mitigate by keeping interactions simple, validating email on insert, and
  requiring accessible remove labels for each chip.
- [Unauthenticated home surfaces may render meeting-creation affordances
  differently from workspace surfaces] → Mitigate by making `NewMeetingDropdown`
  a wrapper that can be conditionally mounted based on existing auth-aware
  screen logic.

## Migration Plan

1. Add the shared RHF form wrapper and meeting schemas first so downstream form
   migrations can build on stable primitives.
2. Migrate existing auth and join forms without changing their route contracts
   or async service hooks.
3. Introduce `create-meeting` components and reducer hook, then integrate them
   into workspace and eligible home entry points.
4. Replace the static workspace schedule page with the validated API-backed
   scheduling flow.
5. Add translation keys, delete the unused legacy auth screen, and run
   typecheck, build, and lint validation.

Rollback is low risk because the change is frontend-only. If needed, the new
meeting-creation UI can be reverted by removing the new components and restoring
prior form implementations without backend coordination.

## Open Questions

- None at spec time; the provided request fixes the scope, route choices,
  packages, and backend contract assumptions needed for implementation.
