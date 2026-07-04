# Tasks

## 1. Set up the shared web UI foundation

- [x] 1.1 Add the shadcn/ui support dependencies and required Radix packages to
      the web frontend package manifest
- [x] 1.2 Create `src/lib/utils.ts` with the shared `cn()` helper that composes
      `clsx` and `tailwind-merge`
- [x] 1.3 Add the requested base primitives under `src/components/ui/` using
      shadcn-compatible patterns, project import conventions, and Biome
      formatting ← (verify: all requested primitives exist, compile cleanly, and
      expose reusable typed APIs)

## 2. Restore global styling and build correctness

- [x] 2.1 Remove the Arial body font override and update root metadata so the
      existing Geist font token and app-specific metadata are applied
- [x] 2.2 Restore explicit `next-intl` plugin wiring in `next.config.ts` and
      remove the `ignoreBuildErrors` TypeScript bypass
- [x] 2.3 Confirm `next-intl` is declared in dependencies and that the web build
      can surface real type errors again ← (verify: configuration matches
      localized App Router usage and build no longer suppresses TypeScript
      failures)

## 3. Normalize design tokens and shared visual language

- [x] 3.1 Add the audited light and dark color tokens to `src/app/globals.css`
      with Tailwind v4 `@theme inline` mappings
- [x] 3.2 Replace hardcoded audited hex values in web components with semantic
      token-based classes or CSS variable references
- [x] 3.3 Replace duplicated inline SVG components with mapped lucide-react
      icons across the affected web screens ← (verify: audited colors and icons
      are centralized, and no duplicated file-local SVG helpers remain in the
      targeted components)

## 4. Extract shared structural UI elements

- [x] 4.1 Create `src/components/shared/app-header.tsx` to support workspace,
      meeting, and green-room header variants with brand, navigation, and action
      props
- [x] 4.2 Update `workspace-shell.tsx`, `green-room-screen.tsx`, and
      `meeting-room-screen.tsx` to consume the shared header and shadcn
      Button-based actions
- [x] 4.3 Validate that the extracted header preserves each screen's existing
      layout intent and interaction affordances ← (verify: all three contexts
      render the correct variant-specific nav and actions without layout
      regressions)

## 5. Externalize mock data and provider lifecycle logic

- [x] 5.1 Create typed mock-data modules under `src/lib/mock-data/` for
      green-room attendees, meeting participants, and initial meeting messages
- [x] 5.2 Update green-room and meeting components to import typed fixture data
      instead of declaring inline arrays
- [x] 5.3 Refactor `api-client-provider.tsx` to initialize the client through an
      idempotent provider lifecycle guard and update `src/lib/api/client.ts` to
      track and eject response interceptors symmetrically ← (verify: fixture
      data is fully externalized and API client initialization/interceptor
      behavior remains stable across mounts and rerenders)

## 6. Split large screens into feature folders

- [x] 6.1 Create the `src/components/auth/` structure and move auth
      responsibilities into container, form, hero, and social-button
      subcomponents
- [x] 6.2 Create the `src/components/home/` structure and split the home
      experience into container, hero, join-form, and actions subcomponents
- [x] 6.3 Create the `src/components/meeting/` structure and split the meeting
      experience into container, toolbar, sidebar, participant-grid, and chat
      subcomponents
- [x] 6.4 Update all affected localized route files to import the new
      feature-folder entry components while preserving current paths and
      behavior ← (verify: auth, home, and meeting routes render through the new
      folder structure with unchanged i18n, navigation, and user-visible
      behavior)

## 7. Run final verification

- [x] 7.1 Run formatting and lint checks required by the web frontend tooling
- [x] 7.2 Run `bun run build` for the web frontend and fix any remaining type or
      integration issues
- [x] 7.3 Perform a focused regression pass on localized auth, home, workspace,
      green-room, and meeting flows ← (verify: build passes and the refactored
      UI preserves existing appearance, translations, auth flow, route
      navigation, and API client behavior)
