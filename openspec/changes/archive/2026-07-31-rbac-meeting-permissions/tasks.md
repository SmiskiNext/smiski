## 1. Forge App — Manifest

- [x] 1.1 Add `jira:projectPermission` block to `app/manifest.yml` with
      `view-meeting` (key, name, description, category: projects) and
      `edit-meeting` (key, name, description, category: projects)

## 2. Shared Infrastructure — PermissionContext

- [x] 2.1 Create `PermissionContext.java` in the identity package of
      `services/shared` following the same ThreadLocal pattern as
      `AccountContext` — holds `Set<String>` of permission keys, provides
      `setPermissions`, `getPermissions`, `hasPermission(String)`, and `clear`
      static methods
- [x] 2.2 Add `app.security.permissions.require-header` boolean config property
      (default `false`) to the shared identity auto-configuration properties
      class

## 3. Shared Infrastructure — PermissionFilter

- [x] 3.1 Create `PermissionFilter.java` in the identity package of
      `services/shared` — reads `X-Project-Permissions` header, splits on comma,
      trims, binds to `PermissionContext`; if header is absent and
      `require-header=true` returns `403 application/problem+json` with code
      `NOT_AUTHORIZED`; always clears context in `finally`
- [x] 3.2 Register `PermissionFilter` as a `FilterRegistrationBean` in the
      shared identity auto-configuration after `AccountFilter` ← (verify: filter
      order is TenantFilter → AccountFilter → PermissionFilter; context cleared
      after each request)

## 4. Meet Service — Security Config

- [x] 4.1 Update
      `services/meet/src/main/java/io/github/smiskinext/meet/infrastructure/security/SecurityConfig.java`
      to declare `PermissionFilter` bean dependency and ensure it is registered
      in the filter chain after `AccountFilter`

## 5. Meet Service — Controller Permission Checks

- [x] 5.1 Add a private helper method `requirePermission(String permissionKey)`
      to `MeetingController` that reads `PermissionContext` and returns a
      `403 NOT_AUTHORIZED` Problem Details response if the permission is absent
- [x] 5.2 Add `view-meeting` check to `POST /meetings` (list meetings) — call
      `requirePermission("view-meeting")` at the top of the handler after the
      `X-Account-Id` check
- [x] 5.3 Add `view-meeting` check to `GET /meetings/{id}` (get meeting detail)
- [x] 5.4 Add `view-meeting` check to `POST /meetings/{id}:join` (join meeting)
- [x] 5.5 Add `edit-meeting` check to `POST /meetings:instant` (create instant
      meeting)
- [x] 5.6 Add `edit-meeting` check to `POST /meetings:schedule` (schedule
      meeting)
- [x] 5.7 Add `edit-meeting` check to `PUT /meetings/{id}` (update meeting)
- [x] 5.8 Add `edit-meeting` check to `PUT /meetings/{id}/settings` (update
      settings)
- [x] 5.9 Add `edit-meeting` check to `DELETE /meetings/{id}` (delete meeting)
- [x] 5.10 Add `edit-meeting` check to `POST /meetings:batchDelete` (batch
      delete meetings)
- [x] 5.11 Add `edit-meeting` check to `POST /meetings/{id}:end` (end meeting)
- [x] 5.12 Add `edit-meeting` check to `POST /meetings/{id}:cancel` (cancel
      meeting)
- [x] 5.13 Add `edit-meeting` check to `POST /meetings/{id}/invitees` (add
      invitees)
- [x] 5.14 Add `edit-meeting` check to
      `POST /meetings/{id}/invitees:batchDelete` (remove invitees)
- [x] 5.15 Add `edit-meeting` check to
      `POST /meetings/{id}/join-requests:accept` (accept join requests)
- [x] 5.16 Add `edit-meeting` check to
      `POST /meetings/{id}/join-requests:decline` (decline join requests) ←
      (verify: all 17 endpoints check the correct permission; RSVP and webhook
      endpoints have no permission check added)

## 6. Tests — PermissionFilter

- [x] 6.1 `PermissionFilterTest`: header present → `PermissionContext` contains
      expected permissions (covers spec scenario "Header present is parsed and
      bound")
- [x] 6.2 `PermissionFilterTest`: header absent + `require-header=false` →
      filter passes, empty context (covers "Header absent with require-header
      false passes through")
- [x] 6.3 `PermissionFilterTest`: header absent + `require-header=true` → 403
      NOT_AUTHORIZED (covers "Header absent with require-header true is
      rejected")
- [x] 6.4 `PermissionFilterTest`: context cleared after request completes
      (covers "Permission context does not leak across requests") ← (verify:
      filter behaves correctly for all 4 scenarios; no ThreadLocal leak between
      requests)

## 7. Tests — Controller Permission Enforcement

- [x] 7.1 `MeetingControllerTest` (or slice): `GET /meetings/{id}` with empty
      permission context → 403 NOT_AUTHORIZED (covers "Missing view-meeting is
      rejected")
- [x] 7.2 `MeetingControllerTest`: `POST /meetings:instant` with only
      `view-meeting` → 403 NOT_AUTHORIZED (covers "Missing edit-meeting is
      rejected")
- [x] 7.3 `MeetingControllerTest`: `PUT /meetings/{id}` with `edit-meeting` but
      non-host accountId → permission check passes, host-ownership check returns
      403 NOT_OWNER (covers "Correct permission plus non-host ownership still
      fails")
- [x] 7.4 `MeetingControllerTest`:
      `POST /meetings/{id}/invitees/{inviteeId}:accept` with empty permission
      context → no permission rejection (RSVP not gated — covers "RSVP endpoints
      are not permission-gated") ← (verify: permission enforcement scenarios all
      pass; RSVP and webhook endpoints remain ungated)
