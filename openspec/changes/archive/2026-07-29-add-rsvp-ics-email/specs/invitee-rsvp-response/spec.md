## ADDED Requirements

### Requirement: Invitee self-response endpoints

The meet service SHALL expose three action endpoints for an invitee to respond
to their own invitation:
`POST /api/1/meetings/{id}/invitees/{inviteeId}:accept`,
`POST /api/1/meetings/{id}/invitees/{inviteeId}:decline`, and
`POST /api/1/meetings/{id}/invitees/{inviteeId}:tentative`. The acting account
SHALL be resolved from the configured account header and the tenant from the
tenant context. A successful call SHALL return `200 OK` with the full snapshot
of the responding invitee, carrying `id`, `accountId`, `email`, `displayName`,
`role`, `status`, `invitedAt`, and `respondedAt`.

#### Scenario: Invitee accepts their invitation

- **WHEN** the invitee sends a valid `:accept` request with the account header
  and the acting account owns the target invitation
- **THEN** the system sets the invitee status to `ACCEPTED`, records
  `respondedAt`, publishes an invitee-accepted event, and returns `200 OK` with
  the updated invitee snapshot

#### Scenario: Invitee declines their invitation

- **WHEN** the invitee sends a valid `:decline` request that the current status
  permits
- **THEN** the system sets the status to `DECLINED`, records `respondedAt`,
  publishes an invitee-declined event, and returns `200 OK` with the snapshot

#### Scenario: Invitee marks their invitation tentative

- **WHEN** the invitee sends a valid `:tentative` request that the current
  status permits
- **THEN** the system sets the status to `TENTATIVE`, records `respondedAt`,
  publishes an invitee-tentative event, and returns `200 OK` with the snapshot

#### Scenario: Missing account identity is rejected

- **WHEN** a response request does not contain the configured account header
- **THEN** the system returns `400` Problem Details, changes no invitee state,
  and publishes no event

#### Scenario: Unknown meeting or invitee is rejected

- **WHEN** the request references a meeting id or invitee id that does not exist
  in the tenant
- **THEN** the system returns `404` Problem Details, changes no state, and
  publishes no event

### Requirement: Response ownership authorization

The system SHALL authorize a response only when the acting account matches the
target invitee's own account. A request by any account other than the invitee's
owner SHALL be rejected with an RFC 9457 authorization Problem Details response
and HTTP `403`, SHALL change no invitee state, and SHALL publish no event.

#### Scenario: Non-owner response is rejected

- **WHEN** an account that does not own the target invitation sends an
  `:accept`, `:decline`, or `:tentative` request
- **THEN** the system returns `403` Problem Details, changes no invitee state,
  and publishes no event

#### Scenario: Owner response is authorized

- **WHEN** the account that owns the target invitation sends a response request
- **THEN** the system proceeds to evaluate the status transition and, if valid,
  applies the response

### Requirement: Invitee response status transitions

The system SHALL enforce the invitation status-transition rules on every
response: `NEEDS_ACTION` MAY transition to `ACCEPTED`, `DECLINED`, or
`TENTATIVE`; `TENTATIVE` MAY transition to `ACCEPTED` or `DECLINED`; `ACCEPTED`
MAY transition to `DECLINED` or `TENTATIVE`; `DECLINED` is terminal. A response
that would violate these rules, or a response targeting an invitee that has been
removed, SHALL be rejected with an invalid-transition Problem Details response
and HTTP `409`, SHALL change no state, and SHALL publish no event.

#### Scenario: Declined invitation cannot be changed

- **WHEN** the invitee attempts any response on an invitation whose status is
  already `DECLINED`
- **THEN** the system returns `409` invalid-transition Problem Details, changes
  no state, and publishes no event

#### Scenario: Response to a removed invitation is rejected

- **WHEN** the invitee attempts a response on an invitation that has been
  soft-removed
- **THEN** the system returns `409` invalid-transition Problem Details, changes
  no state, and publishes no event

#### Scenario: Tentative-to-accepted is permitted

- **WHEN** the invitee sends `:accept` on an invitation whose current status is
  `TENTATIVE`
- **THEN** the transition succeeds, the status becomes `ACCEPTED`, and an
  invitee-accepted event is published
