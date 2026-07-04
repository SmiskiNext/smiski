## ADDED Requirements

### Requirement: Docker Compose starts the full development stack with a single command

The system SHALL provide a `services/docker/compose.yaml` file that starts all
application services, databases, message broker, cache, object storage, and
LiveKit infrastructure using `docker compose up`.

#### Scenario: All services start successfully

- **WHEN** `docker compose up` is run from `services/docker/`
- **THEN** all containers (caddy, user-management, meeting-management,
  chat-management, notification, user-postgres, meeting-postgres, chat-mongo,
  kafka, valkey, rustfs, livekit-server, livekit-redis, livekit-egress) SHALL
  reach healthy state

#### Scenario: Services start in correct dependency order

- **WHEN** `docker compose up` is run
- **THEN** infrastructure services (databases, kafka, valkey) SHALL be healthy
  before application services start

### Requirement: Caddy gateway validates JWT and injects user identity headers

The system SHALL deploy a custom Caddy image (built with caddy-jwt module) that
validates HS256 JWT tokens on protected routes and injects `X-User-ID` (from
`sub` claim) and `X-User-Email` (from `email` claim) headers before forwarding
to upstream services.

#### Scenario: Valid JWT on protected route

- **WHEN** a request with a valid `Authorization: Bearer <token>` header is sent
  to `/api/v1/users/me`
- **THEN** Caddy SHALL validate the HS256 signature, extract `sub` and `email`
  claims, set `X-User-ID` and `X-User-Email` headers, and forward to
  user-management:8080

#### Scenario: Missing or invalid JWT on protected route

- **WHEN** a request without a valid JWT is sent to a protected route
  (`/api/v1/users/*`, `/api/v1/me/*`, `/api/v1/meetings/*`, `/api/v1/chat/*`)
- **THEN** Caddy SHALL respond with HTTP 401 Unauthorized without forwarding to
  upstream

#### Scenario: Public routes bypass JWT validation

- **WHEN** a request is sent to a public route (`/api/v1/auth/register`,
  `/api/v1/auth/login`, `/api/v1/auth/google-login`, `/api/v1/auth/refresh`,
  `/api/v1/auth/logout`, `/api/v1/auth/forgot-password`,
  `/api/v1/auth/reset-password`, `/api/v1/webhook/livekit`)
- **THEN** Caddy SHALL forward the request to the upstream service without JWT
  validation

### Requirement: Caddy proxies LiveKit WebSocket connections at /livekit path

The system SHALL configure Caddy to reverse-proxy WebSocket connections from
`/livekit` to the LiveKit server container on port 7880.

#### Scenario: WebSocket upgrade for LiveKit signaling

- **WHEN** a client sends a WebSocket upgrade request to
  `ws://localhost:30000/livekit`
- **THEN** Caddy SHALL proxy the connection to `livekit-server:7880` with
  WebSocket upgrade headers preserved

#### Scenario: LiveKit client SDK connects through Caddy

- **WHEN** the web frontend connects using LiveKit SDK with URL
  `ws://localhost:30000/livekit`
- **THEN** the LiveKit signaling connection SHALL be established successfully
  through Caddy

### Requirement: Caddy handles CORS for local development

The system SHALL configure Caddy to add CORS headers allowing requests from
`http://localhost:3000` (Next.js dev server) and `http://localhost:5173` (Vite
dev server).

#### Scenario: Preflight OPTIONS request

- **WHEN** a browser sends an OPTIONS preflight request with
  `Origin: http://localhost:3000`
- **THEN** Caddy SHALL respond with appropriate `Access-Control-Allow-Origin`,
  `Access-Control-Allow-Methods`, `Access-Control-Allow-Headers`, and
  `Access-Control-Allow-Credentials` headers

### Requirement: Gateway is accessible on host port 30000

The system SHALL expose the Caddy gateway on host port 30000, providing a single
entry point for all API and LiveKit WebSocket traffic.

#### Scenario: API request via host port

- **WHEN** a request is sent to `http://localhost:30000/api/v1/auth/login`
- **THEN** the request SHALL be routed through Caddy to user-management service

### Requirement: LiveKit server runs with dedicated Redis and Egress

The system SHALL deploy LiveKit server, a dedicated Redis instance for LiveKit,
and LiveKit Egress for recording support, all within the Docker Compose stack.

#### Scenario: LiveKit server starts with Redis backend

- **WHEN** Docker Compose starts
- **THEN** livekit-server SHALL connect to livekit-redis for room state and
  signaling

#### Scenario: LiveKit Egress connects to LiveKit server

- **WHEN** Docker Compose starts
- **THEN** livekit-egress SHALL connect to livekit-server via livekit-redis job
  queue and be ready to process recording requests

#### Scenario: LiveKit Egress uploads recordings to RustFS

- **WHEN** a recording is completed by livekit-egress
- **THEN** the recording file SHALL be uploaded to the `recordings` bucket in
  RustFS

### Requirement: Kafka runs in KRaft mode without Zookeeper

The system SHALL deploy Apache Kafka in KRaft mode (single node, combined
controller+broker) without requiring a separate Zookeeper instance.

#### Scenario: Kafka is ready for producers and consumers

- **WHEN** Docker Compose starts and Kafka container is healthy
- **THEN** application services SHALL be able to produce and consume messages on
  configured topics

### Requirement: MongoDB runs as a single-node replica set

The system SHALL deploy MongoDB 8.0 as a single-node replica set (`rs0`) to
support change streams required by chat-management.

#### Scenario: Replica set is initialized on first start

- **WHEN** the chat-mongo container starts for the first time
- **THEN** the replica set `rs0` SHALL be automatically initiated with the
  single member

#### Scenario: Change streams work for chat-management

- **WHEN** chat-management connects to MongoDB
- **THEN** MongoDB change streams SHALL function correctly for real-time message
  delivery

### Requirement: Application services are built via Spring Boot Buildpacks

The system SHALL use the existing `bootBuildImage` Gradle task to build Docker
images for all application services. The compose file SHALL reference these
local images.

#### Scenario: Images are built before compose up

- **WHEN** a developer runs the build command
  (`./services/gradlew bootBuildImage`)
- **THEN** Docker images for user-management, meeting-management,
  chat-management, and notification SHALL be created with the naming pattern
  `ghcr.io/phunguy65/zms/<service-name>:0.0.1-SNAPSHOT`

### Requirement: Environment configuration is documented via .env.example

The system SHALL provide a `services/docker/.env.example` file documenting all
required environment variables for the Docker Compose stack with sensible
development defaults.

#### Scenario: Developer copies .env.example to start

- **WHEN** a developer copies `services/docker/.env.example` to
  `services/docker/.env`
- **THEN** `docker compose up` SHALL work with the default values without
  additional configuration

### Requirement: RustFS console is accessible on host port 30001

The system SHALL expose the RustFS web console on host port 30001 for debugging
object storage during development.

#### Scenario: Developer accesses RustFS console

- **WHEN** a developer navigates to `http://localhost:30001`
- **THEN** the RustFS web console SHALL be accessible for bucket and object
  management
