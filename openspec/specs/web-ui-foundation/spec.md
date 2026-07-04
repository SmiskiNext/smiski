# ADDED Requirements

## Requirement: Shadcn-compatible primitives SHALL provide a reusable web UI foundation

The web frontend SHALL provide a shared UI primitive layer under
`src/components/ui` built with shadcn-compatible patterns, including a shared
`cn()` utility, class-variance-authority variant support where applicable,
`forwardRef` wrappers, and the Radix primitives needed by the requested
controls. The foundation SHALL include button, input, card, switch, tabs,
avatar, dialog, dropdown-menu, sheet, badge, separator, and tooltip components
so feature code can compose screens without introducing new one-off base
controls.

### Scenario: Shared primitive set is available

- **WHEN** web feature components import controls from `src/components/ui`
- **THEN** the requested primitive set SHALL exist and expose reusable APIs
  compatible with the app's TypeScript and Biome conventions

### Scenario: Shared utility merges style variants safely

- **WHEN** a component combines default classes, variant classes, and
  caller-provided `className` values
- **THEN** the `cn()` helper SHALL merge them deterministically using `clsx` and
  `tailwind-merge`

## Requirement: Web styling SHALL use semantic color and typography tokens

The web frontend SHALL define the audited color palette and font mappings as
global CSS custom properties and Tailwind v4 theme tokens instead of relying on
hardcoded component-level hex values or an Arial body override. Components
updated by this change SHALL consume semantic token-based styling so the
existing visual design remains consistent while theme values become centralized.

### Scenario: Components use centralized palette values

- **WHEN** audited web components need primary, muted, border, or foreground
  colors
- **THEN** they SHALL reference semantic token utilities or CSS variables
  derived from `globals.css` rather than embedding hex literals in component
  code

### Scenario: Geist font token is honored

- **WHEN** the root web layout renders pages using the existing font setup
- **THEN** the active sans-serif font SHALL resolve through the Geist token
  mapping instead of being overridden by a hardcoded Arial family in global
  styles

## Requirement: Shared icons and headers SHALL replace duplicated inline UI patterns

The web frontend SHALL replace duplicated inline SVG components with
lucide-react icons and SHALL provide a shared header/topbar component that
supports workspace, meeting, and green-room variants. The shared header SHALL
support branding, navigation items, and right-side actions so the existing
layouts can remain functionally equivalent without duplicating structure across
screens.

### Scenario: Duplicated icon components are removed

- **WHEN** a screen renders common actions such as help, notifications, search,
  profile, settings, video, participants, or directional navigation
- **THEN** it SHALL use mapped lucide-react icons instead of file-local SVG
  component definitions

### Scenario: Header variants preserve existing contexts

- **WHEN** workspace, green-room, and meeting-room screens render their top
  sections
- **THEN** they SHALL use the shared header component with variant-specific
  props that preserve the current actions, navigation affordances, and layout
  intent

## Requirement: Web bootstrapping SHALL initialize providers and builds correctly

The web frontend SHALL configure the API client through a real client provider
lifecycle, SHALL register and clean up interceptors symmetrically, and SHALL
fail production builds on TypeScript errors. The Next.js configuration SHALL
include the explicit next-intl plugin wiring required by the localized App
Router setup.

### Scenario: API client initialization is idempotent inside React lifecycle

- **WHEN** the API client provider mounts, rerenders, or hot reloads within the
  web app
- **THEN** API client configuration SHALL initialize through provider-scoped
  logic guarded against duplicate setup instead of module-scope execution

### Scenario: Production build enforces type safety

- **WHEN** `bun run build` runs for the web frontend
- **THEN** Next.js SHALL perform normal TypeScript error checking and use the
  configured next-intl plugin setup required for localized routes
