# ui-backend-interaction (delta: instant-meeting-forge-remote)

## MODIFIED Requirements

### Requirement: Instant meeting creation through the backend SDK

The app SHALL create instant meetings by calling the `meet` backend through
Forge Remote from the Custom UI, so that Atlassian attaches a signed Forge
Invocation Token (FIT) as the `Authorization: Bearer` credential on the outbound
request. The app SHALL NOT attach `X-Tenant-ID`, `X-Account-Id`, or any other
client-asserted tenant/account identity header; deriving tenant and account
identity from the verified FIT is the gateway's responsibility and is out of
scope for the app. The instant-create flow SHALL NOT use the in-memory mock; it
SHALL depend on a reachable backend. On success the app SHALL receive the
created meeting snapshot and the host's LiveKit access details.

#### Scenario: Instant creation calls the backend via Forge Remote

- **WHEN** the frontend creates an instant meeting with a valid payload
- **THEN** the app issues the create request through Forge Remote to the `meet`
  backend and, on a successful response, obtains the created meeting snapshot
  together with the host's LiveKit token and room name

#### Scenario: Forge attaches the FIT and the app asserts no identity headers

- **WHEN** the app sends an instant-create request to the backend
- **THEN** the request carries only the Forge-attached `Authorization: Bearer`
  FIT for identity, and the app does not set `X-Tenant-ID` or `X-Account-Id`
  itself

#### Scenario: Backend failure is surfaced, not mocked

- **WHEN** the backend rejects the instant-create request or is unreachable
- **THEN** the app surfaces the error to the caller and no meeting is created,
  and the flow does not fall back to the in-memory mock
