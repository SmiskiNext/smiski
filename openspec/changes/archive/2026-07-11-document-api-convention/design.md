## Context

The backend is API-first: each Spring Boot service emits its own OpenAPI spec
from a `@SpringBootTest`, and specs are never merged. The cross-service HTTP
contract is already implemented in `services/shared`
(`ApiPathPrefixAutoConfiguration`, `ProblemDetailMapper`, `ResultResponder`,
`GlobalExceptionHandler`, `ErrorCode`/`ErrorCategory`, `Violation`) and consumed
by each service's `presentation/` controllers. However, the authoritative
description lives only as prose in `services/AGENTS.md`, and
`openspec/specs/api-convention/spec.md` is empty even though `config.yaml`
instructs authors to read it before making API decisions.

Notably, `services/AGENTS.md` still describes a "JSend envelope" for all
responses, but the implemented behavior is RFC 9457 (`application/problem+json`)
for errors and raw representations (no envelope) for success. This spec
documents the implemented behavior, which is the source of truth.

## Goals / Non-Goals

**Goals:**

- Capture the implemented HTTP contract as a testable behavioral spec: versioned
  URL scheme, RESTful resource/action naming, success-body shape, RFC 9457 error
  responses, and validation-failure semantics.
- Give design reviews and new services a single authoritative reference.
- Align the spec with what `services/shared` and existing controllers actually
  do.

**Non-Goals:**

- No code changes to `services/shared` or any service.
- No per-service resource modeling (each service owns its own resources/spec).
- No changes to transport concerns outside HTTP REST (Kafka/CloudEvents, gRPC).
- Not defining authentication/authorization scheme details (separate concern).

## Decisions

- **Document implemented behavior, not AGENTS.md prose.** Where AGENTS.md and
  the code disagree (JSend vs. Problem Details), the spec follows the code.
  Rationale: the code is the running contract clients depend on. Alternative
  (spec the JSend prose) was rejected because it contradicts shipped behavior.

- **Single new capability `api-convention` with ADDED requirements.** The
  existing spec file is empty, so all requirements are additive. Rationale:
  nothing pre-exists to MODIFY.

- **Requirements grouped by concern** (URL scheme, naming, success body, error
  responses, validation) rather than by class. Rationale: specs describe
  behavior, not implementation structure; this keeps each requirement
  independently testable.

- **Error category → HTTP status mapping treated as normative** but expressed at
  the category level (e.g. `NOT_FOUND` → 404). Rationale: the mapping is
  centralized in `ProblemDetailMapper#toStatus`; specs reference the semantic
  category, keeping them framework-agnostic.

## Risks / Trade-offs

- **Spec drifts from `services/AGENTS.md`** → Follow-up: update AGENTS.md's REST
  section to point at this spec and correct the stale JSend reference (tracked
  as a task, out of this change's spec scope).

- **New services could diverge from the contract** → The spec's scenarios are
  written as testable acceptance criteria; per-service OpenAPI generation and
  the shared `GlobalExceptionHandler`/`ResultResponder` enforce most of it at
  runtime.

- **Category-to-status mapping could change** → Kept at category granularity in
  the spec so status-code details remain owned by `ProblemDetailMapper`; a
  mapping change would be a deliberate spec update.

## Migration Plan

Not applicable — documentation-only artifact. No deploy or rollback. The spec
becomes effective on archive, replacing the empty
`openspec/specs/api-convention/spec.md`.

## Open Questions

- Should the stale JSend wording in `services/AGENTS.md` be corrected as part of
  this change or a separate docs change? (Proposed: a single doc-fix task here.)
