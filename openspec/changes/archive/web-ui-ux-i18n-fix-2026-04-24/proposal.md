# Why

The web app already has the required i18n namespaces added to `en.json` and
`vi.json`, but several frontend screens still ship with hardcoded brand text,
incomplete accessibility semantics, and UI controls that do not match their
intended behavior. These inconsistencies weaken localisation coverage, create
keyboard and screen-reader friction, and leave parts of the app feeling
unfinished despite the translation groundwork already being in place.

## What Changes

- Replace remaining hardcoded brand and status strings in web screens with the
  appropriate translation lookups, including shared `workspace.common.brand`
  usage and an ICU pluralised `meetingRoom.activeSession` string.
- Clean up redundant or dead UI logic in `auth-screen.tsx` and
  `splash-screen.tsx`, including the duplicated switch-prefix conditional and
  duplicate splash brand rendering.
- Improve accessibility semantics across locale toggles, password visibility
  controls, icon-only buttons, navigation links, and disabled join links.
- Update workspace scheduling inputs to use native `date`, `time`, and `select`
  controls instead of plain text or static display elements.
- Replace navigation-style buttons in the profile screen with proper links and
  derive avatar initials from the translated display name rather than hardcoded
  text.
- Replace the default Next.js locale page scaffold with a redirect to
  `/{locale}/home`.

## Capabilities

### New Capabilities

- `web-ui-a11y-polish`: Ensures icon-only controls, toggle states, locale
  selection, and disabled navigation affordances expose correct accessibility
  metadata in the web UI.

### Modified Capabilities

- `web-home-localization`: Completes localisation of brand text, locale
  controls, and home-page entry behavior.
- `web-workspace-shell`: Refines workspace shell navigation semantics and
  icon-button accessibility labels.
- `web-meeting-ui`: Refines meeting and green-room branding, control labels, and
  participant-count messaging.
- `web-workspace-scheduling`: Replaces placeholder scheduling inputs with
  semantic native form controls.
- `web-profile-navigation`: Aligns profile actions with correct
  link-versus-button semantics and translated identity rendering.

## Impact

- **Files modified**: `frontends/web/src/components/auth-screen.tsx`,
  `frontends/web/src/components/home-screen.tsx`,
  `frontends/web/src/components/splash-screen.tsx`,
  `frontends/web/src/components/green-room-screen.tsx`,
  `frontends/web/src/components/meeting-room-screen.tsx`,
  `frontends/web/src/components/workspace-home-screen.tsx`,
  `frontends/web/src/components/workspace-schedule-screen.tsx`,
  `frontends/web/src/components/workspace-profile-screen.tsx`,
  `frontends/web/src/components/workspace-shell.tsx`,
  `frontends/web/src/app/[locale]/page.tsx`.
- **Message files**: `frontends/web/src/messages/en.json` and
  `frontends/web/src/messages/vi.json` already contain the required missing
  namespaces; this change adds the remaining UI/a11y-specific keys and updates
  any strings that need ICU or parameterised formatting.
- **No backend changes required**.
- **No new runtime dependencies required**.
