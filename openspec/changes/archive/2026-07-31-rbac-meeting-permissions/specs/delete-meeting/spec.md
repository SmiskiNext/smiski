## MODIFIED Requirements

### Requirement: Host-only single meeting soft-delete endpoint

The system SHALL expose `DELETE /api/1/meetings/{id}` for soft-deleting a single
meeting. The endpoint SHALL require the `edit-meeting` project permission — if
the caller's permission context does not contain `edit-meeting`, the endpoint
SHALL reject the request with `403 application/problem+json` and code
`NOT_AUTHORIZED` before executing the use case. The acting account SHALL be
resolved from the configured account header, the tenant SHALL be resolved from
the tenant context, and only the meeting host SHALL be authorized to delete the
meeting. A successful deletion SHALL mark the meeting soft-deleted and return
`200 OK` with the full snapshot of the deleted meeting, including the deletion
timestamp and deleting account.

#### Scenario: Host deletes an eligible meeting

- **WHEN** the host sends `DELETE /api/1/meetings/{id}` with the account header
  for a meeting whose status is `SCHEDULED`, `COMPLETED`, or `CANCELED` and has
  `edit-meeting` permission
- **THEN** the system records the deletion timestamp and the deleting account,
  the meeting no longer appears in tenant meeting lists, and the response is
  `200 OK` carrying the deleted meeting snapshot (including the deletion
  timestamp and deleting account)

#### Scenario: Missing edit-meeting permission is rejected

- **WHEN** a caller without `edit-meeting` sends `DELETE /api/1/meetings/{id}`
- **THEN** the response is `403 application/problem+json` with code
  `NOT_AUTHORIZED` and the meeting is not deleted

#### Scenario: Non-host deletion is rejected

- **WHEN** an account that is not the meeting host sends a delete request even
  with `edit-meeting` permission
- **THEN** the system returns an RFC 9457 Problem Details response with the
  authorization error code and the meeting is not deleted
