## MODIFIED Requirements

### Requirement: OpenAPI drift check

The test workflow SHALL, for each changed service in `openapi_services`,
regenerate the per-service OpenAPI spec, fail if the committed
`services/<name>/openapi.yaml` differs from the regenerated output, and lint the
specs with Redocly. When any service in `openapi_services` changed, the workflow
SHALL additionally regenerate the combined `services/openapi.yaml` by joining
the per-service specs, fail if the committed combined document differs from the
regenerated output, and lint the combined document with Redocly.

#### Scenario: Committed specs match generated output

- **WHEN** regenerating produces no change to the committed `openapi.yaml` and
  Redocly lint passes
- **THEN** the OpenAPI drift check passes

#### Scenario: Committed specs are stale

- **WHEN** regenerating produces a diff against the committed `openapi.yaml`
- **THEN** the check fails with a message instructing the author to run
  `pnpm run openapi` locally and commit the result

#### Scenario: Spec violates lint rules

- **WHEN** Redocly lint reports an error against a generated spec
- **THEN** the OpenAPI drift check fails

#### Scenario: Combined spec is stale

- **WHEN** regenerating the combined `services/openapi.yaml` produces a diff
  against the committed combined document
- **THEN** the check fails with a message instructing the author to run
  `pnpm run openapi` locally and commit the result

#### Scenario: Combined spec violates lint rules

- **WHEN** Redocly lint reports an error against the generated combined
  `services/openapi.yaml`
- **THEN** the OpenAPI drift check fails
