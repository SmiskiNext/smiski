## ADDED Requirements

### Requirement: App forwards install and upgrade lifecycle events to the tenant endpoint

The Forge app SHALL subscribe to the `avi:forge:installed:app` and
`avi:forge:upgraded:app` lifecycle events through a Forge `trigger` module bound
to a `function`, and the function SHALL record the installation by calling the
generated `@smiskinext/smiski-ts` `register()` operation whose transport is
Forge Remote to the `meet` backend, so that Atlassian attaches a signed Forge
Invocation Token (FIT) as the `Authorization: Bearer` credential. The function
SHALL map the lifecycle event payload to the SDK `register()` request body (the
installation `id`, optional `installerAccountId`, the `app` object, and the
optional `environment` object) and SHALL send the request through a transport
that also delivers the app system token to the gateway. The function SHALL NOT
attach `X-Tenant-ID`, `X-Account-Id`, or any other client-asserted
tenant/account identity header, and SHALL NOT attach `X-Issue-Id` or
`X-Project-Id` context headers, because a lifecycle event has no issue or
project scope and tenant identity is derived by the gateway from the FIT. On an
SDK result whose `error` is present (a non-success response or an unreachable
backend), the function SHALL fail by throwing so the Forge platform's built-in
event retry applies.

#### Scenario: Install event records the tenant through the SDK over Forge Remote

- **WHEN** the app receives an `avi:forge:installed:app` event
- **THEN** the function issues the request through the SDK `register()`
  operation, whose transport is Forge Remote to the `meet` backend, mapping the
  event payload to the register request body, and on a successful response
  obtains the tenant snapshot in the result `data`

#### Scenario: Major upgrade event also calls register

- **WHEN** the app receives an `avi:forge:upgraded:app` event
- **THEN** the function calls the same SDK `register()` operation with the
  upgrade payload, relying on the backend's idempotent, reactivating upsert to
  refresh the installation, and treats a `200` result as success

#### Scenario: Forge attaches the FIT and the function asserts no identity or context

- **WHEN** the function sends the register request
- **THEN** the request carries only the Forge-attached `Authorization: Bearer`
  FIT for identity, and the function sets neither `X-Tenant-ID`/`X-Account-Id`
  nor `X-Issue-Id`/`X-Project-Id`

#### Scenario: Non-success response triggers Forge retry

- **WHEN** the SDK result for the register request has `error` present because
  the backend returned a non-success status or was unreachable
- **THEN** the function throws so the Forge platform retries the event delivery,
  and no success is reported
