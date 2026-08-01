## MODIFIED Requirements

### Requirement: Get meeting detail endpoint

The system SHALL expose `GET /api/1/meetings/{id}` that returns a single meeting
belonging to the caller's tenant together with its invitee list and its joined
participant list in one response body. The endpoint SHALL use the HTTP `GET`
method, resolve the tenant from the `X-Tenant-ID` header, and require an
`X-Account-Id` header. The endpoint SHALL require the `view-meeting` project
permission — if the caller's permission context does not contain `view-meeting`,
the endpoint SHALL reject the request with `403 application/problem+json` and
code `NOT_AUTHORIZED` before executing the use case. Any authenticated account
within the meeting's tenant who has `view-meeting` permission SHALL be permitted
to read the meeting; access SHALL NOT be restricted to the host. The successful
response SHALL be `200 OK` with the representation returned directly without a
wrapping envelope, and SHALL NOT include the tenant identifier.

#### Scenario: Existing meeting is returned with its people

- **WHEN** a client sends `GET /api/1/meetings/{id}` with a valid `X-Tenant-ID`
  and `X-Account-Id` for a meeting that exists in that tenant and the caller has
  `view-meeting` permission
- **THEN** the response is `200 OK` containing the meeting snapshot, an
  `invitees` array, and a `participants` array

#### Scenario: Missing view-meeting permission is rejected

- **WHEN** a caller without `view-meeting` sends `GET /api/1/meetings/{id}`
- **THEN** the response is `403 application/problem+json` with code
  `NOT_AUTHORIZED` and no meeting detail is returned

#### Scenario: A non-host tenant member may read the meeting

- **WHEN** an authenticated account that is not the meeting host requests
  `GET /api/1/meetings/{id}` for a meeting in its own tenant and has
  `view-meeting` permission
- **THEN** the response is `200 OK` with the meeting detail, and the request is
  not rejected for lack of host ownership

#### Scenario: Missing account header is rejected

- **WHEN** a client sends `GET /api/1/meetings/{id}` without an `X-Account-Id`
  header
- **THEN** the request fails with a `400` Problem Details response and no
  meeting detail is returned
