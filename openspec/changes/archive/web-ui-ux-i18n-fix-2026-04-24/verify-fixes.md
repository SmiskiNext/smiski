# Verify Fixes Log

## [2026-04-24] Round 1 (from apply auto-verify)

### Verifier

- **Fixed**: Trailing comma in `home` namespace in `en.json` and `vi.json` after
  adding `copyright` key — JSON parse failed without this fix.
- **Fixed**: Missing `auth.common.brand` key — auth-screen.tsx references
  `common("brand")` but the key did not exist in `auth.common`; added
  `"brand": "Zero Meet"` to both locale files.
- **Fixed**: Accidentally dropped `tabChat`, `tabPeople`, `messagePlaceholder`
  keys from `en.json`'s `meetingRoom` section during a bulk edit — restored all
  three keys.
- **Fixed**: `home.copyright` key was missing from both `en.json` and `vi.json`
  — added `"copyright": "{year} Zero Meet. All rights reserved."` (en) and
  Vietnamese equivalent with diacritics.
