# openapi-merge Specification

## Purpose

TBD - created by archiving change add-openapi-merge. Update Purpose after
archive.

## Requirements

### Requirement: Combined OpenAPI document produced by join

The build tooling SHALL produce a single combined OpenAPI 3.1 document at
`services/openapi.yaml` by joining the per-service documents
`services/tenant/openapi.yaml`, `services/meet/openapi.yaml`, and
`services/record/openapi.yaml` using the Redocly `join` command. The combined
document SHALL be committed to the repository as a generated artifact.

#### Scenario: Combined document is generated from the three services

- **WHEN** the merge tooling runs against the three per-service specs
- **THEN** it writes `services/openapi.yaml` containing the paths, components,
  and tags of all three source documents

#### Scenario: Combined document is valid

- **WHEN** Redocly lint runs against the generated `services/openapi.yaml`
- **THEN** the combined document reports no lint errors

### Requirement: Component name conflict resolution

The join SHALL prefix components with the value of each source document's
`info.title`, so that identically named components across the per-service
documents (such as `ProblemDetail` and `Violation`) become distinct in the
combined document instead of causing a join conflict.

#### Scenario: Duplicated schema names are prefixed

- **WHEN** the combined document is generated and a schema name exists in more
  than one source document
- **THEN** each occurrence appears prefixed by its source `info.title` (for
  example `Tenant_ProblemDetail`, `Meet_ProblemDetail`, `Record_ProblemDetail`)
  and no join conflict aborts the operation

#### Scenario: References target the prefixed components

- **WHEN** an operation in the combined document references a prefixed component
- **THEN** its `$ref` points to the prefixed component name and resolves within
  the combined document

### Requirement: Fixed combined info block

The combined document SHALL carry a fixed `info` block identifying the unified
gateway surface, rather than inheriting the `info.title` and `info.version` of
the first joined input file. The fixed `info` SHALL define a `title`, a
`version`, and a `description`.

#### Scenario: Combined document uses its own info

- **WHEN** `services/openapi.yaml` is generated
- **THEN** its `info.title`, `info.version`, and `info.description` are the
  fixed combined values and not the `Tenant` service's `info` values

### Requirement: Merge script in the openapi flow

The repository SHALL expose an npm script that regenerates
`services/openapi.yaml`, and the existing `pnpm run openapi` flow SHALL produce
and lint the combined document in addition to the per-service documents.

#### Scenario: Dedicated merge script produces the combined document

- **WHEN** the merge npm script is run
- **THEN** `services/openapi.yaml` is regenerated from the current per-service
  specs

#### Scenario: Aggregate openapi flow covers the combined document

- **WHEN** `pnpm run openapi` runs
- **THEN** the per-service specs are regenerated, the combined document is
  produced, and Redocly lint runs against both the per-service specs and
  `services/openapi.yaml`
