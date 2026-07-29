## MODIFIED Requirements

### Requirement: Invitee list in meeting detail

The response SHALL include an `invitees` array listing the meeting's active
(non-removed) invitees. Each entry SHALL carry `id` (the invitee identity used
to remove the invitee via `POST /api/1/meetings/{id}/invitees:batchDelete`),
`accountId`, `email`, `displayName`, `status` (the RSVP status), `invitedAt`,
and `respondedAt` (nullable when the invitee has not yet responded).

#### Scenario: Invitees are listed with their RSVP status

- **WHEN** a meeting has invitees and a client retrieves it
- **THEN** the `invitees` array contains one entry per active invitee, each with
  its `id`, `accountId`, `email`, `displayName`, `status`, `invitedAt`, and
  `respondedAt`

#### Scenario: Invitee entry exposes its removal id

- **WHEN** a client retrieves a meeting that has active invitees
- **THEN** each invitee entry includes a non-empty `id` that can be submitted to
  `POST /api/1/meetings/{id}/invitees:batchDelete`

#### Scenario: Meeting with no invitees returns an empty invitee list

- **WHEN** a meeting has no active invitees and a client retrieves it
- **THEN** the `invitees` array is empty and the request still succeeds with
  `200 OK`
