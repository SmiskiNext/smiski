## ADDED Requirements

### Requirement: App forwards the pre-uninstall lifecycle event to the tenant endpoint

The Forge app SHALL register a `preUninstall` module bound to a `function`, and
the function SHALL record the uninstall by calling the generated
`@smiskinext/smiski-ts` `uninstall()` operation (HTTP `DELETE`) whose transport
is Forge Remote to the `meet` backend, so that Atlassian attaches a signed Forge
Invocation Token (FIT) as the `Authorization: Bearer` credential. The function
SHALL send the request through a transport that also delivers the app system
token to the gateway, and SHALL NOT attach `X-Tenant-ID`, `X-Account-Id`,
`X-Issue-Id`, or `X-Project-Id`, because a lifecycle event has no issue or
project scope and tenant identity is derived by the gateway from the FIT. Given
the platform's pre-uninstall invocation budget (single attempt, no retry, a
bounded number of seconds after which the uninstallation proceeds regardless),
the function SHALL treat both a `200` result (tenant marked uninstalled, or
already uninstalled) and a `404` result (no tenant row exists) as terminal
success, and SHALL NOT throw for either outcome.

#### Scenario: Pre-uninstall event records the uninstall through the SDK over Forge Remote

- **WHEN** the app receives the `preUninstall` invocation
- **THEN** the function issues the request through the SDK `uninstall()`
  operation, whose transport is Forge Remote to the `meet` backend, and treats a
  successful `200` result as the tenant being marked uninstalled

#### Scenario: Forge attaches the FIT and the function asserts no identity or context

- **WHEN** the function sends the uninstall request
- **THEN** the request carries only the Forge-attached `Authorization: Bearer`
  FIT for identity, and the function sets neither `X-Tenant-ID`/`X-Account-Id`
  nor `X-Issue-Id`/`X-Project-Id`

#### Scenario: Unknown tenant is treated as success, not failure

- **WHEN** the SDK result for the uninstall request carries a `404` (no tenant
  row for the resolved cloudId)
- **THEN** the function treats the invocation as complete and does not throw,
  because no retry exists to benefit from a failure signal within the
  pre-uninstall budget

#### Scenario: Already-uninstalled tenant is treated as success

- **WHEN** the SDK result for the uninstall request carries a `200` for a tenant
  that was already `UNINSTALLED`
- **THEN** the function treats the invocation as complete and does not throw
