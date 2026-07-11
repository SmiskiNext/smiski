## 1. Establish the api-convention spec

- [x] 1.1 Populate `openspec/specs/api-convention/spec.md` with the versioned
      URL scheme, RESTful resource/action naming, success-body, Problem Details
      error, and validation-failure requirements (via archive of this change).
- [x] 1.2 Confirm the spec matches the implemented behavior in `services/shared`
      (`ApiPathPrefixAutoConfiguration`, `ResultResponder`,
      `ProblemDetailMapper`, `GlobalExceptionHandler`) — no code changes
      expected.

## 2. Align documentation

- [x] 2.1 Correct the stale "JSend envelope" wording in the REST section of
      `services/AGENTS.md` to describe RFC 9457 (`application/problem+json`)
      errors and raw success bodies, and point readers to the api-convention
      spec.

## 3. Verify against existing behavior (test coverage)

- [x] 3.1 Verify "Version resolved from path segment" and "Controller declares
      only the resource path": confirm an existing controller test/OpenAPI
      output shows routes served under `/api/{version}/...`; reference the
      existing test instead of duplicating.
- [x] 3.2 Verify "Framework endpoints stay unprefixed": confirm Actuator /
      `/v3/api-docs` are reachable without the `/api/{version}` prefix.
- [x] 3.3 Verify "Creation returns Location header" and "No-body operation
      returns 204": confirm `ResultResponder.created`/`noContent` behavior via
      an existing controller test; add one only if no coverage exists.
- [x] 3.4 Verify "Domain error mapped to Problem Details" and "Type URI
      derivation": confirm a `ProblemDetailMapper`/error path test asserts
      `application/problem+json`, `code`, `traceId`, and `type` behavior.
- [x] 3.5 Verify "Localized message via Accept-Language": confirm a test asserts
      `vi` bundle resolution for `title`/`detail` with locale-independent
      `code`.
- [x] 3.6 Verify validation-failure scenarios (`VALIDATION_ERROR` with `errors`,
      `REQUIRED`, `INVALID_FORMAT`, `MALFORMED_REQUEST`) against existing
      `GlobalExceptionHandler` tests; add missing scenario coverage if absent.

## 4. Finalize

- [x] 4.1 Run `openspec validate document-api-convention` and resolve any
      issues.
