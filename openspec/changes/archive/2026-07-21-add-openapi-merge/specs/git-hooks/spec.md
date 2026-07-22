## ADDED Requirements

### Requirement: Combined OpenAPI drift check at pre-push

The pre-push hook SHALL, when a push includes changes under any service that
emits an OpenAPI spec (`tenant`, `meet`, `record`), regenerate the combined
`services/openapi.yaml` by joining the per-service specs and block the push if
the committed combined document differs from the regenerated output. The hook
SHALL restore the working tree to the committed state after checking, so the
drift check does not leave uncommitted changes.

#### Scenario: Combined spec is up to date

- **WHEN** a push includes changes under `services/{tenant,meet,record}/` and
  the regenerated combined document matches the committed
  `services/openapi.yaml`
- **THEN** the pre-push hook passes the combined drift check

#### Scenario: Combined spec drift blocks the push

- **WHEN** a push includes changes under `services/{tenant,meet,record}/` and
  the regenerated combined document differs from the committed
  `services/openapi.yaml`
- **THEN** the pre-push hook fails with a message instructing the author to run
  `pnpm run openapi` and commit the regenerated combined document

#### Scenario: No relevant service changes skip the check

- **WHEN** a push includes no changes under `services/{tenant,meet,record}/`
- **THEN** the combined drift check is skipped and does not block the push
