# Context

The affected code lives in `frontends/web`, a Next.js app using `next-intl` for
localisation and app-router locale segments under `src/app/[locale]`. The scope
covers wiring translations into UI components, correcting a small set of
message-file issues uncovered during verification, and addressing a focused
accessibility and UX audit in the web frontend.

Most of the changes are in presentational components under `src/components/`,
with one routing change in `src/app/[locale]/page.tsx`. No API contracts, auth
flows, or backend-facing logic need to change for this spec.

## Goals / Non-Goals

**Goals:**

- Remove remaining hardcoded brand and status text from audited web screens and
  replace it with existing or newly added translation keys.
- Improve semantic accessibility for icon-only buttons, active navigation links,
  locale toggles, password visibility toggles, and disabled join links.
- Upgrade scheduling inputs to semantic native controls that better support
  keyboard, mobile, and assistive technologies.
- Ensure profile navigation uses links for navigation destinations and derives
  avatar initials from the translated user-facing name.
- Redirect the locale root page to `/{locale}/home` instead of rendering the
  default Next.js scaffold.

**Non-Goals:**

- No new authentication, API, middleware, or state-management work.
- No visual redesign beyond the specified audit fixes.
- No changes outside `frontends/web/src/components/`,
  `frontends/web/src/app/[locale]/page.tsx`, and the affected web message files.
- No introduction of new dependencies or design system primitives.

## Decisions

### 1. Reuse existing translation structure rather than inventing new shared helpers

- Decision: Each component will continue using `useTranslations(...)` with the
  appropriate namespace, adding only the minimal new keys required by the audit.
- Rationale: The app already uses `next-intl` component-level hooks, and the
  remaining work is mostly text replacement and accessibility labelling. Adding
  another abstraction would add indirection without reducing complexity.
- Alternative considered: Create a shared brand/helper translation wrapper.
  Rejected because the audited changes are few and already map cleanly to
  current namespaces such as `common`, `workspace.common`, `meetingRoom`, and
  `auth.login`.

### 2. Treat navigation semantics as a first-class accessibility fix

- Decision: Elements that navigate to another route must be rendered as `<Link>`
  elements, while buttons remain reserved for in-place actions.
- Rationale: Screen readers, keyboard users, and browser affordances depend on
  correct semantic roles. This is especially important in
  `workspace-profile-screen.tsx`, where some cards currently act like links but
  are implemented as buttons.
- Alternative considered: Keep buttons and manually emulate link semantics.
  Rejected because native links already provide the expected behavior and
  require less maintenance.

### 3. Prefer native HTML form controls for schedule fields

- Decision: Replace freeform text inputs and static duration display with
  `type="date"`, `type="time"`, and a `<select>` with translated options.
- Rationale: Native controls improve mobile UX, validation, and accessibility
  while matching the audit scope without needing a custom picker implementation.
- Alternative considered: Introduce custom date/time pickers. Rejected because
  it would expand scope, require additional dependencies or more complex state
  handling, and is unnecessary for the current audit.

### 4. Make stateful control labels reflect actual state transitions

- Decision: For mic/video toggles and password visibility controls, use
  translation keys that describe the action the control will perform in its
  current state.
- Rationale: Controls should announce the next action, not a static label, so
  assistive technology users understand whether activating the control will show
  or hide a password or mute or unmute media.
- Alternative considered: Keep static labels and rely on visual state only.
  Rejected because it fails accessibility expectations for non-visual users.

### 5. Keep copyright year dynamic in the component

- Decision: The translation string will accept `{year}`, while the component
  passes `new Date().getFullYear()` at render time.
- Rationale: This keeps messages reusable and avoids a stale hardcoded year in
  localisation files.
- Alternative considered: Hardcode the year in translations and update annually.
  Rejected due to unnecessary maintenance and higher risk of stale content.

## Risks / Trade-offs

- **Namespace mismatch risk** — Some components will start consuming keys from
  `workspace.common`, `home`, `auth.common`, `auth.login`, `workspace.home`,
  `workspace.schedule`, and `meetingRoom`. A typo would not be caught visually
  until runtime. → Mitigation: keep key additions minimal and align exact key
  names with the confirmed scope.
- **Native input styling variance** — `date`, `time`, and `select` controls may
  render slightly differently across browsers. → Mitigation: accept native
  styling differences because semantic correctness and improved usability are
  the primary goals of this change.
- **Derived initials behavior** — Translating or changing the displayed profile
  name can alter initials output. → Mitigation: derive initials from the
  translated display string consistently, using first and last initial so the
  avatar always matches visible text.
- **Redirect-only locale root** — Replacing scaffold content with a redirect
  changes default page behavior immediately. → Mitigation: redirect directly to
  the intended home route to match the existing app information architecture.

## Migration Plan

1. Add the remaining translation keys and update any existing keys that must
   become parameterised or pluralised.
2. Apply hardcoded-text replacements and dead-code cleanup in the audited screen
   components.
3. Implement the accessibility semantics updates for toggles, icon buttons,
   navigation links, and disabled links.
4. Replace the schedule screen placeholder controls with native semantic inputs.
5. Replace the locale root scaffold with a redirect to `/{locale}/home`.
6. Validate the main user flows manually in both English and Vietnamese.

## Open Questions

- None. The scope is explicitly defined and the user confirmed that the missing
  namespaces are already present.
