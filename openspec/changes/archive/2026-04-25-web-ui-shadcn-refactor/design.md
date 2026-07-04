# Context

The web frontend in `frontends/web` already delivers the required routes and
localized user journeys, but its implementation is difficult to extend because
foundational UI concerns are spread across large page-level components and
repeated styling patterns. The current codebase mixes custom Tailwind-only
primitives, duplicated inline SVG icons, hardcoded color values, embedded
fixture data, and duplicated topbar implementations across workspace-related
screens. It also contains correctness issues in cross-cutting infrastructure:
the API client provider performs initialization through module-scope side
effects, the intended Geist font token is overridden in global CSS, and Next.js
type-check enforcement is disabled.

This change affects multiple frontend layers at once: shared design primitives,
route-facing screen composition, CSS tokens, icons, mock data organization, and
application bootstrapping. Because the refactor must preserve the existing UI
appearance, i18n behavior, route structure, and auth/API semantics while
introducing new dependencies and folder conventions, an explicit design is
needed before implementation.

## Goals / Non-Goals

**Goals:**

- Establish a reusable shadcn/ui-compatible foundation under `src/components/ui`
  that matches existing project formatting and import conventions.
- Standardize common visual building blocks by replacing inline SVG usage with
  lucide-react icons and consolidating duplicated header/topbar patterns into a
  shared component.
- Tokenize the current web color palette in `globals.css` so components consume
  semantic color classes instead of hardcoded hex values.
- Decompose the largest web screens into feature folders and smaller
  subcomponents while preserving visible behavior, routes, translation lookups,
  and interaction flows.
- Move fixture and mock data into dedicated typed modules to separate view logic
  from sample content.
- Correct provider and configuration issues so fonts, API client initialization,
  and TypeScript build validation behave predictably in production.

**Non-Goals:**

- Redesigning the existing web experience, changing user-facing copy, or
  altering navigation structure.
- Refactoring smaller workspace screen modules beyond adopting shared primitives
  where needed.
- Changing backend APIs, auth semantics, generated API client contracts, or
  localized message content.
- Introducing dark-mode-specific UX changes beyond defining matching tokens
  needed for safe theming support.
- Migrating the web app to a different state-management, styling, or
  component-library architecture beyond the requested shadcn-compatible layer.

## Decisions

### Use a compatibility-first shadcn/ui foundation instead of a visual redesign

The refactor will add the requested shadcn-style primitives in
`src/components/ui/` using class-variance-authority, Radix primitives,
`forwardRef`, and a shared `cn()` helper. These primitives will be introduced as
infrastructure for reuse, but each component will be styled to preserve the
current UI appearance rather than adopting default shadcn aesthetics.

This approach was chosen over a broader restyle because the stated requirement
is behavior and look preservation. It also creates a stable base for future UI
work without forcing immediate churn across every screen.

### Centralize shared visual language through semantic tokens and shared components

Hardcoded colors, repeated header layouts, and duplicated icon functions will be
replaced with semantic design tokens, a shared `AppHeader` component, and
lucide-react icon imports. Tokens will be declared in `globals.css` using CSS
custom properties and mapped into Tailwind v4 `@theme inline`, allowing
components to use semantic utilities such as primary, muted, border, and
foreground variants.

This was chosen over leaving screen-specific styling in place because the
current duplication makes consistency and theming difficult. A tokenized layer
also limits future regressions when colors or spacing patterns change.

### Refactor large screens into feature folders with container-presentational boundaries

The largest screens will move into `src/components/auth`, `src/components/home`,
and `src/components/meeting`. Each folder will keep a top-level container
component responsible for orchestration and route-facing exports, while
subcomponents handle isolated pieces such as hero panels, forms, toolbars,
sidebars, and chat.

This was chosen over only extracting hooks or helper functions because the main
maintenance problem is component size and mixed concerns inside render trees.
Folder-based composition improves discoverability and makes future tests and
stories easier to add.

### Externalize fixture data into typed mock-data modules

Mock participants, attendees, and messages will be moved to `src/lib/mock-data/`
and typed with explicit interfaces shared with the consuming UI. Component
modules will import this data rather than defining literals inline.

This was chosen over leaving fixture data colocated because it reduces noise
inside already large UI files and clarifies which data is temporary or
sample-only.

### Make API client initialization idempotent inside a real client provider

`api-client-provider.tsx` will remain a client component, but it will initialize
the API client through React lifecycle-safe logic guarded by `useRef` so
configuration happens exactly once per provider lifecycle instead of at module
scope. The related interceptor tracking in `src/lib/api/client.ts` will be
updated so request and response interceptors are both registered and ejected
symmetrically.

This was chosen over keeping module-level initialization because module
execution order is harder to reason about in App Router environments and can
produce subtle behavior during hot reloads or repeated mounts.

### Re-enable strict build feedback before finishing the refactor

The Next.js configuration will remove `ignoreBuildErrors`, restore explicit
`next-intl` plugin wiring, and rely on `bun run build` as the end-to-end
verification step once refactoring is complete.

This was chosen over postponing type-check re-enablement because suppressed type
errors would hide regressions introduced by the refactor and undermine
confidence in the resulting artifact set.

## Risks / Trade-offs

- Preserving the exact visual appearance while replacing primitives and icons
  may expose spacing or alignment drift → Mitigation: use compatibility-first
  class names, map each icon to the closest lucide equivalent, and verify
  high-traffic screens after each major refactor group.
- Shared header extraction may accidentally collapse screen-specific behaviors
  that were previously implicit in duplicated code → Mitigation: model explicit
  header variants and props for nav items, branding, and right-side actions
  instead of over-generalizing.
- Breaking monolithic screens into smaller components can introduce prop
  drilling or fragmented state ownership → Mitigation: keep orchestration state
  in folder-level container components and extract only presentational or
  focused interactive segments.
- Re-enabling TypeScript build checks may surface pre-existing errors unrelated
  to the refactor → Mitigation: schedule config restoration early, fix
  configuration-level issues first, and treat build success as a gate before
  deeper UI decomposition.
- Tokenizing colors and removing hardcoded values can unintentionally change
  dark-mode or fallback rendering → Mitigation: define both light and dark
  custom properties up front and migrate components to semantic classes
  systematically.
- Provider lifecycle changes can affect auth header or interceptor timing if
  initialization ordering changes → Mitigation: preserve current configuration
  inputs and verify interceptor registration/ejection behavior against existing
  auth flows.

## Migration Plan

1. Add the shared UI foundation and utility dependencies so new primitives can
   be adopted incrementally.
2. Fix global configuration issues early: font override removal, metadata
   cleanup, next-intl plugin wiring, and TypeScript build enforcement.
3. Introduce semantic color tokens and replace hardcoded values before larger
   component decomposition.
4. Replace inline SVG usage with lucide-react icons and extract the shared
   header component used by workspace, green-room, and meeting-room screens.
5. Move fixture data into typed mock-data modules and refactor the API client
   provider/interceptor lifecycle.
6. Split auth, home, and meeting screens into feature folders and update route
   imports.
7. Run full formatting/lint/build verification, then compare key localized flows
   to confirm unchanged behavior.

Rollback is straightforward because the change is frontend-only and file-local:
revert the change set to restore previous component structure, styling values,
and provider initialization if regressions are discovered.

## Open Questions

- No blocking product questions remain; the primary implementation assumption is
  that visual output should remain materially identical even when exact icon
  glyphs shift slightly to their closest lucide-react equivalents.
- If any workspace sub-screens depend on private CSS values not listed in the
  audited palette, they will be migrated to the nearest semantic token during
  implementation rather than introducing new ad hoc colors.
