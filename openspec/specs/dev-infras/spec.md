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

Every service in the stack SHALL start successfully under its default
configuration profile, with every configuration property its component graph
requires resolvable.

#### Scenario: Service with no unresolvable property starts

- **WHEN** a service starts under the default profile with the stack's supplied
  environment
- **THEN** it initialises its component graph and becomes healthy

#### Scenario: Property required by a shared component is supplied

- **WHEN** a service includes a shared component that reads a configuration
  property having no default
- **THEN** that service's configuration supplies the property, so the service
  does not fail during initialisation

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
