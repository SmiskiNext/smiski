## ADDED Requirements

### Requirement: Observability stack is opt-in and leaves default startup unchanged

The stack SHALL provide log aggregation, metrics collection, and a visualisation
interface as an opt-in addition that an operator enables with a single command
and no file edits. Enabling it SHALL NOT be required to run the system, and the
default startup command SHALL start exactly the containers it started before
observability existed.

#### Scenario: Default startup is unaffected

- **WHEN** an operator runs the stack's default startup command without
  requesting observability
- **THEN** the set of running containers is identical to the set that ran before
  observability was introduced, and no observability component is started

#### Scenario: Observability is enabled by one command

- **WHEN** an operator starts the stack requesting the observability profile
- **THEN** the log store, log collector, metrics store, visualisation interface,
  and every infrastructure exporter start alongside the application containers,
  with no edit to any configuration file

#### Scenario: Observability is disabled again without file changes

- **WHEN** an operator restarts the stack without requesting the observability
  profile
- **THEN** the observability components do not start, and the application
  containers run unaffected

#### Scenario: Observability failure does not block the application

- **WHEN** an observability component is unavailable or fails to start while the
  profile is enabled
- **THEN** the application containers continue to start and serve requests, and
  no application service reports unhealthy as a result

### Requirement: Java services expose a metrics scrape endpoint

Each of the `tenant`, `meet`, and `notification` services SHALL expose runtime
metrics on an endpoint suitable for scraping by a pull-based metrics collector,
served on the service's existing application port at its conventional unprefixed
framework path.

#### Scenario: Metrics endpoint serves scrapeable output

- **WHEN** the metrics endpoint of a running service is requested
- **THEN** it responds successfully with metric families in the collector's
  exposition format, including JVM memory, garbage collection, and HTTP request
  metrics

#### Scenario: Endpoint availability survives build-time optimisation

- **WHEN** a service image is built with ahead-of-time optimisation enabled and
  run with that optimisation active
- **THEN** the metrics endpoint is available at runtime, because its exposure is
  declared in configuration compiled into the image rather than supplied only as
  a runtime environment variable

#### Scenario: Only health and metrics endpoints are exposed

- **WHEN** the set of reachable management endpoints on a running service is
  enumerated
- **THEN** only the health and metrics endpoints respond, and endpoints that
  would disclose configuration or environment values — including datastore
  credentials — are not reachable

#### Scenario: Stale image reports a down target rather than failing

- **WHEN** the observability profile is started against service images built
  before the metrics endpoint was added
- **THEN** the metrics collector records those targets as down and the rest of
  the stack continues to operate, rather than any container failing to start

### Requirement: Metrics coverage spans services and infrastructure

The metrics collector SHALL gather metrics from the three Java services, the API
gateway proxy, every application database, the application cache, and each
running container's resource consumption.

#### Scenario: Every declared target is scraped

- **WHEN** the metrics collector's target list is inspected after the
  observability profile has started
- **THEN** every declared target reports as up, covering the three Java
  services, the proxy, the three databases, the cache, and the log collector

#### Scenario: Per-container resource metrics are available

- **WHEN** CPU and memory metrics are queried for a named application container
- **THEN** values are returned for that container without a dedicated
  per-container metrics agent being deployed

#### Scenario: A component without a metrics endpoint is excluded deliberately

- **WHEN** a stack component exposes no metrics endpoint in the image the stack
  runs
- **THEN** it is absent from the target list and its exclusion is recorded in
  the change's documentation, rather than being declared as a target that
  permanently reports down

### Requirement: Java services emit structured logs under the stack profile

When run by the stack, the `tenant`, `meet`, and `notification` services SHALL
emit each log record as a single-line structured document carrying at minimum
the severity, the logger name, the message, the timestamp, and the emitting
service's name, so that a collector can index records without parsing free text.

#### Scenario: Log records are machine-parseable

- **WHEN** a log line produced by a service running under the stack is read
- **THEN** it parses as a structured record exposing severity, logger name,
  message, timestamp, and service name as discrete fields

#### Scenario: Correlation identifier is preserved

- **WHEN** a request carrying a correlation identifier is handled and the
  resulting log records are read
- **THEN** the identifier appears as a field on those records, so records from
  one request can be gathered across services

#### Scenario: Other profiles are unaffected

- **WHEN** a service is run under the human-readable local profile or the
  cluster profile
- **THEN** its log output format is unchanged from before this change

#### Scenario: Every profile the stack activates produces log output

- **WHEN** the profile the stack activates is applied to the logging
  configuration
- **THEN** a matching output configuration exists for it, so no profile can
  result in a service producing no log output at all

### Requirement: Container logs are centrally collected and queryable

The stack SHALL collect the standard output of every running container and
forward it to a log store queryable through the visualisation interface, with
each record labelled by its originating container.

#### Scenario: Logs from every container reach the store

- **WHEN** the observability profile is running and containers are producing
  output
- **THEN** records from each container are retrievable from the log store,
  labelled with that container's name

#### Scenario: Logs are filterable by service and severity

- **WHEN** an operator queries the log store for one service at one severity
- **THEN** only records from that service at that severity are returned

#### Scenario: Collection requires no per-service configuration

- **WHEN** a new container is added to the stack
- **THEN** its output is collected without any logging configuration being added
  to that container's own definition

#### Scenario: Collector restart does not lose the log store's contents

- **WHEN** the log collector is restarted
- **THEN** records already written to the log store remain queryable, and
  collection resumes for running containers

### Requirement: Observability configuration is validatable offline

Every configuration file the observability components consume SHALL be
verifiable for syntactic and schema validity without starting the stack, and the
commands to do so SHALL be documented alongside the existing validation
commands.

#### Scenario: Each configuration format has a documented validation command

- **WHEN** an operator consults the stack documentation after editing an
  observability configuration file
- **THEN** it states a command that validates that file without starting the
  stack

#### Scenario: Compose definition is valid with and without the profile

- **WHEN** the compose definition is validated both with and without the
  observability profile requested
- **THEN** both succeed and report no undefined variable or schema errors

#### Scenario: Invalid configuration is rejected before startup

- **WHEN** a validation command is run against a configuration file containing a
  syntax or schema error
- **THEN** the command fails and identifies the error, rather than the fault
  surfacing only when the stack is started

### Requirement: Observability component versions are pinned

Every observability container image SHALL be pinned to an explicit version tag
that resolves in its registry, consistent with the rest of the stack, so that
the topology an operator starts does not change without a deliberate edit.

#### Scenario: No observability image floats on a mutable tag

- **WHEN** the observability service definitions are inspected
- **THEN** every image reference carries an explicit version tag and none relies
  on a mutable tag such as `latest`

#### Scenario: Every pinned tag resolves

- **WHEN** each pinned image reference is resolved against its registry
- **THEN** every reference resolves to a published image

## MODIFIED Requirements

### Requirement: Services start successfully under the default profile

Every service in the stack SHALL start successfully under the configuration
profile the stack activates for it, with every configuration property its
component graph requires resolvable. Where the stack activates a profile other
than the default, that profile SHALL differ from the default only in
configuration property values and SHALL NOT change which components the service
creates, so that a service image built with ahead-of-time optimisation remains
valid under it.

#### Scenario: Service with no unresolvable property starts

- **WHEN** a service starts under the profile the stack activates, with the
  stack's supplied environment
- **THEN** it initialises its component graph and becomes healthy

#### Scenario: Property required by a shared component is supplied

- **WHEN** a service includes a shared component that reads a configuration
  property having no default
- **THEN** that service's configuration supplies the property, so the service
  does not fail during initialisation

#### Scenario: Stack-activated profile does not alter the component graph

- **WHEN** a service runs under the stack-activated profile with ahead-of-time
  optimisation enabled
- **THEN** it creates the same components it creates under the default profile,
  so no component is missing at runtime as a result of the profile differing
  from the one present when the image was built
