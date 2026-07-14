## MODIFIED Requirements

### Requirement: Successful response body

A successful response SHALL return the resource representation directly as the
response body without any wrapping envelope. The HTTP status SHALL reflect the
operation outcome: `200 OK` for retrievals and updates that return a body,
`201 Created` with a `Location` header for resource creation, and
`204 No Content` for operations that produce no body.

For mutation operations that return a body (POST and PUT), the representation
SHALL be the **full domain snapshot** of the affected resource — every field of
the resource's aggregate — with two exclusions: the tenant identifier (which the
client already supplies via the `X-Tenant-ID` header and which therefore SHALL
NOT be echoed in the body) and any field explicitly marked sensitive. A field
marked sensitive SHALL be omitted from the response body even though it MAY be
present in the resource's lifecycle events. The tenant identifier SHALL remain
available where it is structurally required, such as the `Location` header of a
`201 Created` response.

#### Scenario: Retrieval returns the raw representation

- **WHEN** a client successfully retrieves a resource
- **THEN** the response status is `200 OK` and the body is the resource
  representation itself, not wrapped in an envelope

#### Scenario: Creation returns Location header

- **WHEN** a client successfully creates a resource
- **THEN** the response status is `201 Created`, the `Location` header points to
  the newly created resource, and the body is the created representation

#### Scenario: Mutation response returns the full snapshot without the tenant id

- **WHEN** a client successfully performs a POST or PUT that returns a body
- **THEN** the body contains every non-excluded field of the resource aggregate
  and does not contain the tenant identifier, because the client supplied it via
  `X-Tenant-ID`

#### Scenario: Sensitive field omitted from response but retained in events

- **WHEN** a resource has a field marked sensitive and the client performs a
  mutation that returns a body
- **THEN** that field is absent from the response body while still being carried
  in the resource's lifecycle events

#### Scenario: No-body operation returns 204

- **WHEN** a successful operation produces no representation
- **THEN** the response status is `204 No Content` with an empty body
