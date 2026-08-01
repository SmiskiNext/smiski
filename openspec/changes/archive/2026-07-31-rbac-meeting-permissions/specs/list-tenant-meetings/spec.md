## MODIFIED Requirements

### Requirement: List meetings search endpoint

The system SHALL expose `POST /api/1/meetings` that returns meetings belonging
to the caller's tenant. The endpoint SHALL use the HTTP `POST` method (not `GET`
or `QUERY`) and accept a JSON request body in which every field is optional:
`creatorId` (string), `search` (string), `statuses` (array of
`SCHEDULED|RUNNING|COMPLETED|CANCELED`), `issueKey` (string), `sort`
(`CREATED_AT` or `START_TIME`), `pageSize` (integer), and `pageToken` (opaque
string). The endpoint SHALL require the `view-meeting` project permission — if
the caller's permission context does not contain `view-meeting`, the endpoint
SHALL reject the request with `403 application/problem+json` and code
`NOT_AUTHORIZED` before executing the use case. When the body is absent or
empty, the endpoint SHALL behave as if all fields were omitted. The tenant SHALL
be resolved from the `X-Tenant-ID` header, and the request SHALL require an
`X-Account-Id` header.

#### Scenario: Empty body returns tenant meetings with defaults

- **WHEN** a client sends `POST /api/1/meetings` with a valid `X-Tenant-ID`, an
  `X-Account-Id` header, an empty JSON body `{}`, and the caller has
  `view-meeting` permission
- **THEN** the response is `200 OK` containing meetings from that tenant ordered
  by created-at descending with default page size

#### Scenario: Missing view-meeting permission is rejected

- **WHEN** a caller without `view-meeting` sends `POST /api/1/meetings`
- **THEN** the response is `403 application/problem+json` with code
  `NOT_AUTHORIZED` and no meeting list is returned
