## MODIFIED Requirements

### Requirement: Extract accountId from FIT principal claim

The authorization service SHALL extract the accountId (user identifier) from the
FIT `principal` claim when present, normalising the Forge colon-prefixed and ARI
forms to a bare account identifier. The `principal` claim is optional: Atlassian
omits it for invocations that run without a user in session (for example, a
Forge app-lifecycle trigger or pre-uninstall function). When `principal` is
absent or empty, the service SHALL NOT reject the request on that basis; it
SHALL resolve accountId to an empty string and continue parsing the remaining
claims, including cloudId.

#### Scenario: AccountId extracted from Forge colon-prefixed principal

- **WHEN** FIT contains
  `"principal": "655362:312d3308-8954-42b0-aa38-771a10c88656"`
- **THEN** system extracts accountId as `312d3308-8954-42b0-aa38-771a10c88656`

#### Scenario: AccountId extracted from ARI principal

- **WHEN** FIT contains
  `"principal": "ari:cloud:identity::user/5f8c9d1234567890abcdef01"`
- **THEN** system extracts accountId as `5f8c9d1234567890abcdef01`

#### Scenario: AccountId extracted from bare principal

- **WHEN** FIT contains `"principal": "5f8c9d1234567890abcdef01"`
- **THEN** system extracts accountId as `5f8c9d1234567890abcdef01`

#### Scenario: Missing principal yields an empty accountId, not a rejection

- **WHEN** FIT does not contain a `principal` claim, and a valid cloudId can be
  resolved from `context.cloudId` or `app.apiBaseUrl`
- **THEN** parsing succeeds, system resolves accountId as an empty string, and
  the request is not rejected for a missing principal

#### Scenario: Empty principal yields an empty accountId, not a rejection

- **WHEN** FIT contains `"principal": ""`, and a valid cloudId can be resolved
- **THEN** parsing succeeds and system resolves accountId as an empty string

### Requirement: Parse FIT payload without signature verification

The authorization service SHALL parse the FIT JWT payload to extract claims
without performing signature verification, because the signature is already
validated by the gateway.

#### Scenario: FIT payload parsed from validated token

- **WHEN** FIT has been validated by the gateway JWT filter
- **THEN** the authorization service decodes the JWT payload and extracts claims

#### Scenario: Base64 decoding failure handled

- **WHEN** FIT payload is not valid base64
- **THEN** system returns HTTP 400 Bad Request with message "Invalid token
  format"

#### Scenario: Missing cloudId claims reported

- **WHEN** FIT contains neither `context.cloudId` nor `app.apiBaseUrl`,
  regardless of whether `principal` is present
- **THEN** system returns HTTP 400 Bad Request with a message indicating cloudId
  could not be resolved; the message SHALL NOT cite a missing `principal` claim,
  since `principal` is optional and never contributes to this rejection

#### Scenario: Invalid claim type reported

- **WHEN** FIT contains `"principal": 123` (number instead of string)
- **THEN** system returns HTTP 400 Bad Request with message "Invalid principal
  claim type"

### Requirement: Return allow decision with injected identity headers

The authorization service SHALL return a `CheckResponse` containing an
`OkHttpResponse` with `X-Tenant-ID`, `X-Account-Id` and `X-Project-Permissions`
headers when authorization succeeds. `X-Account-Id` SHALL be injected with an
empty value when the FIT carried no `principal` claim, rather than blocking the
response — a resolvable cloudId is sufficient for an allow decision.

#### Scenario: Success response carries OK status

- **WHEN** authorization succeeds
- **THEN** CheckResponse contains `Status{Code: Code_OK}` and an
  `OkHttpResponse`

#### Scenario: X-Tenant-ID carries cloudId

- **WHEN** FIT yields cloudId `abc123-def456`
- **THEN** the response injects header `X-Tenant-ID: abc123-def456`

#### Scenario: X-Account-Id carries accountId

- **WHEN** FIT yields accountId `5f8c9d1234567890abcdef01`
- **THEN** the response injects header `X-Account-Id: 5f8c9d1234567890abcdef01`

#### Scenario: Multiple permissions injected as comma-separated list

- **WHEN** the user has permissions `["view-meeting", "edit-meeting"]`
- **THEN** the response injects header
  `X-Project-Permissions: view-meeting,edit-meeting`

#### Scenario: Single permission injected

- **WHEN** the user has permissions `["view-meeting"]`
- **THEN** the response injects header `X-Project-Permissions: view-meeting`

#### Scenario: No permissions results in empty header

- **WHEN** the user has no permissions
- **THEN** the response injects header `X-Project-Permissions` with an empty
  value

#### Scenario: Headers added as HeaderValueOption with append false

- **WHEN** building the OkHttpResponse
- **THEN** each header is added as
  `HeaderValueOption{Header: {Key, Value}, Append: false}` so the backend
  receives exactly one instance of each

#### Scenario: Allow decision with empty accountId when principal is absent

- **WHEN** a request carries a FIT with a resolvable cloudId but no `principal`
  claim (a user-less app-lifecycle invocation)
- **THEN** the service returns an allow decision, injects
  `X-Tenant-ID: <cloudId>`, and injects `X-Account-Id` with an empty value

### Requirement: Return deny decision on authorization failure

The authorization service SHALL return a `CheckResponse` with a
`DeniedHttpResponse` when authorization fails, and the request SHALL NOT reach
any backend service.

#### Scenario: Deny response with 403 status

- **WHEN** authorization fails due to missing permissions
- **THEN** the service returns
  `CheckResponse{Status: PERMISSION_DENIED, HttpResponse: DeniedHttpResponse{Status: 403}}`

#### Scenario: Deny response with error message

- **WHEN** authorization fails
- **THEN** DeniedHttpResponse includes body
  `{"error":"authorization_failed","message":"<reason>"}`

#### Scenario: Deny response with 401 for invalid token

- **WHEN** authorization fails due to an unparseable or invalid token
- **THEN** DeniedHttpResponse includes status 401 instead of 403

#### Scenario: Missing cloudId prevents header injection

- **WHEN** FIT does not yield a valid cloudId
- **THEN** the request is denied and no identity headers are injected,
  regardless of whether `principal`/accountId is present

#### Scenario: Valid cloudId with absent accountId is not denied

- **WHEN** FIT yields a valid cloudId but no `principal` claim, so accountId
  resolves to an empty string
- **THEN** the request is NOT denied on that basis; the service proceeds to the
  context-header check and, absent `X-Issue-Id`/`X-Project-Id`, returns an allow
  decision with empty permissions
