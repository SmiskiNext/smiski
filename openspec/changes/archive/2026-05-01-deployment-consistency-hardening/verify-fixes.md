## [2026-05-01] Round 1 (from apply auto-verify)

### Verifier

- Fixed: Biome string concatenation formatting in
  `frontends/web/src/components/api-client-provider.tsx` — moved `+` operators
  to the start of continuation lines to match Biome's style rules
  (operator-linebreak).
- Fixed: Biome string concatenation formatting in
  `frontends/web/src/components/meeting/index.tsx` — same `+` operator placement
  fix in `liveKitServerUrl` function.
