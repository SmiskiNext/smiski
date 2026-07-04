# Why

The web frontend has accumulated structural UI debt that makes it expensive to
evolve: core screens are monolithic, shared UI patterns are duplicated, design
tokens are not centralized, and intended platform foundations such as shadcn/ui
and a proper provider model are missing. This refactor is needed now to restore
maintainability before more web features are added, while preserving the current
user experience and internationalized flows.

## What Changes

- Introduce a shadcn/ui-compatible component foundation for the web app,
  including shared primitives, Tailwind token alignment, and lucide-react icon
  standardization.
- Replace duplicated inline SVGs and repeated header/topbar implementations with
  shared, reusable UI building blocks.
- Refactor large web screen components into feature-oriented folders and smaller
  subcomponents without changing routes, visible behavior, or i18n usage.
- Move UI fixture data into dedicated mock-data modules with TypeScript typing.
- Correct the API client provider lifecycle so initialization occurs through a
  real React provider pattern instead of module-scope side effects.
- Fix the font system to honor the already configured Geist font tokens.
- Re-enable TypeScript build checking in Next.js and update the web
  configuration so production builds fail on real type errors.

## Capabilities

### New Capabilities

- `web-ui-foundation`: Shared web UI primitives, icon usage, tokenized styling,
  and provider/layout foundations that keep the existing web experience visually
  consistent while making the frontend reusable and maintainable.
- `web-screen-composition`: Feature-based composition rules for auth, home, and
  meeting screens, including shared headers and externalized mock data, while
  preserving existing navigation and localized behavior.

### Modified Capabilities

- None.

## Impact

- Affected area:
  `/home/PNguyen/.config/spec-ade/worktrees/94d59967-a4b1-4505-9db4-65c477d73a63/dev-web-app/frontends/web`
- Key code paths: `src/components/**`, `src/components/ui/**`,
  `src/components/shared/**`, `src/lib/**`, `src/app/**`, `next.config.ts`, and
  `src/app/globals.css`
- Dependencies: shadcn/ui supporting packages, Radix UI primitives,
  lucide-react, clsx, tailwind-merge, class-variance-authority
- Systems affected: Next.js App Router rendering, next-intl localized routes,
  Firebase-backed auth flows, generated API client initialization, and frontend
  build/type-check validation
