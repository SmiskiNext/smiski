# project-permission-enforcement Specification

## Purpose

TBD - created by archiving change rbac-meeting-permissions. Update Purpose after
archive.

## Requirements

### Requirement: Project permission header binding

The system SHALL read the `X-Project-Permissions` request header, parse it as a
comma-separated list of permission keys, and bind the result to a request-scoped
`PermissionContext` (ThreadLocal) via a shared servlet filter. The filter SHALL
run after `AccountFilter` in the Spring Security filter chain. The context SHALL
be cleared at the end of each request. When the header is absent and
`app.security.permissions.require-header` is `false` (the default), the filter
SHALL bind an empty permission set and allow the request to proceed. When the
header is absent and `app.security.permissions.require-header` is `true`, the
filter SHALL reject the request with `403 application/problem+json` and code
`NOT_AUTHORIZED`.

#### Scenario: Header present is parsed and bound

- **WHEN** a request arrives with
  `X-Project-Permissions: view-meeting,edit-meeting`
- **THEN** `PermissionContext` contains both `view-meeting` and `edit-meeting`
  for the duration of that request and is cleared after the request completes

#### Scenario: Header absent with require-header false passes through

- **WHEN** a request arrives without `X-Project-Permissions` and
  `app.security.permissions.require-header` is `false`
- **THEN** `PermissionContext` contains an empty permission set and the request
  is not rejected by the filter

#### Scenario: Header absent with require-header true is rejected

- **WHEN** a request arrives without `X-Project-Permissions` and
  `app.security.permissions.require-header` is `true`
- **THEN** the filter rejects the request with `403 application/problem+json`
  and code `NOT_AUTHORIZED` before it reaches the controller

#### Scenario: Permission context does not leak across requests

- **WHEN** a request carrying `X-Project-Permissions: edit-meeting` completes
  and a subsequent request arrives without the header
- **THEN** the subsequent request does not observe the previous request's
  permission set

### Requirement: Permission enforcement on meeting endpoints

The `meet` service SHALL enforce project-level permissions on each API endpoint
before executing the use case. An endpoint requiring `view-meeting` SHALL reject
the request with `403 application/problem+json` and code `NOT_AUTHORIZED` if the
caller's `PermissionContext` does not contain `view-meeting`. An endpoint
requiring `edit-meeting` SHALL reject the request with
`403 application/problem+json` and code `NOT_AUTHORIZED` if the caller's
`PermissionContext` does not contain `edit-meeting`. Permission checks SHALL run
after the `X-Account-Id` header check and before any use case is executed. Host-
ownership checks in the application layer SHALL remain unchanged; a caller must
therefore satisfy both the project permission and the host-ownership check to
perform host-only operations.

**Endpoint permission mapping:**

| Permission     | Endpoints                                                                                                                                                                                                                                                                                                                                                                                     |
| -------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `view-meeting` | `POST /meetings` (list), `GET /meetings/{id}`, `POST /meetings/{id}:join`                                                                                                                                                                                                                                                                                                                     |
| `edit-meeting` | `POST /meetings:instant`, `POST /meetings:schedule`, `PUT /meetings/{id}`, `PUT /meetings/{id}/settings`, `DELETE /meetings/{id}`, `POST /meetings:batchDelete`, `POST /meetings/{id}:end`, `POST /meetings/{id}:cancel`, `POST /meetings/{id}/invitees`, `POST /meetings/{id}/invitees:batchDelete`, `POST /meetings/{id}/join-requests:accept`, `POST /meetings/{id}/join-requests:decline` |
| None           | `POST /meetings/{id}/invitees/{inviteeId}:accept`, `POST /meetings/{id}/invitees/{inviteeId}:decline`, `POST /meetings/{id}/invitees/{inviteeId}:tentative`, `POST /webhooks/livekit`                                                                                                                                                                                                         |

#### Scenario: Missing view-meeting is rejected

- **WHEN** a caller with no project permissions sends `GET /meetings/{id}`
- **THEN** the response is `403 application/problem+json` with code
  `NOT_AUTHORIZED` and the use case is not invoked

#### Scenario: Missing edit-meeting is rejected

- **WHEN** a caller with only `view-meeting` sends `POST /meetings:instant`
- **THEN** the response is `403 application/problem+json` with code
  `NOT_AUTHORIZED` and no meeting is created

#### Scenario: Correct permission plus non-host ownership still fails

- **WHEN** a caller has `edit-meeting` but is not the host of the meeting and
  sends `PUT /meetings/{id}`
- **THEN** the permission check passes but the application layer rejects the
  request with `403 application/problem+json` with the host-ownership error code

#### Scenario: Correct permission plus host ownership succeeds

- **WHEN** a caller has `edit-meeting` and is the host of the meeting and sends
  `PUT /meetings/{id}`
- **THEN** the request proceeds and the meeting is updated

#### Scenario: RSVP endpoints are not permission-gated

- **WHEN** a caller with no project permissions sends
  `POST /meetings/{id}/invitees/{inviteeId}:accept`
- **THEN** the request is not rejected by the permission check (existing
  ownership check applies)

### Requirement: Jira project permission declarations in Forge manifest

The Forge app manifest SHALL declare the `view-meeting` and `edit-meeting`
project permissions under `jira:projectPermission` so that Jira administrators
can assign them to roles and users within a project.

| Key            | Name         | Description                                                                                             | Category   |
| -------------- | ------------ | ------------------------------------------------------------------------------------------------------- | ---------- |
| `view-meeting` | View Meeting | View meeting info and history, view meeting detail, join a running meeting, view recording if available | `projects` |
| `edit-meeting` | Edit Meeting | Create, schedule, start, edit, cancel, and end meetings; start/stop recording                           | `projects` |

#### Scenario: Manifest declares both permissions

- **WHEN** the Forge app is installed in a Jira site
- **THEN** a Jira administrator can find `view-meeting` and `edit-meeting` in
  the project permission scheme and assign them to roles
