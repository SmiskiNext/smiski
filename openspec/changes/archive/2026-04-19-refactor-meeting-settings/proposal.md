# Why

The current `MeetingSettings` model exposes fields and enums that no longer
match the simplified product direction for meeting configuration. Because the
system is not yet in production, this is the right time to make a breaking
contract cleanup across backend, OpenAPI, Android, and specs before more clients
depend on the old shape.

## What Changes

- **BREAKING** Refactor the backend `MeetingSettings` value object and all
  request/response/persistence mappings to remove `joinRequestTimeout`,
  `muteOnEntry`, and `recordingEnabled`.
- **BREAKING** Replace `screenShareMode` with a simpler `allowScreenShare`
  boolean where host sharing remains implicit and the flag controls participant
  sharing.
- **BREAKING** Rename `passwordHash` to `password` in the domain-facing settings
  model while continuing to store the value as a hash internally.
- Add `allowMicrophone` and `allowVideo` to the meeting settings contract so
  hosts can control participant media permissions explicitly.
- Update the unified OpenAPI schemas and regenerate Android DTOs so generated
  API models match the new meeting settings contract.
- Update Android meeting creation inputs, repository mapping, view model,
  fragment, and layout to remove obsolete controls and present the new settings
  shape with the documented defaults.
- Update backend and Android-facing specs to reflect the new request/response
  schema, defaults, and scheduling UI behavior.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `meeting-settings-replacement-api`: change the PUT meeting-settings contract
  to use the simplified field set, new defaults, and renamed password field
  semantics.
- `android-meeting-creation`: change Android schedule-meeting request mapping
  and schedule form behavior to align with the new meeting settings fields and
  remove obsolete controls.

## Impact

- Affected backend code in `services/meeting-management` domain, presentation,
  response, persistence, repository mapping, and tests.
- Affected API contract in `openapi/unified-openapi.yaml`, plus generated
  Android OpenAPI DTOs.
- Affected Android code in meeting settings input, repository request building,
  schedule presentation logic, schedule UI layout, and related tests/build
  validation.
- Affected OpenSpec files in `openspec/specs/android-meeting-creation/spec.md`
  and `openspec/specs/meeting-settings-replacement-api/spec.md`.
