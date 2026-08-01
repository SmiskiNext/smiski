## Why

The `meet` service currently performs only host-ownership checks (e.g., "only
the host may cancel this meeting") with no project-level permission gating. Any
authenticated Jira user in a tenant can create, view, or manage meetings
regardless of their role in the project. Jira's project permission model
(`jira:projectPermission`) must be enforced so that organisations can control
who is allowed to view meetings and who can create or manage them.

## What Changes

- Add two Jira project permission declarations to the Forge app manifest:
  `view-meeting` and `edit-meeting`.
- Introduce `PermissionContext` (ThreadLocal holder) and `PermissionFilter`
  (header reader) in `services/shared`, following the same pattern as the
  existing `AccountContext`/`AccountFilter`.
- Add a configurable property `app.security.permissions.require-header` (default
  `false`) — when `false`, a missing `X-Project-Permissions` header is treated
  as an empty permission set and passes through (dev-friendly); when `true`,
  absence is treated as 403.
- Register `PermissionFilter` in `services/meet` after `AccountFilter`.
- Enforce `view-meeting` on all read/join endpoints and `edit-meeting` on all
  write/lifecycle endpoints in `MeetingController`.
- RSVP endpoints (`:accept`, `:decline`, `:tentative`) and the LiveKit webhook
  endpoint are **not** gated — deferred.
- Host-ownership checks in the application layer are **not** changed.

## Capabilities

### New Capabilities

- `project-permission-enforcement`: Header-based RBAC gate in the `meet` service
  — reads `X-Project-Permissions` from the request, binds it to a ThreadLocal,
  and enforces `view-meeting` / `edit-meeting` per endpoint with configurable
  header-required mode.

### Modified Capabilities

- `create-instant-meeting`: Adds `edit-meeting` permission requirement before
  the use case is invoked.
- `create-schedule-meeting`: Adds `edit-meeting` permission requirement before
  the use case is invoked.
- `get-meeting-detail`: Adds `view-meeting` permission requirement before the
  use case is invoked.
- `list-tenant-meetings`: Adds `view-meeting` permission requirement before the
  use case is invoked.
- `join-meeting`: Adds `view-meeting` permission requirement before the use case
  is invoked.
- `update-meeting`: Adds `edit-meeting` permission requirement before the use
  case is invoked.
- `update-meeting-settings`: Adds `edit-meeting` permission requirement before
  the use case is invoked.
- `delete-meeting`: Adds `edit-meeting` permission requirement before the use
  case is invoked.
- `cancel-meeting`: Adds `edit-meeting` permission requirement before the use
  case is invoked.
- `add-meeting-invitees`: Adds `edit-meeting` permission requirement before the
  use case is invoked.
- `remove-meeting-invitees`: Adds `edit-meeting` permission requirement before
  the use case is invoked.
- `host-join-decision`: Adds `edit-meeting` permission requirement before the
  use case is invoked.

## Impact

- **`app/manifest.yml`**: new `jira:projectPermission` entries for
  `view-meeting` and `edit-meeting`.
- **`services/shared`**: two new classes (`PermissionContext`,
  `PermissionFilter`) in the identity package; new config property under
  `app.security.permissions`.
- **`services/meet`**: `SecurityConfig` updated to register the new filter;
  `MeetingController` updated with permission checks on 17 endpoints; new
  `MeetingErrorCode.PERMISSION_DENIED` (or reuse existing `NOT_AUTHORIZED`)
  surfaced as `403`.
- **No database changes**, no new Kafka topics, no API contract changes (error
  shape is existing `application/problem+json`).
- **Tests**: `PermissionFilterTest` (unit), `MeetingController` slice tests for
  permission enforcement (new cases only).
