# dev-infras Specification

## Purpose

Defines the Docker Compose stack under `services/docker/` that runs the complete
backend system locally — the `tenant`, `meet`, and `notification` services, the
`gateway` authorization service, the `envoy` API gateway, and every datastore
they depend on. Covers image provenance, startup ordering, per-service database
isolation, the environment variable contract, operator documentation, gateway
route coverage, and the local-development-only scope of the stack.

## Requirements

### Requirement: Single-command local stack startup

The repository SHALL provide a Docker Compose definition under
`services/docker/` that starts the complete backend system — the `tenant`,
`meet`, and `notification` services, the `gateway` authorization service, the
`envoy` API gateway, and every backing datastore they require — with a single
command, after the Java service images have been built.

#### Scenario: Stack starts and reaches a serving state

- **WHEN** an operator has built the Java service images and runs
  `docker compose up -d` from `services/docker/`
- **THEN** every container that declares a health probe reports healthy, every
  remaining container is running, and the gateway answers requests on the
  published host port

#### Scenario: Compose definition is syntactically valid

- **WHEN** `docker compose config` is run against the definition
- **THEN** the command succeeds and reports no undefined variable or schema
  errors

#### Scenario: Missing service image fails fast

- **WHEN** the stack is started before a Java service image has been built
- **THEN** startup fails immediately with an image resolution error naming the
  missing image, rather than starting partially and failing at request time

### Requirement: Record service excluded from the local stack

The local stack SHALL NOT include the `record` service or any infrastructure
that exists solely to support it, including S3-compatible object storage and
media-egress components.

#### Scenario: Record service absent

- **WHEN** the running stack is inspected
- **THEN** no container corresponds to the `record` service, and no
  object-storage or egress container is present

#### Scenario: No service depends on record topics

- **WHEN** the messaging topics consumed across the stack are inspected
- **THEN** no service subscribes to a `record.*` topic, so the stack functions
  with `record` absent

### Requirement: Per-service database isolation

Each service that owns persistent state SHALL be provisioned with its own
dedicated database instance, reachable from the host on a distinct port, so no
service can read or write another service's tables.

#### Scenario: Each service has a dedicated instance

- **WHEN** the stack is running
- **THEN** `tenant`, `meet`, and `notification` each connect to a separate
  database instance published on its own host port

#### Scenario: Schema is migrated before the service accepts traffic

- **WHEN** a service starts and its schema validation runs against a freshly
  created database
- **THEN** migrations have already been applied, so schema validation succeeds
  and the service becomes healthy

#### Scenario: Host port allocation does not collide

- **WHEN** all database instances publish host ports simultaneously
- **THEN** every published port is distinct and no container fails to bind

### Requirement: Service images match the released artifact

Java service images used by the local stack SHALL be the same artifacts the
release pipeline publishes, identified by the same image coordinates, so an
image verified locally is the image that ships.

#### Scenario: Local and released coordinates agree

- **WHEN** the compose definition's image reference for a Java service is
  compared with the coordinates the release pipeline publishes
- **THEN** the repository, name, and tagging scheme are identical

#### Scenario: Image version is overridable

- **WHEN** an operator supplies an explicit version through the environment
- **THEN** the stack resolves service images at that version instead of the
  default development version

### Requirement: Startup ordering enforced by dependency health

A service SHALL NOT start until every datastore it depends on reports healthy.
Where a container image cannot express a health probe, the stack SHALL tolerate
the resulting startup window rather than declare a probe that cannot run, and
the gateway SHALL absorb connection-level failures during that window by
retrying.

#### Scenario: Service waits for its datastores

- **WHEN** the stack starts from a cold state
- **THEN** each service starts only after its database, message broker, and
  cache dependencies report healthy

#### Scenario: Datastore probes use a binary present in the image

- **WHEN** a datastore container declares a health probe
- **THEN** the probe invokes a command that exists inside that image, so the
  probe reflects real readiness rather than passing or failing spuriously

#### Scenario: Image without a shell declares no health probe

- **WHEN** a container image provides no shell and no HTTP probe binary, as with
  a scratch-based or minimal run image
- **THEN** no health probe is declared for that container, and the omission is
  documented together with the mechanism that compensates for it

#### Scenario: Gateway retries a not-yet-ready upstream

- **WHEN** the gateway forwards a request to a service that has started but has
  not finished initialising, and the connection is refused
- **THEN** the gateway retries the request, and it retries only failures where
  no response has begun, so a stream is never replayed

### Requirement: Message broker reachable from containers and from the host

The message broker SHALL expose one listener addressable by containers on the
stack network and a second listener addressable from the host, so services can
be run either inside the stack or natively against the same broker.

#### Scenario: Containerised service connects over the internal listener

- **WHEN** a service running inside the stack connects to the broker
- **THEN** it connects over the internal listener using the broker's stack
  hostname

#### Scenario: Natively run service connects over the host listener

- **WHEN** a service is run natively on the host under the development profile
- **THEN** it connects over the host listener without any change to the broker
  configuration

### Requirement: Real-time media server ports published directly

The real-time media server's signalling and media ports SHALL be published
directly to the host, and media traffic SHALL NOT be routed through the API
gateway.

#### Scenario: Signalling and media reachable from a browser

- **WHEN** a browser establishes a real-time session against the local stack
- **THEN** it reaches signalling and media on the media server's published host
  ports

#### Scenario: Advertised address is explicitly configured

- **WHEN** the media server starts without an explicitly configured host address
  for advertising connection candidates
- **THEN** startup fails with an error naming the missing configuration, rather
  than starting and producing sessions whose media never connects

### Requirement: Environment variable contract is documented and complete

`services/docker/` SHALL include an environment template enumerating every
variable the stack consumes, and SHALL identify those that have no default and
therefore block startup when unset.

#### Scenario: Template covers every consumed variable

- **WHEN** the template is compared against the variables the stack's services
  read
- **THEN** every consumed variable appears in the template

#### Scenario: Startup-blocking variables are marked

- **WHEN** a variable has no default value in the consuming service
- **THEN** the template marks it as required

#### Scenario: Unset required variable fails at startup

- **WHEN** the stack is started with a required variable unset
- **THEN** the affected service fails at startup with an error identifying the
  unresolved configuration property, rather than failing later during request
  handling

### Requirement: Operator documentation covers ports, routing, and known gaps

`services/docker/` SHALL include documentation stating the host port allocation,
the gateway route table mapping each backend path to its owning service and
authentication level, the image build prerequisite, and every known security
limitation of the stack.

#### Scenario: Port map is documented

- **WHEN** an operator needs to reach a component directly
- **THEN** the documentation states which host port serves it

#### Scenario: Route table is documented

- **WHEN** a developer adds a backend endpoint
- **THEN** the documentation provides the route table to check whether a
  matching gateway route already exists

#### Scenario: Security limitation is stated prominently

- **WHEN** an operator reads the documentation
- **THEN** it states that the event-stream routes are served without
  authentication and that the stack is restricted to local development

### Requirement: Stack scope restricted to local development

The stack SHALL be documented as suitable only for local development, and SHALL
NOT be presented as a baseline for any network-reachable environment while any
route is served without authentication.

#### Scenario: Scope restriction is explicit

- **WHEN** the stack's documentation and specification are read
- **THEN** both state the local-development-only restriction and the reason for
  it

#### Scenario: Unauthenticated route is recorded as accepted debt

- **WHEN** a reviewer encounters an unauthenticated gateway route
- **THEN** the specification records it as a deliberate, bounded decision with
  the closure options identified, rather than leaving it to be mistaken for an
  oversight

### Requirement: Every backend endpoint is reachable through the gateway

For every HTTP endpoint a backend service exposes, the gateway SHALL provide a
route that reaches that endpoint's owning service.

#### Scenario: Issue-scoped meeting listing is reachable

- **WHEN** a client requests the issue-scoped meeting collection through the
  gateway
- **THEN** the request reaches the `meet` service and is not answered with 404

#### Scenario: Event-stream endpoints reach their owning service

- **WHEN** a client opens either meeting event-stream endpoint through the
  gateway
- **THEN** the request reaches the `notification` service, not the `meet`
  service, even though the paths begin with the meeting path space

#### Scenario: Inbound webhook endpoints are reachable

- **WHEN** an external system posts to the media-server webhook or the inbound
  email webhook through the gateway
- **THEN** the request reaches its owning service

#### Scenario: No route targets a non-existent endpoint

- **WHEN** the gateway's route table is compared against the backend endpoint
  inventory
- **THEN** every route resolves to at least one endpoint that exists in code

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

### Requirement: Native development profile agrees with the stack port map

The development configuration profile used for natively run services SHALL
address each datastore on the same host port the stack publishes for it.

#### Scenario: Native run reaches the stack's datastores

- **WHEN** a service is run natively under the development profile against the
  running stack
- **THEN** it connects to its own database, broker, and cache without any
  configuration override

#### Scenario: No development port targets a decommissioned component

- **WHEN** the development profile's datastore ports are reviewed
- **THEN** none addresses a port belonging to a component removed from the
  system

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
