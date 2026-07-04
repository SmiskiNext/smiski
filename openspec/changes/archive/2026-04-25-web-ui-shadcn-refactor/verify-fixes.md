## [2026-04-25] Round 1 (from apply auto-verify)

### Verifier

- Fixed: Replaced all hardcoded hex color values in `auth/hero.tsx` with CSS
  variable references (`--preview-dark-card`, `--preview-avatar-gray`,
  `--preview-card-bg-start/end`, `--preview-tile-dark/light-start/end`,
  `--orb-blue-1/2`, `--surface-hero-start/end`,
  `--surface-hero-register-start/end`); replaced `#15191e` with
  `text-text-dark`; replaced `#dbe7ff`/`#3157b8` with
  `bg-icon-security-bg`/`text-icon-security-color`
- Fixed: Replaced hardcoded `#f8fbff` hover color in `auth/social-button.tsx`
  with `hover:bg-surface-pale-blue`
- Fixed: Replaced hardcoded preview tile colors and card border in
  `home/hero.tsx` with CSS variable class references
  (`bg-[var(--preview-tile-*)]`, `border-border-card`)
- Fixed: Replaced hardcoded `#9aa0a6` in `home/join-form.tsx` with
  `text-text-disabled`
- Fixed: Extracted inline `KeyboardIcon` SVG from `home/join-form.tsx` and
  replaced with `Keyboard` from lucide-react
- Fixed: Replaced hardcoded `#111827` in `meeting/index.tsx` with
  `bg-meeting-bg`
- Fixed: Replaced hardcoded `#98a2b3`, `#1a3760`, `#f7f9fc`, `#d1d5db` in
  `meeting/sidebar.tsx` with semantic token classes (`text-text-message-time`,
  `text-text-message-own`, `bg-surface-person-item`,
  `text-border-muted-disabled`)
- Fixed: Replaced hardcoded `ring-offset-[#111827]` in
  `meeting/participant-grid.tsx` with `ring-offset-meeting-bg`
- Fixed: Replaced hardcoded `#243b67`/`#4e7fd4` gradient in
  `shared/app-header.tsx` (both `ProfileAvatar` and inline meeting header
  button) with CSS variable references
- Fixed: Replaced all hardcoded avatar gradient hex values in
  `lib/mock-data/green-room.ts` with CSS variable references
- Fixed: Replaced all hardcoded tileBg gradient hex values and avatarGradient
  hex values in `lib/mock-data/meeting.ts` with CSS variable references
- Fixed: Moved `configureApiClient()` from render body into `useEffect` with
  cleanup in `api-client-provider.tsx`; added `ejectApiClient()` export to
  `lib/api/client.ts` that symmetrically ejects both interceptors and resets IDs
  to null
- Fixed: Extracted chat section from `meeting/sidebar.tsx` into new
  `meeting/chat.tsx` self-contained presentational component; sidebar now
  imports and renders `MeetingChat`
- Fixed: Removed inline comments from `lib/api/client.ts` (em-dash `—` style
  replaced with standard `-` in JSDoc)
- Fixed: Added all new semantic tokens to `globals.css` `:root` block and mapped
  them through `@theme inline`
- Verified: `bun run lint` passes with 0 errors after Biome auto-fix
- Verified: `bun run build` passes with 0 TypeScript or compilation errors
