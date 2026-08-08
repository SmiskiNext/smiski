## ADDED Requirements

### Requirement: Test stack composes the development stack

The repository SHALL provide a Docker Compose definition at
`services/test/compose.yaml` that composes `services/docker/compose.yaml` by
reference rather than duplicating it, declares a distinct project name, and
overrides only the services and configuration entries that differ. The
definition SHALL NOT require any modification to `services/docker/`.

#### Scenario: Test stack inherits an unmodified development stack

- **WHEN** an operator validates the test stack definition
- **THEN** every service declared by the development stack is present in the
  resolved configuration, and no file under `services/docker/` has been changed

#### Scenario: Test stack runs alongside the development stack

- **WHEN** both stacks are started on the same host
- **THEN** each owns a separately named set of volumes and networks, and neither
  reads nor writes the other's data

#### Scenario: An override that no longer resolves fails validation

- **WHEN** the development stack renames a service or configuration key that the
  overlay overrides, and the operator validates the test stack definition
- **THEN** validation exits non-zero and names the unresolved override rather
  than silently starting a partially patched stack

### Requirement: Scripted callers authenticate through the gateway

The test stack SHALL accept Forge Invocation Tokens signed by a locally
generated key, by sourcing the gateway's JSON Web Key Set from a local file
instead of a remote provider. Every other element of the authentication chain —
signature verification, claim extraction, and the external authorization call —
SHALL remain in the measured request path.

#### Scenario: A locally signed token is accepted

- **WHEN** a scripted caller sends a request carrying a token signed by the
  generated key and bearing the claim set the gateway requires
- **THEN** the request reaches the backend service and receives a successful
  response

#### Scenario: An unsigned or badly signed token is rejected

- **WHEN** a caller sends a request with no token, or with a token whose
  signature does not verify against the local key set
- **THEN** the gateway rejects the request with an authentication failure and
  the backend service is never reached

#### Scenario: Client-supplied identity headers cannot be spoofed

- **WHEN** a caller sends a validly signed token together with its own values
  for the tenant and account identity headers
- **THEN** the values forwarded to the backend are derived from the verified
  token claims, and the client-supplied values are discarded

#### Scenario: A token whose claim set is incomplete is rejected

- **WHEN** a caller sends a validly signed token that omits a claim the gateway
  parser requires to derive tenant, account, application, or environment
  identity
- **THEN** authorization fails and the failure names the missing claim

### Requirement: External project-management calls are mocked

The test stack SHALL satisfy the gateway's project-permission lookups from a
mock service configured through the existing base-URL setting, so that no
measurement depends on an external network call.

#### Scenario: Permission lookup resolves without external network access

- **WHEN** the gateway performs a permission lookup with an empty cache while
  the host has no route to the real project-management API
- **THEN** the lookup succeeds from the mock and the request proceeds

#### Scenario: Both cache states are measurable

- **WHEN** a measurement run is executed once against an empty cache and once
  against a populated cache
- **THEN** both runs complete and their response-time distributions are recorded
  separately

### Requirement: Media relay through a TURN server

The test stack SHALL provide a TURN server as an independently observable
container, and the media server SHALL advertise it to clients over a transport
that remains reachable when UDP is blocked.

#### Scenario: Media connects when all UDP is blocked

- **WHEN** a client joins a meeting from an environment where every UDP port is
  blocked, with the client configured to use relay candidates only
- **THEN** the media session establishes, and the selected candidate pair
  reports a relayed candidate type

#### Scenario: Call setup completes within the stated budget

- **WHEN** a client joins under the blocked-UDP condition
- **THEN** the elapsed time from connection start to media flowing is under 3
  seconds, and the measured value is recorded

#### Scenario: Relay usage is proven rather than assumed

- **WHEN** a relay-only run reports a successful connection
- **THEN** the evidence records the negotiated candidate type, so a connection
  that succeeded over a direct path cannot be recorded as a relayed one

#### Scenario: Relay failure is distinguishable from harness failure

- **WHEN** the TURN server is unreachable or misconfigured during a relay-only
  run
- **THEN** the run reports a connection failure attributable to relay
  unavailability, rather than reporting a passing result

### Requirement: Reproducible network conditions

The harness SHALL apply and remove named network-impairment profiles covering an
unimpaired baseline, a mobile-network condition with added latency, jitter and
loss, and a condition blocking UDP. Applying a profile SHALL be reversible
without restarting the stack.

#### Scenario: A profile is applied and observed

- **WHEN** an operator applies the mobile-network profile
- **THEN** subsequent traffic exhibits the configured latency, jitter and loss

#### Scenario: A profile is removed

- **WHEN** an operator removes an applied profile
- **THEN** traffic returns to baseline behavior and no impairment remains
  configured

#### Scenario: Quality thresholds are asserted only under impairment

- **WHEN** a media-quality measurement is recorded
- **THEN** the evidence names the profile in force, and results gathered on the
  unimpaired baseline are not presented as evidence of behavior under a degraded
  network

### Requirement: Meeting fixtures are seeded before measurement

The harness SHALL create the tenant and meeting records that measurement
requires, supporting both an admission policy that admits joiners immediately
and one that holds them for approval, and SHALL report the identifiers it
created.

#### Scenario: Fixtures are created and reported

- **WHEN** an operator seeds fixtures for a measurement run
- **THEN** the tenant and the requested meetings exist, and their identifiers
  are printed for use by subsequent commands

#### Scenario: Token identity matches the seeded tenant

- **WHEN** a request carries a token whose tenant claim does not match the
  tenant that owns the target meeting
- **THEN** the meeting is reported as not found, and the harness surfaces the
  mismatch rather than recording a measurement failure

#### Scenario: Seeding is repeatable

- **WHEN** seeding is run more than once
- **THEN** it either reuses or replaces prior fixtures without leaving the
  database in a state that fails subsequent runs

### Requirement: Token issuance load measurement

The harness SHALL measure concurrent access-token requests through the gateway,
reporting success rate and response-time distribution. Because a meeting row is
locked for the duration of each join, the measurement SHALL be executed both
against a single meeting and against multiple meetings, so contention is visible
rather than conflated with throughput.

#### Scenario: Concurrent load is generated and summarised

- **WHEN** the operator runs the token load measurement
- **THEN** the configured number of requests is issued within the configured
  window, and success rate together with median and tail response times is
  reported

#### Scenario: Lock contention is separated from throughput

- **WHEN** the single-meeting and multiple-meeting variants have both completed
- **THEN** their results are reported separately, and the evidence states which
  variant each figure came from

#### Scenario: The stated response-time threshold is asserted against steady state

- **WHEN** results are evaluated against the response-time threshold
- **THEN** the threshold is applied to the populated-cache, multiple-meeting
  variant, and the remaining variants are recorded as supporting context

#### Scenario: Token issuance and cache-state criteria are measured separately

- **WHEN** the admission policy admits joiners immediately, so responses carry a
  token but no approval state is cached
- **THEN** token success rate is measured in that run, and correctness of cached
  approval state is measured in a separate run using the approval-based policy

#### Scenario: Partial failures are reported, not averaged away

- **WHEN** any request in a run fails or is rejected
- **THEN** the failure count and the reason distribution are reported alongside
  the timing figures

### Requirement: Concurrent room capacity measurement

The harness SHALL drive a single room to the participant and publisher counts
the test plan requires, and SHALL report stream distribution, connection
failures and room stability. Where simulated publishing cannot produce a
screen-share track, the harness SHALL support an operator performing that step
and SHALL record when it occurred.

#### Scenario: A populated room is driven and observed

- **WHEN** the operator runs the room capacity measurement
- **THEN** the configured participants join, the configured publishers publish
  video at the configured resolution, and the room remains available throughout

#### Scenario: Connection failures are counted

- **WHEN** a room capacity run completes
- **THEN** the number of failed connections and dropped participants is
  reported, so a zero-failure result is an observation rather than an assumption

#### Scenario: A manual screen share is correlated with server metrics

- **WHEN** an operator starts and stops a screen share during a run
- **THEN** the start and stop times are recorded, so the interval can be aligned
  with server-side measurements

### Requirement: Client-side media quality measurement

The harness SHALL provide a browser client, independent of the production
application, that joins a meeting with a supplied token, can be constrained to
relay-only connectivity, can publish a screen share, and samples transport
statistics at a fixed interval into an exportable record.

#### Scenario: Transport statistics are sampled and exported

- **WHEN** an operator joins with the harness client and later exports the
  results
- **THEN** the export contains periodic samples of round-trip time, jitter,
  packet loss, dropped frames and bitrate in both directions

#### Scenario: Relay-only connectivity is selectable

- **WHEN** the operator enables the relay-only option before joining
- **THEN** the client attempts only relayed candidates, and the resulting
  candidate type is visible in the exported record

#### Scenario: A sustained session is measurable

- **WHEN** a two-party call runs for the duration the test plan requires
- **THEN** sampling continues for the whole session and the export covers the
  entire interval

#### Scenario: The production application is unmodified

- **WHEN** the harness client is added
- **THEN** no source file of the production application is changed

### Requirement: Infrastructure resource measurement

The test stack SHALL expose per-container processor, memory, network and disk
metrics for the media server, the TURN server, the backend services, both cache
instances and the databases. The two cache instances SHALL be reported
separately.

#### Scenario: Per-container resource series are available

- **WHEN** the observability components of the test stack are running
- **THEN** processor, memory, network and disk series are queryable per
  container and attributed to a named container

#### Scenario: The two cache instances are distinguishable

- **WHEN** cache memory usage is reported
- **THEN** the application cache and the media server's internal cache appear as
  separate series, so a threshold can be applied to the intended one

#### Scenario: Media server metrics are exposed

- **WHEN** the test stack is running
- **THEN** the media server publishes participant, track, packet-loss, jitter
  and round-trip-time metrics, and they are collected

#### Scenario: Load-generator overhead is separable

- **WHEN** resource usage is measured during a load run
- **THEN** the load generator's own consumption is attributable to its container
  and can be excluded from the figures reported for the system under test

### Requirement: Evidence collection

The harness SHALL export the collected measurements as machine-readable files,
one per test case, and each export SHALL record the conditions under which it
was produced.

#### Scenario: Results are exported per test case

- **WHEN** an operator collects results after a run
- **THEN** one file per test case is written, containing the measured series for
  that case

#### Scenario: Run conditions accompany the numbers

- **WHEN** a result file is produced
- **THEN** it records the network profile in force, the cache state, the
  admission policy and the variant, so a figure cannot be read without its
  context

#### Scenario: Substituted metrics are labelled

- **WHEN** a metric named by the test plan is unavailable and a substitute is
  measured in its place
- **THEN** the export names the substitution, so the report does not present a
  substituted figure as the original one

### Requirement: Documented operator procedure

The repository SHALL document how to start the test stack, the preconditions
each test case requires, which steps are performed by an operator rather than
scripted, and how each divergence between the test plan and the system's actual
behavior was resolved.

#### Scenario: An operator can run a case from the documentation alone

- **WHEN** an operator follows the documentation for a test case
- **THEN** the required preconditions, commands and manual steps are stated in
  order, and the expected evidence is named

#### Scenario: Divergences from the test plan are recorded

- **WHEN** a reader consults the documentation about a test-plan element that
  does not match the system's behavior
- **THEN** the mismatch and the chosen resolution are stated explicitly

#### Scenario: Preconditions that silently degrade results are called out

- **WHEN** a precondition exists whose omission yields missing data rather than
  an error
- **THEN** the documentation states the precondition and the symptom of skipping
  it

### Requirement: Test tooling is isolated from the application

The harness SHALL run its tooling from pinned container images rather than
requiring host installation, and SHALL NOT modify application source, the
development stack, or production configuration.

#### Scenario: Measurement runs without host tool installation

- **WHEN** an operator runs the harness on a host with only a container runtime
  available
- **THEN** the load generator, media client simulator and supporting services
  run from pinned images

#### Scenario: Application sources remain untouched

- **WHEN** the change is complete
- **THEN** no backend service source, no gateway source, no production
  application source and no file under the development stack directory has been
  modified

#### Scenario: The declared command entry point resolves

- **WHEN** the harness command is invoked through the entry point the package
  manifest declares
- **THEN** it executes, rather than failing because the declared path does not
  exist
