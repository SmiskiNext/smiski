## Context

The `meet` service currently relies on the API gateway to inject authenticated
identity (`X-Account-Id`, `X-Tenant-Id`) but performs no project-level
permission checks. Any authenticated user in a tenant can create, view, or
manage meetings. The Jira Forge platform supports project permissions
(`jira:projectPermission`) that let organizations control who can view and
manage meetings. This design adds header-based RBAC enforcement in the backend
service so that the gateway (once built) can inject verified permissions and the
service can gate endpoints accordingly.

The gateway component does not yet exist — this design prepares the backend to
consume permissions when the gateway is deployed. A configurable
`require-header` property allows dev/test environments to operate without the
gateway while production can enforce strict permission checks.

## Goals / Non-Goals

**Goals:**

- Declare `view-meeting` and `edit-meeting` Jira project permissions in the
  Forge app manifest
- Add shared infrastructure (`PermissionContext`, `PermissionFilter`) in
  `services/shared` to read and bind permissions from the
  `X-Project-Permissions` header
- Enforce `view-meeting` on read/join endpoints and `edit-meeting` on
  write/lifecycle endpoints in `MeetingController`
- Provide a configurable toggle (`app.security.permissions.require-header`,
  default `false`) for dev vs. production permission enforcement
- Return `403` with RFC 9457 Problem Details when permission checks fail
- Preserve existing host-ownership checks unchanged

**Non-Goals:**

- Implementing the API gateway or FIT verification logic (deferred to Phase 2)
- Gating RSVP endpoints (`:accept`, `:decline`, `:tentative`) or the LiveKit
  webhook endpoint (deferred)
- Replacing host-ownership checks with permission-only checks
- Supporting external invitee RSVP flows (deferred to a separate feature)
- Database changes or new Kafka topics

## Decisions

### Decision 1: Header name and format

**Chosen:** `X-Project-Permissions: view-meeting,edit-meeting` (comma-separated
list)

**Rationale:** Consistent with existing `X-Account-Id` and `X-Tenant-Id` naming.
Comma-separated format is simple to parse, forward-compatible (new permissions
append to the list), and readable for debugging. The header name makes the scope
explicit (project-level, not global).

**Alternatives considered:**

- `X-Permissions` — too generic, could be confused with global permissions
- JSON array in header — over-engineered for a simple string list
- Multiple headers (`X-View-Meeting`, `X-Edit-Meeting`) — verbose, doesn't scale

### Decision 2: `edit-meeting` semantics relative to host-ownership

**Chosen:** `edit-meeting` is required to create meetings and to participate in
meeting lifecycle (cancel, end, add invitees). Host-ownership checks in the
application layer remain unchanged. A user must have `edit-meeting` **and** be
the host to modify a specific meeting.

**Rationale:** Separation of concerns. `edit-meeting` gates whether a user can
interact with the meeting system at all (create, manage lifecycle).
Host-ownership gates which specific meetings they can modify. This prevents
privilege escalation (user with `edit-meeting` can't cancel another user's
meeting) while still enforcing project-level access control.

**Alternatives considered:**

- `edit-meeting` replaces host checks → allows admin-level access but breaks the
  current owner-only model
- `edit-meeting` only for creation → inconsistent; lifecycle operations would
  remain ungated

### Decision 3: Configurable header requirement

**Chosen:** `app.security.permissions.require-header` (default `false`) — when
`false`, a missing `X-Project-Permissions` header is treated as an empty
permission set and the filter passes through; when `true`, absence triggers
`403`.

**Rationale:** Dev/test environments do not yet have a gateway. Default `false`
keeps the service functional during development. Production deployments set
`require-header=true` to enforce strict gating. This mirrors the existing
`TenantFilter` fallback pattern (defaults to tenant `"system"` when header is
absent).

**Alternatives considered:**

- Always require header → breaks all dev/test until gateway exists
- Always allow missing header → unsafe for production, easy to misconfigure
- Environment-based logic without config → implicit behavior, harder to audit

### Decision 4: Error code for permission denial

**Chosen:** Reuse existing `MeetingErrorCode.NOT_AUTHORIZED` → maps to `403`
Problem Details

**Rationale:** The `NOT_AUTHORIZED` code already exists and is semantically
correct for permission denial. Creating a new `PERMISSION_DENIED` code would be
redundant. The `detail` field in Problem Details can distinguish between "not
the host" and "missing permission" if needed for debugging.

**Alternatives considered:**

- New `PERMISSION_DENIED` code → adds code without semantic value
- `403` without Problem Details → violates existing error contract

### Decision 5: Filter registration order

**Chosen:** `PermissionFilter` runs **after** `AccountFilter` in the Spring
Security filter chain.

**Rationale:** `AccountContext` must be populated before `PermissionContext` so
that permission checks can reference the current account if needed. The existing
filter order is `TenantFilter` → `AccountFilter` → (proposed)
`PermissionFilter`.

### Decision 6: RSVP and webhook endpoints deferred

**Chosen:** RSVP endpoints (`:accept`, `:decline`, `:tentative`) and the LiveKit
webhook endpoint are **not** gated in this change.

**Rationale:** RSVP flows involve external invitees who may not have project
permissions. The correct permission model for external RSVP is unclear (magic
link? token-based?) and is deferred to a separate feature. LiveKit webhooks are
server-to-server calls authenticated by HMAC signature, not user permissions.

## Risks / Trade-offs

**[Risk]** Gateway not deployed → permission checks bypassed in dev/test
**Mitigation:** Default `require-header=false` makes this explicit. Production
config sets `require-header=true` and will fail-safe (deny requests without
header).

**[Risk]** Permission header spoofing if gateway is bypassed **Mitigation:**
Production network topology must ensure backend services are not exposed
directly to external clients. This is a deployment constraint, not a code-level
mitigation.

**[Risk]** Adding `edit-meeting` requirement could break existing automated
workflows or integrations that create meetings **Mitigation:** Jira admins must
assign `edit-meeting` permission to relevant users/roles during deployment. This
is a one-time configuration step. The capability spec includes a migration note.

**[Risk]** RSVP deferral means external invitees cannot respond even if they
have a valid invite token **Mitigation:** This is by design. RSVP permission
model is unclear and is explicitly out of scope. A follow-up feature will
address external RSVP flows.

**[Trade-off]** `edit-meeting` + host-ownership dual-check is more restrictive
than pure RBAC (admin can't cancel any meeting) **Benefit:** Preserves existing
owner-only semantics. **Cost:** More complex mental model (two checks, not one).
Judged acceptable because meeting ownership is a strong invariant in the current
system.

## Migration Plan

1. **Deploy shared infrastructure** (`PermissionContext`, `PermissionFilter`)
   with `require-header=false` default
2. **Deploy `meet` service** with permission checks enabled but header not
   required
3. **Test in dev/staging** without gateway (permission checks pass through)
4. **Deploy API gateway** (Phase 2) with FIT verification and
   `X-Project-Permissions` injection
5. **Enable strict mode** in production: set `require-header=true` in config
6. **Communicate to Jira admins**: assign `view-meeting` and `edit-meeting`
   permissions to appropriate roles

**Rollback:** Revert to previous `meet` service version. The Forge manifest
change (adding permission declarations) is additive and does not break existing
functionality if the backend does not enforce them.

## Open Questions

None — all decisions are locked based on conversation context.
